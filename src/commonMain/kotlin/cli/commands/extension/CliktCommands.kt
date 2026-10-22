package cli.commands.extension

import com.github.ajalt.clikt.core.CliktCommand
import domain.entity.OutputFileBackup
import domain.entity.OutputFileBackupReason
import domain.entity.RemovedOutputFile
import domain.entity.ReplacedFile
import domain.entity.RepositorySettings
import domain.entity.UserSettingsLocation
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.policy.RepositorySettingsRepairWarning
import domain.policy.UserSettingsRepairWarning
import domain.usecase.ConfigureGitIgnoreRulesResult
import domain.usecase.LoadoutActivation

private data class FragmentMove(
    val backupPath: String,
    val fragmentPath: String,
)

fun CliktCommand.echoFilesGenerated(
    writtenFiles: List<String>,
    composedContentLength: Int,
) {
    echo("Generated files ($composedContentLength characters):")
    writtenFiles.forEach { path ->
        echo("  • $path")
    }
}

// Warnings go to stderr so they survive the auto-sync hooks, which discard stdout.
fun CliktCommand.echoComposedFilesWriteResult(
    result: WriteComposedFilesResult,
    loadoutName: String,
    composedContentLength: Int,
) {
    when (result) {
        is WriteComposedFilesResult.Written -> {
            echoFilesGenerated(result.writtenFiles, composedContentLength)
            echoRemovedFiles(result.removedFiles)
            echoReleasedFiles(result.releasedFiles)
            echoOutputFileBackups(result.backups, loadoutName)
        }
        WriteComposedFilesResult.AlreadyUpToDate -> {
            echo("Loadout `$loadoutName` is active and up to date. Nothing to do.")
        }
    }
}

private fun CliktCommand.echoRemovedFiles(removedFiles: List<RemovedOutputFile>) {
    if (removedFiles.isEmpty()) return

    echo("Removed output files that are no longer configured:")
    removedFiles.forEach { removed -> echo("  • ${removed.path}") }
    echoTrackedRemovalsNote(removedFiles.filter { removed -> removed.trackedByGit })
}

private fun CliktCommand.echoTrackedRemovalsNote(removedFiles: List<RemovedOutputFile>) {
    if (removedFiles.isEmpty()) return
    val paths = removedFiles.map(RemovedOutputFile::path)

    // Warnings go to stderr so they survive the auto-sync hooks, which discard stdout.
    when {
        paths.size == 1 -> {
            echo("Warning: ${paths.single()} is tracked by git, and git status now reports the deletion.", err = true)
            echo("Run `git rm ${paths.single()}` to remove it from the repository.", err = true)
        }

        else -> {
            echo(
                "Warning: ${paths.joinWithConjunction()} are tracked by git, " +
                    "and git status now reports the deletions.",
                err = true,
            )
            echo("Run `git rm ${paths.joinToString(" ")}` to remove them from the repository.", err = true)
        }
    }
}

private fun List<String>.joinWithConjunction(): String =
    when {
        isEmpty() -> ""
        size == 1 -> single()
        else -> dropLast(1).joinToString(", ") + " and " + last()
    }

private fun CliktCommand.echoReleasedFiles(releasedFiles: List<String>) {
    if (releasedFiles.isEmpty()) return

    releasedFiles.forEach { path ->
        echo("Warning: Kept $path because it changed after Loadout generated it.", err = true)
    }
    if (releasedFiles.size == 1) {
        echo("Loadout no longer manages or ignores it. Delete it, or commit it as your own file.", err = true)
    } else {
        echo("Loadout no longer manages or ignores them. Delete them, or commit them as your own files.", err = true)
    }
}

private fun CliktCommand.echoOutputFileBackups(
    backups: List<OutputFileBackup>,
    loadoutName: String,
) {
    backups.forEach { backup -> echoOutputFileBackup(backup) }

    if (backups.any { backup -> backup.reason == OutputFileBackupReason.EditedOutput }) {
        echo("Put lasting guidance in a fragment.", err = true)
    }

    val fragmentMoves =
        backups.mapNotNull { backup ->
            (backup.reason as? OutputFileBackupReason.HandWrittenFile)?.let { reason ->
                FragmentMove(backup.backupPath, reason.suggestedFragmentPath)
            }
        }
    if (fragmentMoves.isNotEmpty()) {
        echoMoveIntoFragmentsCommands(fragmentMoves, loadoutName)
    }
}

private fun CliktCommand.echoOutputFileBackup(backup: OutputFileBackup) {
    when (backup.reason) {
        is OutputFileBackupReason.HandWrittenFile ->
            echo(
                "Warning: Backed up ${backup.originalPath} to ${backup.backupPath} before replacing it.",
                err = true,
            )

        OutputFileBackupReason.EditedOutput ->
            echo(
                "Warning: Backed up your edits to ${backup.originalPath} to ${backup.backupPath} and regenerated it.",
                err = true,
            )
    }

    when (val original = backup.original) {
        ReplacedFile.RegularFile -> Unit
        is ReplacedFile.SymbolicLink ->
            echo(
                "${backup.originalPath} was a symbolic link. Loadout replaced the link with a regular file " +
                    "and left its target, ${original.target}, unchanged.",
                err = true,
            )
    }
}

private fun CliktCommand.echoMoveIntoFragmentsCommands(
    fragmentMoves: List<FragmentMove>,
    loadoutName: String,
) {
    val fragmentsDirectories = fragmentMoves.map { move -> move.fragmentPath.substringBeforeLast('/') }.distinct()

    if (fragmentMoves.size == 1) {
        echo("To keep its guidance, move it into a fragment and link it:", err = true)
    } else {
        echo("To keep their guidance, move them into fragments and link them:", err = true)
    }
    echo("  mkdir -p ${fragmentsDirectories.joinToString(" ")}", err = true)
    fragmentMoves.forEach { move -> echo("  mv ${move.backupPath} ${move.fragmentPath}", err = true) }
    fragmentMoves.forEach { move -> echo("  loadout fragment link ${move.fragmentPath} --to $loadoutName", err = true) }
    echo("  loadout sync", err = true)
}

fun CliktCommand.echoRepositorySettingsWarnings(warnings: List<RepositorySettingsRepairWarning>) {
    warnings.forEach { warning -> echo("Warning: ${warning.describe()}", err = true) }
}

private fun RepositorySettingsRepairWarning.describe(): String =
    when (this) {
        is RepositorySettingsRepairWarning.UnreadableSettingsReplaced ->
            "Repository settings could not be read; safe defaults are in use. ${error.message}"
        is RepositorySettingsRepairWarning.InvalidFragmentsHomeReplaced ->
            "Invalid fragments home '$invalidFragmentsHome' was replaced with " +
                "'${RepositorySettings.DEFAULT_FRAGMENTS_HOME}'."
        is RepositorySettingsRepairWarning.InvalidTrustedHostRemoved ->
            "Invalid trusted fragment host '$invalidHost' was removed."
        is RepositorySettingsRepairWarning.InvalidCopyLimitReplaced ->
            "Invalid fragment copy limit $invalidMaximumBytes was replaced with " +
                "${RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES} bytes."
        is RepositorySettingsRepairWarning.InvalidOutputFileRemoved ->
            "Invalid output file '$invalidOutputFile' was removed: ${error.validationMessage}."
    }

fun CliktCommand.echoUserSettingsWarnings(warnings: List<UserSettingsRepairWarning>) {
    warnings.forEach { warning -> echo("Warning: ${warning.describe()}", err = true) }
}

private fun UserSettingsRepairWarning.describe(): String =
    when (this) {
        is UserSettingsRepairWarning.UnreadableSettingsIgnored ->
            "Could not read the user settings in ${location.displayPath}, so Loadout ignored them.${error.causeDetail()}"
        is UserSettingsRepairWarning.UnreadableSettingsReplaced ->
            "Could not read the user settings in ${location.displayPath}, so Loadout replaced them.${error.causeDetail()}"
        is UserSettingsRepairWarning.InvalidOutputFileIgnored ->
            "Ignored output file '$invalidOutputFile' from your user settings: ${error.validationMessage}."
    }

private fun LoadoutError.UnreadableUserSettings.causeDetail(): String =
    cause?.message?.let { message -> " $message" }.orEmpty()

val UserSettingsLocation.displayPath: String
    get() = pathInHome?.let { relativePath -> "~/$relativePath" } ?: path

fun CliktCommand.echoLoadoutActivation(
    activation: LoadoutActivation,
    loadoutName: String,
    composedContentLength: Int,
) {
    echoRepositorySettingsWarnings(activation.repositorySettingsWarnings)
    echoUserSettingsWarnings(activation.outputFiles.userSettingsWarnings)
    echoComposedFilesWriteResult(activation.writeResult, loadoutName, composedContentLength)
    echoGitIgnoreRulesWarning(activation.ignoreRules)
}

fun CliktCommand.echoGitIgnoreRulesWarning(result: ConfigureGitIgnoreRulesResult) {
    when (result) {
        is ConfigureGitIgnoreRulesResult.Failed ->
            echo("Warning: Could not update git ignore rules: ${result.error.message}", err = true)

        ConfigureGitIgnoreRulesResult.NotAGitRepository,
        is ConfigureGitIgnoreRulesResult.Configured,
        is ConfigureGitIgnoreRulesResult.AlreadyConfigured,
        -> Unit
    }
}
