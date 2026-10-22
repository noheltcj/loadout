package domain.entity

sealed interface WriteComposedFilesResult {
    data object AlreadyUpToDate : WriteComposedFilesResult

    data class Written(
        val writtenFiles: List<String>,
        val backups: List<OutputFileBackup>,
        val removedFiles: List<RemovedOutputFile>,
        /** Kept on disk because they changed after Loadout wrote them, and left unmanaged and unignored. */
        val releasedFiles: List<String>,
    ) : WriteComposedFilesResult
}

/**
 * An output file Loadout removed because it is no longer configured.
 *
 * [trackedByGit] is true when git's index still records the path, so git status reports the removal.
 */
data class RemovedOutputFile(
    val path: String,
    val trackedByGit: Boolean,
)

data class OutputFileBackup(
    val originalPath: String,
    val backupPath: String,
    val original: ReplacedFile,
    val reason: OutputFileBackupReason,
)

sealed interface OutputFileBackupReason {
    data class HandWrittenFile(
        /** Never an existing file. */
        val suggestedFragmentPath: String,
    ) : OutputFileBackupReason

    data object EditedOutput : OutputFileBackupReason
}

sealed interface ReplacedFile {
    data object RegularFile : ReplacedFile

    /** [target] is worktree-relative when it is inside the worktree, and absolute otherwise. */
    data class SymbolicLink(
        val target: String,
    ) : ReplacedFile
}
