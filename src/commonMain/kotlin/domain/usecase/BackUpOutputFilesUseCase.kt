package domain.usecase

import domain.entity.LoadoutFileLocations
import domain.entity.OutputFileBackup
import domain.entity.OutputFileBackupReason
import domain.entity.ReplacedFile
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.MARKDOWN_EXTENSION
import domain.policy.formatBackupTimestamp
import domain.policy.parentDirectories
import domain.repository.EnvironmentRepository
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository

private const val BACKUP_SUFFIX = ".backup"

data class BackUpOutputFileInput(
    val originalPath: String,
    val original: ReplacedFile,
    val origin: UserContentOrigin,
)

/** The `.backup` suffix keeps agents that discover instruction files in subdirectories from reading backups. */
class BackUpOutputFilesUseCase(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val environmentRepository: EnvironmentRepository,
    private val getRepositorySettings: GetRepairableRepositorySettingsUseCase,
    private val loadoutFileLocations: LoadoutFileLocations,
) {
    operator fun invoke(files: List<BackUpOutputFileInput>): Result<List<OutputFileBackup>, LoadoutError> =
        if (files.isEmpty()) {
            Result.Success(emptyList())
        } else {
            getRepositorySettings().flatMap { repair ->
                backUpAll(files, unoccupiedBackupDirectory(), repair.settings.fragmentsHome)
            }
        }

    private fun backUpAll(
        files: List<BackUpOutputFileInput>,
        backupDirectory: String,
        fragmentsHome: String,
    ): Result<List<OutputFileBackup>, LoadoutError> {
        val initialResult: Result<List<OutputFileBackup>, LoadoutError> = Result.Success(emptyList())

        return files.fold(initialResult) { result, file ->
            result.flatMap { backups ->
                backUp(
                    file = file,
                    backupDirectory = backupDirectory,
                    fragmentsHome = fragmentsHome,
                    reservedFragmentPaths = backups.mapNotNull(OutputFileBackup::suggestedFragmentPath).toSet(),
                ).map { backup -> backups + backup }
            }
        }
    }

    private fun backUp(
        file: BackUpOutputFileInput,
        backupDirectory: String,
        fragmentsHome: String,
        reservedFragmentPaths: Set<String>,
    ): Result<OutputFileBackup, LoadoutError> {
        val backupPath = "$backupDirectory/${file.originalPath}$BACKUP_SUFFIX"

        return refuseSymbolicLinks(backupPath)
            .flatMap { fileMutationRepository.createDirectories(backupPath.substringBeforeLast('/')) }
            .flatMap { fileMutationRepository.copyFile(file.originalPath, backupPath) }
            .mapError { error ->
                LoadoutError.FileSystemError(
                    "Could not back up ${file.originalPath}, so no output files were written: ${error.message}",
                    error.cause,
                )
            }
            .map {
                OutputFileBackup(
                    originalPath = file.originalPath,
                    backupPath = backupPath,
                    original = file.original,
                    reason = backupReason(file, fragmentsHome, reservedFragmentPaths),
                )
            }
    }

    // Copying through a symbolic link would write wherever it points, which may be outside the repository.
    private fun refuseSymbolicLinks(backupPath: String): Result<Unit, LoadoutError> =
        (parentDirectories(backupPath) + backupPath)
            .firstOrNull(fileQueryRepository::isSymbolicLink)
            ?.let { symbolicLinkPath ->
                Result.Error(
                    LoadoutError.FileSystemError(
                        "$symbolicLinkPath is a symbolic link, and Loadout never writes through one"
                    )
                )
            }
            ?: Result.Success(Unit)

    private fun backupReason(
        file: BackUpOutputFileInput,
        fragmentsHome: String,
        reservedFragmentPaths: Set<String>,
    ): OutputFileBackupReason =
        when (file.origin) {
            UserContentOrigin.HandWritten ->
                OutputFileBackupReason.HandWrittenFile(
                    suggestedFragmentPath = unoccupiedFragmentPath(
                        fragmentsHome,
                        file.originalPath,
                        reservedFragmentPaths
                    ),
                )

            UserContentOrigin.EditedOutput -> OutputFileBackupReason.EditedOutput
        }

    private fun unoccupiedBackupDirectory(): String {
        val timestampedDirectory =
            "${loadoutFileLocations.backupsDirectory}/${formatBackupTimestamp(
                environmentRepository.currentTimeMillis()
            )}"

        return numberedCandidates(timestampedDirectory) { number -> "$timestampedDirectory-$number" }
            .first { directory -> !fileQueryRepository.fileExists(directory) }
    }

    private fun unoccupiedFragmentPath(
        fragmentsHome: String,
        originalPath: String,
        reservedFragmentPaths: Set<String>,
    ): String {
        val stem = originalPath.substringAfterLast('/').lowercase().removeSuffix(MARKDOWN_EXTENSION)

        return numberedCandidates("$fragmentsHome/$stem$MARKDOWN_EXTENSION") { number ->
            "$fragmentsHome/$stem-$number$MARKDOWN_EXTENSION"
        }.first { fragmentPath ->
            fragmentPath !in reservedFragmentPaths && !fileQueryRepository.fileExists(fragmentPath)
        }
    }
}

private val OutputFileBackup.suggestedFragmentPath: String?
    get() =
        when (val reason = reason) {
            is OutputFileBackupReason.HandWrittenFile -> reason.suggestedFragmentPath
            OutputFileBackupReason.EditedOutput -> null
        }

/** Yields [first], then `numbered(2)`, `numbered(3)`, and so on. */
private fun numberedCandidates(
    first: String,
    numbered: (number: Int) -> String,
): Sequence<String> = sequenceOf(first) + generateSequence(2) { number -> number + 1 }.map(numbered)
