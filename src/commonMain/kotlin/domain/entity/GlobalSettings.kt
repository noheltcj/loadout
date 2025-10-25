package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class GlobalSettings(
    val dryRunByDefault: Boolean = false,
    val verboseOutput: Boolean = false,
    val includeMetadata: Boolean = false
)