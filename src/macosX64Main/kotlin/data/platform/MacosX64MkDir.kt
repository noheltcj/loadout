package data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.S_IRGRP
import platform.posix.S_IROTH
import platform.posix.S_IRWXU
import platform.posix.S_IXGRP
import platform.posix.S_IXOTH
import platform.posix.mkdir

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformMkdir(path: String): Int =
    mkdir(path, (S_IRWXU or S_IRGRP or S_IXGRP or S_IROTH or S_IXOTH).toUShort())
