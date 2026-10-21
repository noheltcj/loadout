package data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.S_IFLNK
import platform.posix.S_IFMT
import platform.posix.free
import platform.posix.lstat
import platform.posix.realpath
import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformIsSymbolicLink(path: String): Boolean =
    memScoped {
        val statBuffer = alloc<stat>()
        lstat(path, statBuffer.ptr) == 0 &&
            (statBuffer.st_mode.toUInt() and S_IFMT.toUInt()) == S_IFLNK.toUInt()
    }

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformCanonicalPath(path: String): String? {
    val resolved = realpath(path, null) ?: return null
    return try {
        resolved.toKString()
    } finally {
        free(resolved)
    }
}
