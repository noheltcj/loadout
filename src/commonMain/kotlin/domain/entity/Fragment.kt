package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class Fragment(
    val name: String,
    val path: String,
    val content: String,
    val metadata: FragmentMetadata,
)
