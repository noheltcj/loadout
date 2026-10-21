package domain.entity

sealed interface WriteComposedFilesResult {
    data object AlreadyUpToDate : WriteComposedFilesResult

    data class Written(
        val writtenFiles: List<String>,
        val backups: List<OutputFileBackup>,
        /** Files of the last write that are no longer configured, deleted because nobody changed them. */
        val removedFiles: List<String>,
        /** Files of the last write that are no longer configured, kept because they changed. */
        val releasedFiles: List<String>,
    ) : WriteComposedFilesResult
}

/** Content Loadout copied aside before replacing it. */
data class OutputFileBackup(
    val originalPath: String,
    val backupPath: String,
    val original: ReplacedFile,
    val reason: OutputFileBackupReason,
)

/** Why the replaced content belonged to the user. */
sealed interface OutputFileBackupReason {
    /** The file had no Loadout header, so Loadout never wrote it. */
    data class HandWrittenFile(
        /** A fragment path the backup can move to without replacing an existing file. */
        val suggestedFragmentPath: String,
    ) : OutputFileBackupReason

    /** The file was Loadout's output, edited after Loadout wrote it. */
    data object EditedOutput : OutputFileBackupReason
}

/** What stood at an output path before Loadout replaced it. */
sealed interface ReplacedFile {
    data object RegularFile : ReplacedFile

    /**
     * Loadout backed up the content the link pointed to and replaced the link, never its [target]. The target is
     * worktree-relative when it is inside the worktree, and absolute otherwise.
     */
    data class SymbolicLink(
        val target: String,
    ) : ReplacedFile
}
