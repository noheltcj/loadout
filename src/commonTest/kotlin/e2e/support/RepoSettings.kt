package e2e.support

import kotlinx.serialization.Serializable

@Serializable
data class RepoSettings(
    val defaultLoadoutName: String?,
)
