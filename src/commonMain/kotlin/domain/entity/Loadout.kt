package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class Loadout(
    val name: String,
    val description: String = "",
    val fragments: List<String> = emptyList(),
    val metadata: LoadoutMetadata,
) {
    fun addFragment(
        fragmentPath: String,
        afterFragment: String? = null,
        currentTimeMillis: Long,
    ): Loadout {
        val updatedFragments =
            if (afterFragment != null) {
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
            metadata = metadata.withUpdatedTimestamp(currentTimeMillis),
        )
    }

    fun removeFragment(
        fragmentPath: String,
        currentTimeMillis: Long,
    ): Loadout =
        copy(
            fragments = fragments - fragmentPath,
            metadata = metadata.withUpdatedTimestamp(currentTimeMillis),
        )

    fun moveFragment(
        fragmentPath: String,
        afterFragment: String?,
        currentTimeMillis: Long,
    ): Loadout {
        val withoutFragment = removeFragment(fragmentPath, currentTimeMillis)
        return withoutFragment.addFragment(fragmentPath, afterFragment, currentTimeMillis)
    }

    fun validate(): List<String> =
        buildList {
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
