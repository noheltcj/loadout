package common

import platform.posix.clock
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
    return clock().toLong() * 1000L / platform.posix.CLOCKS_PER_SEC.toLong()
}