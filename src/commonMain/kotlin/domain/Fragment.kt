package domain

import kotlinx.serialization.Serializable
import common.currentTimeMillis

@Serializable
data class Fragment(
    val path: String,
    val content: String,
    val metadata: FragmentMetadata = FragmentMetadata()
) {
    init {
        require(path.isNotBlank()) { "Fragment path cannot be blank" }
        require(content.isNotBlank()) { "Fragment content cannot be blank" }
    }
    
    val name: String get() = path.substringAfterLast('/')
    val extension: String get() = path.substringAfterLast('.', "")
    
    fun validate(): List<String> = buildList {
        if (path.isBlank()) add("Path is required")
        if (content.isBlank()) add("Content is required")
        if (!path.endsWith(".md")) add("Fragment must be a Markdown file (.md)")
    }
}

@Serializable
data class FragmentMetadata(
    val description: String = "",
    val tags: List<String> = emptyList(),
    val priority: Int = 0,
    val createdAt: Long = currentTimeMillis(),
    val updatedAt: Long = currentTimeMillis()
) {
    fun withUpdatedTimestamp(): FragmentMetadata = copy(updatedAt = currentTimeMillis())
}