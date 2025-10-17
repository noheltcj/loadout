package infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformMkdir(path: String): Int {
    // MinGW's mkdir only takes one parameter (no permission bits on Windows)
    return mkdir(path)
}
