package data.repository

import domain.repository.EnvironmentRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.getenv
import platform.posix.timespec

@OptIn(ExperimentalForeignApi::class)
class NativeEnvironmentRepository : EnvironmentRepository {
    override fun getHomeDirectory(): String? = getenv("HOME")?.toKString()

    override fun currentTimeMillis(): Long {
        memScoped {
            val timeSpec = alloc<timespec>()
            clock_gettime(CLOCK_REALTIME.convert(), timeSpec.ptr)
            // TODO: Account for missing nanos
            return timeSpec.tv_sec * 1000L
        }
    }
}
