package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class RepositorySettings(
    val defaultLoadoutName: String? = null,
    val fragmentsHome: String = DEFAULT_FRAGMENTS_HOME,
    val trustedFragmentSourceHosts: List<String> = emptyList(),
    val maximumFragmentCopyBytes: Int = DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES,
    /** Null defers to the built-in default. */
    val outputFiles: List<String>? = null,
) {
    companion object {
        const val DEFAULT_FRAGMENTS_HOME = "fragments"
        const val DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES = 200_000
    }
}
