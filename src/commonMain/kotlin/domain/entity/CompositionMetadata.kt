package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class CompositionMetadata(
    val generatedAt: Long,
    val fragmentPaths: List<String> = emptyList(),
    val totalLines: Int = 0,
    val totalCharacters: Int = 0,
    val contentHash: String,
) {
    companion object {
        fun from(
            content: String,
            fragmentPaths: List<String>,
            currentTimeMillis: Long,
        ): CompositionMetadata =
            CompositionMetadata(
                generatedAt = currentTimeMillis,
                fragmentPaths = fragmentPaths,
                totalLines = content.lines().size,
                totalCharacters = content.length,
                contentHash = calculateHash(content)
            )

        private fun calculateHash(content: String): String =
            content
                .hashCode()
                .toUInt()
                .toString(16)
                .padStart(8, '0')
    }
}
