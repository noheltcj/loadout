package domain.usecase

import domain.entity.OutputFilesWrite
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.entity.packaging.mapResults
import domain.repository.FileMutationRepository

data class PrunedOutputFiles(
    val removedFiles: List<String>,
    val releasedFiles: List<String>,
)

private sealed interface PruneOutcome {
    data object AlreadyGone : PruneOutcome

    data class Removed(
        val path: String,
    ) : PruneOutcome

    data class Released(
        val path: String,
    ) : PruneOutcome
}

/**
 * Cleans up the files of the last write that the current write no longer produced. A file that is still Loadout's
 * unmodified output is deleted. Anything else is kept and released: Loadout stops managing and ignoring it, and
 * destroys nothing.
 */
class PruneOutputFilesUseCase(
    private val inspectOutputPath: InspectOutputPathUseCase,
    private val fileMutationRepository: FileMutationRepository,
) {
    operator fun invoke(
        lastWrite: OutputFilesWrite,
        writtenFiles: List<String>,
    ): Result<PrunedOutputFiles, LoadoutError> =
        lastWrite.outputFiles
            // A path that differs from a written one only by case can be that same file on macOS and Windows.
            .filterNot { recordedPath ->
                writtenFiles.any { writtenFile -> writtenFile.equals(recordedPath, ignoreCase = true) }
            }
            .mapResults { recordedPath ->
                inspectOutputPath(recordedPath, lastWrite.writtenContentDigest).flatMap(::prune)
            }
            .map { outcomes ->
                PrunedOutputFiles(
                    removedFiles = outcomes.filterIsInstance<PruneOutcome.Removed>().map(PruneOutcome.Removed::path),
                    releasedFiles = outcomes.filterIsInstance<PruneOutcome.Released>().map(PruneOutcome.Released::path),
                )
            }

    private fun prune(occupant: OutputPathOccupant): Result<PruneOutcome, LoadoutError> =
        when (occupant) {
            is OutputPathOccupant.Vacant -> Result.Success(PruneOutcome.AlreadyGone)

            is OutputPathOccupant.UnmodifiedOutput ->
                fileMutationRepository.deleteFile(occupant.path).map { PruneOutcome.Removed(occupant.path) }

            is OutputPathOccupant.SymbolicLinkToUnmodifiedOutput,
            is OutputPathOccupant.UserContent,
            is OutputPathOccupant.NotAFile,
            is OutputPathOccupant.InsideSymbolicLink,
            -> Result.Success(PruneOutcome.Released(occupant.path))
        }
}
