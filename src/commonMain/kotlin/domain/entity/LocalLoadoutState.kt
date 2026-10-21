package domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What this worktree last activated and wrote. It is runtime state that the next write replaces. */
@Serializable
sealed interface LocalLoadoutState {
    @Serializable
    @SerialName("inactive")
    data object Inactive : LocalLoadoutState

    @Serializable
    @SerialName("active")
    data class Active(
        val loadoutName: String,
        val lastWrite: OutputFilesWrite,
    ) : LocalLoadoutState
}

/** Loadout's record of its last write, which tells its own unmodified output apart from a user's edits. */
@Serializable
data class OutputFilesWrite(
    val composedContentHash: String,
    /** A digest of the exact bytes on disk, which every output file received alike. */
    val writtenContentDigest: String,
    val outputFiles: Set<String>,
)
