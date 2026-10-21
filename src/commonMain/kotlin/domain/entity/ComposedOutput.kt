package domain.entity

import domain.policy.GENERATED_OUTPUT_HEADER
import kotlinx.serialization.Serializable

@Serializable
data class ComposedOutput(
    val loadoutName: String,
    val content: String,
    val fragmentCount: Int,
    val metadata: CompositionMetadata,
) {
    fun toFileContent(includeMetadata: Boolean = false): String =
        buildString {
            if (includeMetadata) {
                appendLine(GENERATED_OUTPUT_HEADER)
                appendLine("<!-- Loadout: $loadoutName -->")
                appendLine("<!-- Fragments: $fragmentCount -->")
                appendLine()
            }
            append(content)
        }
}
