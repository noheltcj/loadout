package data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.FILE_ATTRIBUTE_REPARSE_POINT
import platform.windows.FILE_FLAG_BACKUP_SEMANTICS
import platform.windows.FILE_NAME_NORMALIZED
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GetFileAttributesW
import platform.windows.GetFinalPathNameByHandleW
import platform.windows.INVALID_FILE_ATTRIBUTES
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_EXISTING
import platform.windows.WCHARVar

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformIsSymbolicLink(path: String): Boolean {
    val attributes = GetFileAttributesW(path)
    return attributes != INVALID_FILE_ATTRIBUTES &&
        (attributes and FILE_ATTRIBUTE_REPARSE_POINT.toUInt()) != 0u
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformCanonicalPath(path: String): String? =
    memScoped {
        val handle =
            CreateFileW(
                lpFileName = path,
                dwDesiredAccess = 0u,
                dwShareMode = FILE_SHARE_READ.toUInt() or FILE_SHARE_WRITE.toUInt() or FILE_SHARE_DELETE.toUInt(),
                lpSecurityAttributes = null,
                dwCreationDisposition = OPEN_EXISTING.toUInt(),
                dwFlagsAndAttributes = FILE_FLAG_BACKUP_SEMANTICS.toUInt(),
                hTemplateFile = null,
            )
        if (handle == INVALID_HANDLE_VALUE) return@memScoped null

        try {
            val buffer = allocArray<WCHARVar>(WINDOWS_MAX_PATH_LENGTH)
            val length =
                GetFinalPathNameByHandleW(
                    handle,
                    buffer,
                    WINDOWS_MAX_PATH_LENGTH.toUInt(),
                    FILE_NAME_NORMALIZED.toUInt(),
                )
            if (length == 0u || length >= WINDOWS_MAX_PATH_LENGTH.toUInt()) {
                null
            } else {
                buffer.toKString()
            }
        } finally {
            CloseHandle(handle)
        }
    }

private const val WINDOWS_MAX_PATH_LENGTH = 32_768
