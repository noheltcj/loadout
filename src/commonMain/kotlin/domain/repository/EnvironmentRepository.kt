package domain.repository

import domain.entity.UserConfigDirectory

interface EnvironmentRepository {
    fun getUserConfigDirectory(): UserConfigDirectory

    fun currentTimeMillis(): Long
}
