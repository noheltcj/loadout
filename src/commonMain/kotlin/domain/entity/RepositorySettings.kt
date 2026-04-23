package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class RepositorySettings(
    val defaultLoadoutName: String?,
)
