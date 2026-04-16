package e2e.support

import cli.Constants
import cli.createLoadoutCommand
import cli.di.ApplicationScope
import cli.di.withApplicationScope
import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import data.serialization.JsonSerializer
import domain.entity.Loadout
import domain.entity.LoadoutConfig
import domain.entity.LoadoutMetadata
import domain.entity.packaging.Result
import e2e.platform.createTemporaryDirectory
import e2e.platform.currentWorkingDirectory
import e2e.platform.deleteRecursively
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
    private val serializer = JsonSerializer()
    private val gitCeilingDirectories = workspaceRoot.substringBeforeLast("/", missingDelimiterValue = workspaceRoot)

    fun runCommand(vararg args: String): CommandResult =
        withProcessContext {
            withApplicationScope {
                createLoadoutCommand(this).test(args.toList())
            }.toCommandResult()
        }

    fun runExternalCommand(
        vararg args: String,
        workingDirectory: String = workspaceRoot,
        environment: Map<String, String> = emptyMap(),
    ): CommandResult =
        runExternalProcess(
            workingDirectory = workingDirectory,
            command = args.toList(),
            environment = processEnvironment() + environment
        ).toCommandResult()

    fun runGit(
        vararg args: String,
        workingDirectory: String = workspaceRoot,
        environment: Map<String, String> = emptyMap(),
    ): CommandResult =
        runExternalCommand(
            "git",
            *args,
            workingDirectory = workingDirectory,
            environment = gitEnvironment() + environment
        )

    fun initializeGitRepository(workingDirectory: String = workspaceRoot) {
        runGit("init", workingDirectory = workingDirectory).requireSuccess("git init")
        runGit("config", "user.name", "Loadout E2E", workingDirectory = workingDirectory)
            .requireSuccess("git config user.name")
        runGit("config", "user.email", "loadout@example.test", workingDirectory = workingDirectory)
            .requireSuccess("git config user.email")
    }

    fun commitAllFiles(
        message: String,
        workingDirectory: String = workspaceRoot,
    ) {
        runGit("add", "-A", workingDirectory = workingDirectory).requireSuccess("git add -A")
        runGit("commit", "-m", message, workingDirectory = workingDirectory).requireSuccess("git commit")
    }

    fun switchGitBranch(
        branchName: String,
        create: Boolean = false,
        workingDirectory: String = workspaceRoot,
    ): CommandResult =
        if (create) {
            runGit("switch", "-c", branchName, workingDirectory = workingDirectory)
        } else {
            runGit("switch", branchName, workingDirectory = workingDirectory)
        }

    fun addGitWorktree(
        relativePath: String,
        branchName: String? = null,
        sourceDirectory: String = workspaceRoot,
    ): CommandResult {
        val worktreePath = workspacePath(relativePath)
        val arguments = buildList {
            add("worktree")
            add("add")
            add(worktreePath)
            branchName?.let(::add)
        }
        return runGit(*arguments.toTypedArray(), workingDirectory = sourceDirectory)
    }

    fun readGitLocalConfig(
        key: String,
        workingDirectory: String = workspaceRoot,
    ): String? {
        val result = runGit("config", "--local", "--get", key, workingDirectory = workingDirectory)
        return if (result.exitCode == 0) {
            result.stdout.trim().ifBlank { null }
        } else {
            null
        }
    }

    fun loadoutHelperExecutablePath(): String =
        readEnvironmentVariable(loadoutHelperEnvironmentVariable) ?: defaultLoadoutHelperPath

    fun workspacePath(relativePath: String): String =
        if (relativePath.isBlank()) workspaceRoot else "$workspaceRoot/$relativePath"

    fun homePath(relativePath: String): String = if (relativePath.isBlank()) homeRoot else "$homeRoot/$relativePath"

    fun xdgConfigPath(relativePath: String): String =
        if (relativePath.isBlank()) xdgConfigRoot else "$xdgConfigRoot/$relativePath"

    fun writeWorkspaceFile(
        relativePath: String,
        content: String,
    ) {
        writeFile(relativePath, content)
    }

    fun writeHomeFile(
        relativePath: String,
        content: String,
    ) {
        writeFile(homePath(relativePath), content)
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

    fun readHomeFile(relativePath: String): String? = readFile(homePath(relativePath))

    fun workspaceFileExists(relativePath: String): Boolean = fileExists(relativePath)

    fun homeFileExists(relativePath: String): Boolean = fileExists(homePath(relativePath))

    fun readConfig(): LoadoutConfig? =
        readWorkspaceFile(Constants.CONFIG_FILE)?.let {
            serializer.deserialize(it, LoadoutConfig.serializer()).unwrap("deserialize config")
        }

    fun readConfigFromDirectory(directory: String): LoadoutConfig? =
        readFile("$directory/${Constants.CONFIG_FILE}")?.let {
            serializer.deserialize(it, LoadoutConfig.serializer()).unwrap("deserialize config in '$directory'")
        }

    fun readRepoSettings(): RepoSettings? =
        readWorkspaceFile(repoSettingsPath)?.let {
            serializer.deserialize(it, RepoSettings.serializer()).unwrap("deserialize repo settings")
        }

    fun writeConfig(config: LoadoutConfig) {
        writeWorkspaceFile(
            Constants.CONFIG_FILE,
            serializer.serialize(config, LoadoutConfig.serializer()).unwrap("serialize config")
        )
    }

    fun writeRepoSettings(settings: RepoSettings) {
        writeWorkspaceFile(
            repoSettingsPath,
            serializer.serialize(settings, RepoSettings.serializer()).unwrap("serialize repo settings")
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

    fun seedFragment(
        relativePath: String,
        content: String,
    ) {
        writeWorkspaceFile(relativePath, content)
    }

    fun seedGlobalFragment(
        relativePath: String,
        content: String,
    ) {
        writeHomeFile(".loadout/fragments/$relativePath", content)
    }

    fun readGeneratedFile(fileName: String): String? = readWorkspaceFile(fileName)

    fun readGeneratedFileFromDirectory(
        directory: String,
        fileName: String,
    ): String? = readFile("$directory/$fileName")

    fun readGeneratedBody(fileName: String): String? = readGeneratedFile(fileName)?.stripGeneratedMetadata()

    fun readGeneratedBodyFromDirectory(
        directory: String,
        fileName: String,
    ): String? = readGeneratedFileFromDirectory(directory, fileName)?.stripGeneratedMetadata()

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

    private fun writeFile(
        path: String,
        content: String,
    ) {
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

    private fun fileExists(path: String): Boolean =
        withScope {
            fileRepository.fileExists(path)
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

    private fun processEnvironment(): Map<String, String> =
        mapOf(
            "HOME" to homeRoot,
            "XDG_CONFIG_HOME" to xdgConfigRoot,
        )

    private fun gitEnvironment(): Map<String, String> =
        mapOf(
            "GIT_CONFIG_GLOBAL" to "/dev/null",
            "GIT_CONFIG_SYSTEM" to "/dev/null",
            "GIT_CONFIG_NOSYSTEM" to "true",
            "GIT_CEILING_DIRECTORIES" to gitCeilingDirectories,
        )

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

    companion object {
        private const val loadoutHelperEnvironmentVariable = "LOADOUT_E2E_HELPER_PATH"
        private const val repoSettingsPath = ".loadout.repo.json"
        private val defaultLoadoutHelperPath = "${currentWorkingDirectory()}/build/e2e-helper/loadout-e2e-helper"

        fun create(): E2eScenario =
            E2eScenario(
                workspaceRoot = createTemporaryDirectory("loadout-e2e-workspace"),
                homeRoot = createTemporaryDirectory("loadout-e2e-home"),
                xdgConfigRoot = createTemporaryDirectory("loadout-e2e-xdg")
            )
    }
}

fun stripGeneratedMetadata(content: String): String {
    val normalized = content.normalizeLineEndings()
    return normalized.substringAfter("\n\n", missingDelimiterValue = normalized)
}

fun normalizeText(text: String): String = text.normalizeLineEndings()

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
