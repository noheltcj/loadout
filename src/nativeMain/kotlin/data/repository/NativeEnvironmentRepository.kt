package data.repository

import domain.repository.EnvironmentRepository
import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.clock_gettime

@OptIn(ExperimentalForeignApi::class)
class NativeEnvironmentRepository : EnvironmentRepository {
    override fun getHomeDirectory(): String? {
        return getenv("HOME")?.toKString()
    }

    override fun currentTimeMillis(): Long {
        memScoped {
            val timeSpec = alloc<timespec>()
            clock_gettime(CLOCK_REALTIME.convert(), timeSpec.ptr)
            // TODO: Account for missing nanos
            return timeSpec.tv_sec * 1000L
        }
    }
}
