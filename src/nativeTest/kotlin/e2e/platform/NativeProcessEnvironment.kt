package e2e.platform

import data.platform.platformClearEnv
import data.platform.platformMkdir
import data.platform.platformSetEnv
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.chdir
import platform.posix.chmod
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.rmdir
import platform.posix.stat
import platform.posix.system
import platform.posix.unlink
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.random.Random

private const val pathBufferSize = 4096
private const val executablePermissionMask = 493
private const val anyExecutableBitMask = 73u

internal enum class ShellDialect {
    Posix,
    WindowsCmd,
}

internal interface NativeProcessOperations {
    fun currentWorkingDirectory(): String

    fun changeWorkingDirectory(path: String): Int

    fun readEnvironmentVariable(key: String): String?

    fun setEnvironmentVariable(
        key: String,
        value: String,
    ): Int

    fun clearEnvironmentVariable(key: String): Int

    fun executeShellCommand(command: String): Int

    fun createTemporaryDirectory(prefix: String): String

    fun deleteRecursively(path: String)

    fun readFileIfPresent(path: String): String
}

private object PosixNativeProcessOperations : NativeProcessOperations {
    override fun currentWorkingDirectory(): String = getCurrentWorkingDirectory()

    @OptIn(ExperimentalForeignApi::class)
    override fun changeWorkingDirectory(path: String): Int = chdir(path)

    override fun readEnvironmentVariable(key: String): String? = readEnvironmentVariableInternal(key)

    override fun setEnvironmentVariable(
        key: String,
        value: String,
    ): Int = platformSetEnv(key, value)

    override fun clearEnvironmentVariable(key: String): Int = platformClearEnv(key)

    @OptIn(ExperimentalForeignApi::class)
    override fun executeShellCommand(command: String): Int = system(command)

    override fun createTemporaryDirectory(prefix: String): String = createTemporaryDirectoryInternal(prefix)

    override fun deleteRecursively(path: String) {
        deleteRecursivelyInternal(path)
    }

    override fun readFileIfPresent(path: String): String = readFileIfPresentInternal(path)
}

actual fun createTemporaryDirectory(prefix: String): String = createTemporaryDirectoryInternal(prefix)

actual fun deleteRecursively(path: String) {
    deleteRecursivelyInternal(path)
}

actual fun <T> withWorkingDirectoryAndHome(
    workingDirectory: String,
    homeDirectory: String,
    block: () -> T,
): T =
    withWorkingDirectoryAndEnvironment(
        workingDirectory = workingDirectory,
        environment =
            environmentOverlay {
                "HOME" setTo homeDirectory
            },
        block = block
    )

actual fun <T> withWorkingDirectoryAndEnvironment(
    workingDirectory: String,
    environment: EnvironmentOverlay,
    block: () -> T,
): T = withWorkingDirectoryAndEnvironment(PosixNativeProcessOperations, workingDirectory, environment, block)

internal fun <T> withWorkingDirectoryAndEnvironment(
    operations: NativeProcessOperations,
    workingDirectory: String,
    environment: EnvironmentOverlay,
    block: () -> T,
): T {
    val originalWorkingDirectory = operations.currentWorkingDirectory()
    val originalEnvironment = environment.mutationKeys().associateWith(operations::readEnvironmentVariable)

    var result: T? = null
    var failure: Throwable? = null

    try {
        check(operations.changeWorkingDirectory(workingDirectory) == 0) {
            "Failed to change working directory to '$workingDirectory'"
        }
        applyEnvironmentOverlay(operations, environment)
        result = block()
    } catch (throwable: Throwable) {
        failure = throwable
    }

    val cleanupFailure = restoreProcessContext(operations, originalWorkingDirectory, originalEnvironment)

    @Suppress("UNCHECKED_CAST")
    when {
        failure != null && cleanupFailure != null -> {
            failure.addSuppressed(cleanupFailure)
            throw failure
        }

        failure != null -> throw failure
        cleanupFailure != null -> throw cleanupFailure
        else -> return result as T
    }
}

actual fun runExternalProcess(
    workingDirectory: String,
    command: List<String>,
    environment: EnvironmentOverlay,
): ExternalProcessResult = runExternalProcess(PosixNativeProcessOperations, workingDirectory, command, environment)

internal fun runExternalProcess(
    operations: NativeProcessOperations,
    workingDirectory: String,
    command: List<String>,
    environment: EnvironmentOverlay,
): ExternalProcessResult {
    require(command.isNotEmpty()) { "External process command must not be empty" }

    val captureDirectory = operations.createTemporaryDirectory("loadout-e2e-capture")
    val stdoutPath = "$captureDirectory/stdout.txt"
    val stderrPath = "$captureDirectory/stderr.txt"
    val redirectingCommand = buildRedirectingCommand(command, stdoutPath, stderrPath)

    var stdout = ""
    var stderr = ""
    var exitCode = 0
    var failure: Throwable? = null

    try {
        exitCode =
            withWorkingDirectoryAndEnvironment(operations, workingDirectory, environment) {
                decodeSystemExitCode(operations.executeShellCommand(redirectingCommand))
            }
        stdout = operations.readFileIfPresent(stdoutPath)
        stderr = operations.readFileIfPresent(stderrPath)
    } catch (throwable: Throwable) {
        failure = throwable
    }

    val cleanupFailure = runCatching { operations.deleteRecursively(captureDirectory) }.exceptionOrNull()

    when {
        failure != null && cleanupFailure != null -> {
            failure.addSuppressed(cleanupFailure)
            throw failure
        }

        failure != null -> throw failure
        cleanupFailure != null -> throw cleanupFailure
        else -> return ExternalProcessResult(stdout = stdout, stderr = stderr, exitCode = exitCode)
    }
}

actual fun readEnvironmentVariable(key: String): String? = readEnvironmentVariableInternal(key)

actual fun currentWorkingDirectory(): String = getCurrentWorkingDirectory()

@OptIn(ExperimentalForeignApi::class)
actual fun isExecutablePath(path: String): Boolean =
    memScoped {
        val statBuffer = alloc<stat>()
        if (stat(path, statBuffer.ptr) != 0) {
            return false
        }

        statBuffer.st_mode.toUInt() and anyExecutableBitMask != 0u
    }

@OptIn(ExperimentalForeignApi::class)
actual fun setExecutable(path: String) {
    check(chmod(path, executablePermissionMask.convert()) == 0) {
        "Failed to set executable bit on '$path'"
    }
}

private fun applyEnvironmentOverlay(
    operations: NativeProcessOperations,
    environment: EnvironmentOverlay,
) {
    environment.forEachMutation { key, mutation ->
        when (mutation) {
            is EnvironmentMutation.Set -> {
                check(operations.setEnvironmentVariable(key, mutation.value) == 0) {
                    "Failed to set $key to '${mutation.value}'"
                }
            }

            EnvironmentMutation.Unset -> {
                check(operations.clearEnvironmentVariable(key) == 0) {
                    "Failed to clear $key"
                }
            }
        }
    }
}

private fun restoreProcessContext(
    operations: NativeProcessOperations,
    originalWorkingDirectory: String,
    originalEnvironment: Map<String, String?>,
): Throwable? {
    val failures = mutableListOf<String>()

    if (operations.changeWorkingDirectory(originalWorkingDirectory) != 0) {
        failures += "Failed to restore working directory to '$originalWorkingDirectory'"
    }

    originalEnvironment.forEach { (key, value) ->
        val restoreStatus =
            when (value) {
                null -> operations.clearEnvironmentVariable(key)
                else -> operations.setEnvironmentVariable(key, value)
            }

        if (restoreStatus != 0) {
            failures +=
                when (value) {
                    null -> "Failed to clear $key during cleanup"
                    else -> "Failed to restore $key to '$value'"
                }
        }
    }

    return failures
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString(separator = "\n")
        ?.let(::IllegalStateException)
}

@OptIn(ExperimentalForeignApi::class)
private fun createTemporaryDirectoryInternal(prefix: String): String {
    val tempDir =
        (
            getenv("TMPDIR")?.toKString()
                ?: getenv("TEMP")?.toKString()
                ?: getenv("TMP")?.toKString()
                ?: "/tmp"
        ).removeSuffix("/")

    while (true) {
        val randomSuffix = Random.nextInt(1000000, 9999999)
        val path = "$tempDir/$prefix-$randomSuffix"
        if (platformMkdir(path) == 0) {
            return path
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun deleteRecursivelyInternal(path: String) {
    if (!pathExists(path)) return

    if (isDirectory(path)) {
        val directory = checkNotNull(opendir(path)) { "Failed to open directory '$path' for cleanup" }
        try {
            while (true) {
                val entry = readdir(directory) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue

                deleteRecursivelyInternal("$path/$name")
            }
        } finally {
            closedir(directory)
        }

        check(rmdir(path) == 0) { "Failed to remove directory '$path'" }
    } else {
        check(unlink(path) == 0) { "Failed to remove file '$path'" }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun getCurrentWorkingDirectory(): String =
    memScoped {
        val buffer = allocArray<ByteVar>(pathBufferSize)
        checkNotNull(getcwd(buffer, pathBufferSize.convert())) {
            "Failed to read the current working directory"
        }
        buffer.toKString()
    }

@OptIn(ExperimentalForeignApi::class)
private fun readEnvironmentVariableInternal(key: String): String? = getenv(key)?.toKString()

@OptIn(ExperimentalForeignApi::class)
private fun isDirectory(path: String): Boolean =
    memScoped {
        val statBuffer = alloc<stat>()
        if (platform.posix.stat(path, statBuffer.ptr) != 0) {
            return false
        }

        (statBuffer.st_mode.toUInt() and S_IFMT.toUInt()) == S_IFDIR.toUInt()
    }

@OptIn(ExperimentalForeignApi::class)
private fun pathExists(path: String): Boolean =
    memScoped {
        val statBuffer = alloc<stat>()
        stat(path, statBuffer.ptr) == 0
    }

@OptIn(ExperimentalNativeApi::class)
private fun currentShellDialect(): ShellDialect =
    if (Platform.osFamily.name.contains("windows", ignoreCase = true) ||
        Platform.osFamily.name.contains("mingw", ignoreCase = true)
    ) {
        ShellDialect.WindowsCmd
    } else {
        ShellDialect.Posix
    }

internal fun buildRedirectingCommand(
    command: List<String>,
    stdoutPath: String,
    stderrPath: String,
    shellDialect: ShellDialect = currentShellDialect(),
): String =
    "${buildShellCommand(
        command,
        shellDialect
    )} > ${shellQuote(stdoutPath, shellDialect)} 2> ${shellQuote(stderrPath, shellDialect)}"

internal fun buildShellCommand(
    arguments: List<String>,
    shellDialect: ShellDialect = currentShellDialect(),
): String = arguments.joinToString(" ") { argument -> shellQuote(argument, shellDialect) }

internal fun shellQuote(
    argument: String,
    shellDialect: ShellDialect = currentShellDialect(),
): String =
    when (shellDialect) {
        ShellDialect.Posix -> "'${argument.replace("'", "'\"'\"'")}'"
        ShellDialect.WindowsCmd -> "\"${argument.replace("^", "^^").replace("%", "%%").replace("\"", "\"\"")}\""
    }

private fun decodeSystemExitCode(status: Int): Int =
    when {
        status < 0 -> 1
        else -> (status shr 8) and 0xff
    }

@OptIn(ExperimentalForeignApi::class)
private fun readFileIfPresentInternal(path: String): String {
    if (!pathExists(path)) {
        return ""
    }

    val file = checkNotNull(fopen(path, "r")) { "Failed to open '$path' for reading" }

    return try {
        val content = StringBuilder()
        val buffer = ByteArray(1024)

        while (true) {
            val bytesRead =
                buffer.usePinned { pinned ->
                    fread(pinned.addressOf(0), 1u, buffer.size.toULong(), file)
                }

            if (bytesRead == 0uL) {
                break
            }

            content.append(buffer.decodeToString(0, bytesRead.toInt()))
        }

        content.toString().replace("\r\n", "\n")
    } finally {
        fclose(file)
    }
}
