package e2e.support

import cli.Constants
import cli.createLoadoutCommand
import cli.di.ApplicationScope
import cli.di.withApplicationScope
import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import data.serialization.JsonSerializer
import domain.entity.Loadout
import domain.entity.LoadoutMetadata
import domain.entity.LocalLoadoutState
import domain.entity.RepositorySettings
import domain.entity.packaging.Result
import e2e.platform.EnvironmentOverlay
import e2e.platform.createTemporaryDirectory
import e2e.platform.currentWorkingDirectory
import e2e.platform.deleteRecursively
import e2e.platform.environmentOverlay
import e2e.platform.isExecutablePath
import e2e.platform.readEnvironmentVariable
import e2e.platform.runExternalProcess
import e2e.platform.setExecutable
import e2e.platform.withWorkingDirectoryAndEnvironment

typealias ScenarioSeed = E2eScenario.() -> Unit

infix fun ScenarioSeed.andThen(next: ScenarioSeed): ScenarioSeed =
    {
        this@andThen()
        next()
    }

data class CommandResult(
    val stdout: String,
    val stderr: String,
    val output: String,
    val exitCode: Int,
)

class E2eScenario private constructor(
    val workspaceRoot: String,
    val homeRoot: String,
    val xdgConfigRoot: String,
) : AutoCloseable {
    private val projectRoot = currentWorkingDirectory()
    private val serializer = JsonSerializer()
    private val gitCeilingDirectories = workspaceRoot.substringBeforeLast("/", missingDelimiterValue = workspaceRoot)

    fun runCommand(vararg args: String): CommandResult =
        withProcessContext {
            withApplicationScope {
                createLoadoutCommand(this).test(args.toList())
            }.toCommandResult()
        }

    /**
     * Runs an external command inside the harness sandbox.
     *
     * The harness-owned environment overlay is authoritative. Caller-provided entries are additive
     * only for keys the sandbox does not already manage.
     */
    fun runExternalCommand(
        vararg args: String,
        workingDirectory: String = workspaceRoot,
        environment: EnvironmentOverlay = environmentOverlay(),
    ): CommandResult =
        runExternalProcess(
            workingDirectory = workingDirectory,
            command = args.toList(),
            environment = environment + processEnvironment()
        ).toCommandResult()

    /**
     * Runs git inside the harness sandbox.
     *
     * The harness-owned git overlay is authoritative. Caller-provided entries are additive only for
     * keys the sandbox does not already manage.
     */
    fun runGit(
        vararg args: String,
        workingDirectory: String = workspaceRoot,
        environment: EnvironmentOverlay = environmentOverlay(),
    ): CommandResult =
        runExternalCommand(
            "git",
            *args,
            workingDirectory = workingDirectory,
            environment = environment + gitEnvironment()
        )

    fun initializeGitRepository(workingDirectory: String = workspaceRoot) {
        runGit("init", workingDirectory = workingDirectory).requireSuccess("git init")
        runGit("config", "user.name", "Loadout E2E", workingDirectory = workingDirectory)
            .requireSuccess("git config user.name")
        runGit("config", "user.email", "loadout@example.test", workingDirectory = workingDirectory)
            .requireSuccess("git config user.email")
    }

    fun commitAllFiles(message: String, workingDirectory: String = workspaceRoot) {
        runGit("add", "-A", workingDirectory = workingDirectory).requireSuccess("git add -A")
        runGit("commit", "-m", message, workingDirectory = workingDirectory).requireSuccess("git commit")
    }

    fun readGitLocalConfig(key: String, workingDirectory: String = workspaceRoot): String? {
        val result = runGit("config", "--local", "--get", key, workingDirectory = workingDirectory)
        return if (result.exitCode == 0) {
            result.stdout.trim().ifBlank { null }
        } else {
            null
        }
    }

    fun loadoutHelperExecutablePath(): String =
        readEnvironmentVariable(LOADOUT_E2E_HELPER_PATH_ENVIRONMENT_VARIABLE) ?: DEFAULT_LOADOUT_HELPER_PATH

    fun inspectExternalEnvironment(
        vararg keys: String,
        environment: EnvironmentOverlay = environmentOverlay(),
    ): Map<String, String> {
        val arguments = listOf(loadoutHelperExecutablePath(), HELPER_PRINT_ENVIRONMENT_COMMAND) + keys
        val result = runExternalCommand(*arguments.toTypedArray(), environment = environment)
        result.requireSuccess("inspect external environment")
        return result.stdout
            .lineSequence()
            .filter { it.isNotBlank() }
            .associate { line ->
                val delimiterIndex = line.indexOf('=')
                check(delimiterIndex >= 0) {
                    "Expected KEY=value output from helper, but received '$line'"
                }
                val key = line.substring(0, delimiterIndex)
                val value = line.substring(delimiterIndex + 1)
                key to value
            }
    }

    fun workspacePath(relativePath: String): String =
        if (relativePath.isBlank()) workspaceRoot else "$workspaceRoot/$relativePath"

    fun homePath(relativePath: String): String = if (relativePath.isBlank()) homeRoot else "$homeRoot/$relativePath"

    fun writeWorkspaceFile(relativePath: String, content: String) {
        writeFile(relativePath, content)
    }

    fun deleteWorkspaceFile(relativePath: String) {
        withScope {
            if (fileRepository.fileExists(relativePath)) {
                fileRepository.deleteFile(relativePath).unwrap("delete workspace file '$relativePath'")
            }
        }
    }

    fun workspaceFileIsExecutable(relativePath: String): Boolean = isExecutablePath(workspacePath(relativePath))

    fun setWorkspaceFileExecutable(relativePath: String) {
        setExecutable(workspacePath(relativePath))
    }

    fun readWorkspaceFile(relativePath: String): String? = readFile(relativePath)

    fun readLocalLoadoutState(): LocalLoadoutState? =
        readWorkspaceFile(Constants.LOCAL_LOADOUT_STATE_FILE)?.let {
            serializer.deserialize(it, LocalLoadoutState.serializer()).unwrap("deserialize local loadout state")
        }

    fun readLocalLoadoutStateFromDirectory(directory: String): LocalLoadoutState? =
        readFile("$directory/${Constants.LOCAL_LOADOUT_STATE_FILE}")?.let {
            serializer
                .deserialize(it, LocalLoadoutState.serializer())
                .unwrap("deserialize local loadout state in '$directory'")
        }

    fun readRepositorySettings(): RepositorySettings? =
        readWorkspaceFile(Constants.REPOSITORY_SETTINGS_FILE)?.let {
            serializer.deserialize(it, RepositorySettings.serializer()).unwrap("deserialize repository settings")
        }

    fun writeLocalLoadoutState(localLoadoutState: LocalLoadoutState) {
        writeWorkspaceFile(
            Constants.LOCAL_LOADOUT_STATE_FILE,
            serializer
                .serialize(localLoadoutState, LocalLoadoutState.serializer())
                .unwrap("serialize local loadout state")
        )
    }

    fun writeRepositorySettings(repositorySettings: RepositorySettings) {
        writeWorkspaceFile(
            Constants.REPOSITORY_SETTINGS_FILE,
            serializer
                .serialize(repositorySettings, RepositorySettings.serializer())
                .unwrap("serialize repository settings")
        )
    }

    fun readLoadout(name: String): Loadout? =
        readWorkspaceFile("${Constants.LOADOUTS_DIR}/$name.json")?.let {
            serializer.deserialize(it, Loadout.serializer()).unwrap("deserialize loadout '$name'")
        }

    fun listLoadoutNames(): List<String> =
        withScope {
            fileRepository
                .listFiles(Constants.LOADOUTS_DIR, extension = "json")
                .unwrap("list loadouts")
                .map { path -> path.substringAfterLast('/').removeSuffix(".json") }
                .sorted()
        }

    fun seedLoadout(
        name: String,
        description: String = "",
        fragments: List<String> = emptyList(),
        createdAt: Long = 1_000L,
        updatedAt: Long = createdAt,
    ): Loadout {
        val loadout =
            Loadout(
                name = name,
                description = description,
                fragments = fragments,
                metadata = LoadoutMetadata(createdAt = createdAt, updatedAt = updatedAt)
            )

        writeWorkspaceFile(
            "${Constants.LOADOUTS_DIR}/$name.json",
            serializer.serialize(loadout, Loadout.serializer()).unwrap("serialize loadout '$name'")
        )
        return loadout
    }

    fun seedFragment(relativePath: String, content: String) {
        writeWorkspaceFile(relativePath, content)
    }

    fun readGeneratedFile(fileName: String): String? = readWorkspaceFile(fileName)

    fun readGeneratedFileFromDirectory(directory: String, fileName: String): String? = readFile("$directory/$fileName")

    fun readGeneratedBody(fileName: String): String? = readGeneratedFile(fileName)?.stripGeneratedMetadata()

    fun readGeneratedBodyFromDirectory(directory: String, fileName: String): String? =
        readGeneratedFileFromDirectory(directory, fileName)?.stripGeneratedMetadata()

    fun createCustomOutputDirectory(name: String = "custom-output"): String {
        val absolutePath = workspacePath(name)
        withScope {
            createDirectories(absolutePath)
        }
        return absolutePath
    }

    override fun close() {
        deleteRecursively(workspaceRoot)
        deleteRecursively(homeRoot)
        deleteRecursively(xdgConfigRoot)
    }

    private fun writeFile(path: String, content: String) {
        withScope {
            createDirectories(path.substringBeforeLast("/", missingDelimiterValue = ""))
            fileRepository.writeFile(path, content).unwrap("write file '$path'")
        }
    }

    private fun readFile(path: String): String? =
        withScope {
            if (!fileRepository.fileExists(path)) {
                null
            } else {
                fileRepository.readFile(path).unwrap("read file '$path'")
            }
        }

    private fun <T> withScope(block: ApplicationScope.() -> T): T =
        withProcessContext {
            withApplicationScope(block)
        }

    private fun <T> withProcessContext(block: () -> T): T =
        withWorkingDirectoryAndEnvironment(
            workingDirectory = workspaceRoot,
            environment = processEnvironment(),
            block = block
        )

    private fun processEnvironment(): EnvironmentOverlay =
        environmentOverlay {
            "HOME" setTo homeRoot
            "XDG_CONFIG_HOME" setTo xdgConfigRoot
            "XDG_DATA_HOME" setTo homePath(".local/share")
            "XDG_STATE_HOME" setTo homePath(".local/state")
            "XDG_CACHE_HOME" setTo homePath(".cache")
            "PATH" setTo sanitizedProcessPath()
        }

    private fun gitEnvironment(): EnvironmentOverlay =
        environmentOverlay {
            unset(
                "GIT_DIR",
                "GIT_WORK_TREE",
                "GIT_AUTHOR_NAME",
                "GIT_AUTHOR_EMAIL",
                "GIT_COMMITTER_NAME",
                "GIT_COMMITTER_EMAIL",
            )
            "GIT_CONFIG_GLOBAL" setTo "/dev/null"
            "GIT_CONFIG_SYSTEM" setTo "/dev/null"
            "GIT_CONFIG_NOSYSTEM" setTo "true"
            "GIT_CEILING_DIRECTORIES" setTo gitCeilingDirectories
        }

    private fun ApplicationScope.createDirectories(path: String) {
        val normalizedPath = path.trim().trimEnd('/')
        if (normalizedPath.isBlank()) return

        val pathSegments = normalizedPath.split('/').filter { it.isNotBlank() }
        if (pathSegments.isEmpty()) return

        var currentPath = if (normalizedPath.startsWith("/")) "/" else ""

        pathSegments.forEach { segment ->
            currentPath =
                when {
                    currentPath.isEmpty() -> segment
                    currentPath == "/" -> "/$segment"
                    else -> "$currentPath/$segment"
                }

            fileRepository.createDirectory(currentPath).unwrap("create directory '$currentPath'")
        }
    }

    private fun sanitizedProcessPath(): String {
        val hostPath = readEnvironmentVariable("PATH").orEmpty()
        val separator = hostPath.pathListSeparator()
        val blockedRoots =
            listOf(workspaceRoot, projectRoot)
                .map(::normalizeLexicalPath)
                .distinct()

        val sanitizedEntries =
            hostPath
                .split(separator)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filter(::isAbsolutePath)
                .map(::normalizeLexicalPath)
                .filterNot { candidate ->
                    blockedRoots.any { root -> candidate == root || candidate.startsWith("$root/") }
                }

        check(sanitizedEntries.isNotEmpty()) {
            "PATH sanitization removed all safe entries from '$hostPath'"
        }

        return sanitizedEntries.joinToString(separator.toString())
    }

    companion object {
        private const val LOADOUT_E2E_HELPER_PATH_ENVIRONMENT_VARIABLE = "LOADOUT_E2E_HELPER_PATH"
        private const val HELPER_PRINT_ENVIRONMENT_COMMAND = "__printenv__"
        private val DEFAULT_LOADOUT_HELPER_PATH =
            "${currentWorkingDirectory()}/scripts/e2e/${defaultLoadoutHelperFileName()}"

        fun create(): E2eScenario =
            E2eScenario(
                workspaceRoot = createTemporaryDirectory("loadout-e2e-workspace"),
                homeRoot = createTemporaryDirectory("loadout-e2e-home"),
                xdgConfigRoot = createTemporaryDirectory("loadout-e2e-xdg")
            )
    }
}

private fun String.pathListSeparator(): Char = if (contains(';')) ';' else ':'

private fun isAbsolutePath(path: String): Boolean {
    val normalized = path.replace('\\', '/')
    return normalized.startsWith("/") || windowsAbsolutePath.matches(normalized)
}

private fun normalizeLexicalPath(path: String): String {
    val normalized = path.replace('\\', '/')
    val prefix =
        when {
            normalized.startsWith("//") -> "//"
            windowsAbsolutePath.matches(normalized) -> normalized.substring(0, 2)
            normalized.startsWith("/") -> "/"
            else -> ""
        }
    val remainder =
        when {
            prefix == "//" -> normalized.removePrefix("//")
            prefix.length == 2 -> normalized.substring(2).removePrefix("/")
            prefix == "/" -> normalized.removePrefix("/")
            else -> normalized
        }

    val segments = mutableListOf<String>()
    remainder
        .split('/')
        .filter { it.isNotEmpty() && it != "." }
        .forEach { segment ->
            if (segment == "..") {
                if (segments.isNotEmpty() && segments.last() != "..") {
                    segments.removeAt(segments.lastIndex)
                } else if (prefix.isEmpty()) {
                    segments += segment
                }
            } else {
                segments += segment
            }
        }

    val joined = segments.joinToString("/")
    return when {
        prefix == "//" -> if (joined.isEmpty()) "//" else "//$joined"
        prefix.length == 2 -> if (joined.isEmpty()) "$prefix/" else "$prefix/$joined"
        prefix == "/" -> if (joined.isEmpty()) "/" else "/$joined"
        else -> joined.ifEmpty { "." }
    }
}

private val windowsAbsolutePath = Regex("^[A-Za-z]:/.*")

private fun defaultLoadoutHelperFileName(): String =
    if (isWindowsLikeHost()) {
        "loadout-e2e-helper.cmd"
    } else {
        "loadout-e2e-helper.sh"
    }

private fun isWindowsLikeHost(): Boolean =
    listOf(
        readEnvironmentVariable("OS"),
        readEnvironmentVariable("ComSpec"),
        readEnvironmentVariable("PATHEXT"),
    ).filterNotNull().any { value ->
        value.contains("windows", ignoreCase = true) ||
            value.contains("cmd.exe", ignoreCase = true) ||
            value.contains(".cmd", ignoreCase = true)
    }

fun stripGeneratedMetadata(content: String): String {
    val normalized = content.normalizeLineEndings()
    return normalized.substringAfter("\n\n", missingDelimiterValue = normalized)
}

private fun CliktCommandTestResult.toCommandResult(): CommandResult =
    CommandResult(
        stdout = stdout.normalizeLineEndings(),
        stderr = stderr.normalizeLineEndings(),
        output = output.normalizeLineEndings(),
        exitCode = statusCode,
    )

private fun e2e.platform.ExternalProcessResult.toCommandResult(): CommandResult =
    CommandResult(
        stdout = stdout.normalizeLineEndings(),
        stderr = stderr.normalizeLineEndings(),
        output =
        listOf(stdout.normalizeLineEndings(), stderr.normalizeLineEndings())
            .filter { it.isNotBlank() }
            .joinToString("\n"),
        exitCode = exitCode,
    )

private fun String.normalizeLineEndings(): String = replace("\r\n", "\n")

private fun String.stripGeneratedMetadata(): String = stripGeneratedMetadata(this)

private fun CommandResult.requireSuccess(context: String) {
    check(exitCode == 0) {
        "$context failed with exit $exitCode:\n$output"
    }
}

private fun <T, E> Result<T, E>.unwrap(context: String): T =
    when (this) {
        is Result.Success -> value
        is Result.Error -> error("$context failed: $error")
    }
