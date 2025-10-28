package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class FragmentMetadata(
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)
