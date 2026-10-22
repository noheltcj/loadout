package domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

/** Lets Loadout tell its own unmodified output apart from a user's edits. */
@Serializable
data class OutputFilesWrite(
    val composedContentHash: String,
    /** Shared by every output file, since each receives the same bytes. */
    val writtenContentDigest: String,
    val outputFiles: Set<String>,
)
