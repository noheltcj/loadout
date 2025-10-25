package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class LoadoutConfig(
    val currentLoadoutName: String?,
)

