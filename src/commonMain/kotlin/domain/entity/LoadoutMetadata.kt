package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class LoadoutMetadata(
    val createdAt: Long,
    val updatedAt: Long,
    val version: String = "1.0.0",
    val tags: List<String> = emptyList()
) {
    fun withUpdatedTimestamp(currentTimeMillis: Long): LoadoutMetadata = copy(updatedAt = currentTimeMillis)
}