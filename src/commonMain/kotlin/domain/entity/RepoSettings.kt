package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class RepoSettings(
    val defaultLoadoutName: String?,
)
