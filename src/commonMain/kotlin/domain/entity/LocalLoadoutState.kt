package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class LocalLoadoutState(
    val activeLoadoutName: String?,
    val lastComposedContentHash: String?,
)
