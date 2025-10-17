package common

import platform.posix.clock
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
    return clock().toString().toLong() * 1000L / platform.posix.CLOCKS_PER_SEC.toString().toLong()
}