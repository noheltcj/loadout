package infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformMkdir(path: String): Int {
    return mkdir(path, (S_IRWXU or S_IRGRP or S_IXGRP or S_IROTH or S_IXOTH).toUInt())
}
