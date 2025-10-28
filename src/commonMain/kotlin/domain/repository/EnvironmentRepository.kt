package domain.repository

interface EnvironmentRepository {
    fun getHomeDirectory(): String?

    fun currentTimeMillis(): Long
}
