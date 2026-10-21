package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.LocalLoadoutState
import domain.entity.OutputFileBackup
import domain.entity.OutputFilesWrite
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.entity.packaging.mapResults
import domain.policy.OutputFilePath
import domain.repository.FileDigestRepository
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.repository.LocalLoadoutStateRepository

class WriteComposedFilesUseCase(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val fileDigestRepository: FileDigestRepository,
    private val getLocalLoadoutState: GetLocalLoadoutStateUseCase,
    private val localLoadoutStateRepository: LocalLoadoutStateRepository,
    private val inspectOutputPath: InspectOutputPathUseCase,
    private val backUpOutputFiles: BackUpOutputFilesUseCase,
    private val pruneOutputFiles: PruneOutputFilesUseCase,
) {
    /**
     * Every backup is made before any output is written, so a failed backup leaves every output untouched. The outputs
     * are up to date when the composition and the set of output files match the last write and every file still holds
     * exactly what Loadout wrote.
     */
    operator fun invoke(
        composedOutput: ComposedOutput,
        outputFiles: List<OutputFilePath>,
    ): Result<WriteComposedFilesResult, LoadoutError> {
        val state = getLocalLoadoutState().state
        val lastWrite = state.lastWriteOrNull

        return outputFiles
            .mapResults { outputFile ->
                inspectOutputPath(outputFile.value, lastWrite?.digestRecordedFor(outputFile.value))
            }
            .flatMap { occupants ->
                if (state is LocalLoadoutState.Active && state.lastWrite.isCurrent(composedOutput, occupants)) {
                    recordLoadoutName(state, composedOutput.loadoutName)
                        .map { WriteComposedFilesResult.AlreadyUpToDate }
                } else {
                    replaceAll(composedOutput, occupants, lastWrite)
                }
            }
    }

    private fun replaceAll(
        composedOutput: ComposedOutput,
        occupants: List<OutputPathOccupant>,
        lastWrite: OutputFilesWrite?,
    ): Result<WriteComposedFilesResult, LoadoutError> =
        occupants
            .mapResults(::requireReplaceable)
            .flatMap { backUpUserContent(occupants) }
            .flatMap { backups ->
                val content = composedOutput.toFileContent(includeMetadata = true)

                // The record is saved before pruning, so it describes the files on disk even if pruning fails.
                occupants
                    .mapResults { occupant -> writeOutputFile(occupant.path, content) }
                    .flatMap { writtenFiles -> recordWrite(composedOutput, writtenFiles).map { writtenFiles } }
                    .flatMap { writtenFiles ->
                        pruneLastWrite(lastWrite, writtenFiles).map { pruned ->
                            WriteComposedFilesResult.Written(
                                writtenFiles = writtenFiles,
                                backups = backups,
                                removedFiles = pruned.removedFiles,
                                releasedFiles = pruned.releasedFiles,
                            )
                        }
                    }
            }

    private fun requireReplaceable(occupant: OutputPathOccupant): Result<Unit, LoadoutError> =
        when (occupant) {
            is OutputPathOccupant.NotAFile ->
                Result.Error(
                    LoadoutError.FileSystemError("Cannot replace ${occupant.path} because it is not a regular file")
                )

            is OutputPathOccupant.InsideSymbolicLink ->
                Result.Error(
                    LoadoutError.FileSystemError(
                        "Cannot write ${occupant.path} because ${occupant.symbolicLinkPath} is a symbolic link, " +
                            "and Loadout never writes through one"
                    )
                )

            is OutputPathOccupant.Vacant,
            is OutputPathOccupant.UnmodifiedOutput,
            is OutputPathOccupant.SymbolicLinkToUnmodifiedOutput,
            is OutputPathOccupant.UserContent,
            -> Result.Success(Unit)
        }

    private fun backUpUserContent(occupants: List<OutputPathOccupant>): Result<List<OutputFileBackup>, LoadoutError> =
        backUpOutputFiles(
            occupants
                .filterIsInstance<OutputPathOccupant.UserContent>()
                .map { userContent ->
                    BackUpOutputFileInput(userContent.path, userContent.original, userContent.origin)
                }
        )

    // Writing through a symbolic link would change the file it points to, so the link itself is replaced.
    private fun writeOutputFile(
        path: String,
        content: String,
    ): Result<String, LoadoutError> {
        val linkRemoval =
            if (fileQueryRepository.isSymbolicLink(path)) {
                fileMutationRepository.deleteFile(path)
            } else {
                Result.Success(Unit)
            }
        val parentDirectory = path.substringBeforeLast('/', missingDelimiterValue = "")

        return linkRemoval
            .flatMap {
                if (parentDirectory.isEmpty()) {
                    Result.Success(Unit)
                } else {
                    fileMutationRepository.createDirectories(parentDirectory)
                }
            }
            .flatMap { fileMutationRepository.writeFile(path, content) }
            .map { path }
    }

    // Every output file receives the same content, so one file's bytes stand for all of them. Reading them back
    // records exactly what is on disk, whatever line endings the platform wrote.
    private fun recordWrite(
        composedOutput: ComposedOutput,
        writtenFiles: List<String>,
    ): Result<Unit, LoadoutError> =
        fileDigestRepository.digestFile(writtenFiles.first()).flatMap { digest ->
            localLoadoutStateRepository.saveLocalState(
                LocalLoadoutState.Active(
                    loadoutName = composedOutput.loadoutName,
                    lastWrite =
                    OutputFilesWrite(
                        composedContentHash = composedOutput.metadata.contentHash,
                        writtenContentDigest = digest,
                        outputFiles = writtenFiles.toSet(),
                    ),
                )
            )
        }

    private fun pruneLastWrite(
        lastWrite: OutputFilesWrite?,
        writtenFiles: List<String>,
    ): Result<PrunedOutputFiles, LoadoutError> =
        if (lastWrite == null) {
            Result.Success(PrunedOutputFiles(removedFiles = emptyList(), releasedFiles = emptyList()))
        } else {
            pruneOutputFiles(lastWrite, writtenFiles)
        }

    // Two loadouts can compose the same content, so switching between them only records the new name.
    private fun recordLoadoutName(
        state: LocalLoadoutState.Active,
        loadoutName: String,
    ): Result<Unit, LoadoutError> =
        if (state.loadoutName == loadoutName) {
            Result.Success(Unit)
        } else {
            localLoadoutStateRepository.saveLocalState(state.copy(loadoutName = loadoutName))
        }
}

private val LocalLoadoutState.lastWriteOrNull: OutputFilesWrite?
    get() =
        when (this) {
            LocalLoadoutState.Inactive -> null
            is LocalLoadoutState.Active -> lastWrite
        }

private fun OutputFilesWrite.digestRecordedFor(path: String): String? =
    writtenContentDigest.takeIf { path in outputFiles }

private fun OutputFilesWrite.isCurrent(
    composedOutput: ComposedOutput,
    occupants: List<OutputPathOccupant>,
): Boolean =
    composedContentHash == composedOutput.metadata.contentHash &&
        outputFiles == occupants.map(OutputPathOccupant::path).toSet() &&
        occupants.all { occupant -> occupant is OutputPathOccupant.UnmodifiedOutput }
