package e2e.platform

import data.platform.platformClearEnv
import data.platform.platformMkdir
import data.platform.platformSetEnv
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.chdir
import platform.posix.closedir
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.rmdir
import platform.posix.stat
import platform.posix.unlink
import kotlin.random.Random

private const val PATH_BUFFER_SIZE = 4096

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
actual fun <T> withWorkingDirectoryAndHome(workingDirectory: String, homeDirectory: String, block: () -> T): T {
    val originalWorkingDirectory = getCurrentWorkingDirectory()
    val originalHome = getenv("HOME")?.toKString()

    check(chdir(workingDirectory) == 0) { "Failed to change working directory to '$workingDirectory'" }
    setHomeDirectory(homeDirectory)

    return try {
        block()
    } finally {
        check(chdir(originalWorkingDirectory) == 0) {
            "Failed to restore working directory to '$originalWorkingDirectory'"
        }

        if (originalHome == null) {
            check(platformClearEnv("HOME") == 0) { "Failed to clear HOME during cleanup" }
        } else {
            setHomeDirectory(originalHome)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun getCurrentWorkingDirectory(): String =
    memScoped {
        val buffer = allocArray<ByteVar>(PATH_BUFFER_SIZE)
        checkNotNull(getcwd(buffer, PATH_BUFFER_SIZE.convert())) {
            "Failed to read the current working directory"
        }
        buffer.toKString()
    }

private fun setHomeDirectory(path: String) {
    check(platformSetEnv("HOME", path) == 0) { "Failed to set HOME to '$path'" }
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
