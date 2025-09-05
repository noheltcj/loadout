package domain

import kotlinx.serialization.Serializable
import common.currentTimeMillis

@Serializable
data class Loadout(
    val name: String,
    val description: String = "",
    val fragments: List<String> = emptyList(),
    val metadata: LoadoutMetadata = LoadoutMetadata()
) {
    init {
        require(name.isNotBlank()) { "Loadout name cannot be blank" }
        require(name.matches(Regex("^[a-zA-Z0-9_-]+$"))) { 
            "Loadout name must contain only alphanumeric characters, underscores, and hyphens" 
        }
    }
    
    fun addFragment(fragmentPath: String, afterFragment: String? = null): Loadout {
        val updatedFragments = if (afterFragment != null) {
            val index = fragments.indexOf(afterFragment)
            if (index >= 0) {
                fragments.toMutableList().apply { add(index + 1, fragmentPath) }
            } else {
                fragments + fragmentPath
            }
        } else {
            fragments + fragmentPath
        }
        
        return copy(
            fragments = updatedFragments.distinct(),
            metadata = metadata.withUpdatedTimestamp()
        )
    }
    
    fun removeFragment(fragmentPath: String): Loadout = copy(
        fragments = fragments - fragmentPath,
        metadata = metadata.withUpdatedTimestamp()
    )
    
    fun moveFragment(fragmentPath: String, afterFragment: String?): Loadout {
        val withoutFragment = removeFragment(fragmentPath)
        return withoutFragment.addFragment(fragmentPath, afterFragment)
    }
    
    fun validate(): List<String> = buildList {
        if (name.isBlank()) add("Name is required")
        if (!name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            add("Name must contain only alphanumeric characters, underscores, and hyphens")
        }
        val duplicateFragments = fragments.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicateFragments.isNotEmpty()) {
            add("Duplicate fragments found: ${duplicateFragments.keys.joinToString()}")
        }
    }
}

@Serializable
data class LoadoutMetadata(
    val createdAt: Long = currentTimeMillis(),
    val updatedAt: Long = currentTimeMillis(),
    val version: String = "1.0.0",
    val tags: List<String> = emptyList()
) {
    fun withUpdatedTimestamp(): LoadoutMetadata = copy(updatedAt = currentTimeMillis())
}