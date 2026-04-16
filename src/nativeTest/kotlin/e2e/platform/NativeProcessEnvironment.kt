@file:Suppress("ktlint:standard:property-naming")

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
import kotlin.random.Random

private const val pathBufferSize = 4096
private const val executablePermissionMask = 493
private const val anyExecutableBitMask = 73u

@OptIn(ExperimentalForeignApi::class)
actual fun createTemporaryDirectory(prefix: String): String {
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
actual fun deleteRecursively(path: String) {
    if (!pathExists(path)) return

    if (isDirectory(path)) {
        val directory = checkNotNull(opendir(path)) { "Failed to open directory '$path' for cleanup" }
        try {
            while (true) {
                val entry = readdir(directory) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue

                deleteRecursively("$path/$name")
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
actual fun <T> withWorkingDirectoryAndHome(
    workingDirectory: String,
    homeDirectory: String,
    block: () -> T,
): T = withWorkingDirectoryAndEnvironment(workingDirectory, environment = mapOf("HOME" to homeDirectory), block = block)

@OptIn(ExperimentalForeignApi::class)
actual fun <T> withWorkingDirectoryAndEnvironment(
    workingDirectory: String,
    environment: Map<String, String>,
    block: () -> T,
): T {
    val originalWorkingDirectory = getCurrentWorkingDirectory()
    val originalEnvironment = environment.keys.associateWith(::readEnvironmentVariable)

    check(chdir(workingDirectory) == 0) { "Failed to change working directory to '$workingDirectory'" }
    environment.forEach { (key, value) ->
        setEnvironmentVariable(key, value)
    }

    return try {
        block()
    } finally {
        check(chdir(originalWorkingDirectory) == 0) {
            "Failed to restore working directory to '$originalWorkingDirectory'"
        }

        originalEnvironment.forEach { (key, value) ->
            restoreEnvironmentVariable(key, value)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun runExternalProcess(
    workingDirectory: String,
    command: List<String>,
    environment: Map<String, String>,
): ExternalProcessResult {
    require(command.isNotEmpty()) { "External process command must not be empty" }

    val captureDirectory = createTemporaryDirectory("loadout-e2e-capture")
    val stdoutPath = "$captureDirectory/stdout.txt"
    val stderrPath = "$captureDirectory/stderr.txt"
    val shellCommand = buildShellCommand(command)
    val redirectingCommand = "$shellCommand > ${shellQuote(stdoutPath)} 2> ${shellQuote(stderrPath)}"

    val exitCode =
        withWorkingDirectoryAndEnvironment(workingDirectory, environment) {
            decodeSystemExitCode(system(redirectingCommand))
        }

    val stdout = readFileIfPresent(stdoutPath)
    val stderr = readFileIfPresent(stderrPath)
    deleteRecursively(captureDirectory)

    return ExternalProcessResult(stdout = stdout, stderr = stderr, exitCode = exitCode)
}

@OptIn(ExperimentalForeignApi::class)
actual fun readEnvironmentVariable(key: String): String? = getenv(key)?.toKString()

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

@OptIn(ExperimentalForeignApi::class)
private fun getCurrentWorkingDirectory(): String =
    memScoped {
        val buffer = allocArray<ByteVar>(pathBufferSize)
        checkNotNull(getcwd(buffer, pathBufferSize.convert())) {
            "Failed to read the current working directory"
        }
        buffer.toKString()
    }

private fun setEnvironmentVariable(
    key: String,
    value: String,
) {
    check(platformSetEnv(key, value) == 0) { "Failed to set $key to '$value'" }
}

private fun restoreEnvironmentVariable(
    key: String,
    value: String?,
) {
    when (value) {
        null -> check(platformClearEnv(key) == 0) { "Failed to clear $key during cleanup" }
        else -> setEnvironmentVariable(key, value)
    }
}

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

private fun buildShellCommand(arguments: List<String>): String = arguments.joinToString(" ", transform = ::shellQuote)

private fun shellQuote(argument: String): String = "'${argument.replace("'", "'\"'\"'")}'"

private fun decodeSystemExitCode(status: Int): Int =
    when {
        status < 0 -> 1
        else -> (status shr 8) and 0xff
    }

@OptIn(ExperimentalForeignApi::class)
private fun readFileIfPresent(path: String): String {
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
