package domain.usecase

import domain.entity.ReplacedFile
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.GENERATED_OUTPUT_HEADER_PREFIX_BYTES
import domain.policy.hasGeneratedOutputHeader
import domain.policy.parentDirectories
import domain.repository.FileDigestRepository
import domain.repository.FileQueryRepository

private const val WORKTREE_ROOT = "."

sealed interface OutputPathOccupant {
    val path: String

    /** Also covers a symbolic link to nothing. */
    data class Vacant(
        override val path: String,
    ) : OutputPathOccupant

    data class UnmodifiedOutput(
        override val path: String,
    ) : OutputPathOccupant

    data class SymbolicLinkToUnmodifiedOutput(
        override val path: String,
    ) : OutputPathOccupant

    data class UserContent(
        override val path: String,
        val original: ReplacedFile,
        val origin: UserContentOrigin,
    ) : OutputPathOccupant

    data class NotAFile(
        override val path: String,
    ) : OutputPathOccupant

    data class InsideSymbolicLink(
        override val path: String,
        val symbolicLinkPath: String,
    ) : OutputPathOccupant
}

enum class UserContentOrigin {
    HandWritten,

    EditedOutput,
}

/**
 * Content is Loadout's unmodified output when it opens with Loadout's header and, if Loadout has a record of writing
 * the path, its bytes still match that record. A symbolic link is judged by the content it points to.
 */
class InspectOutputPathUseCase(
    private val fileQueryRepository: FileQueryRepository,
    private val fileDigestRepository: FileDigestRepository,
) {
    operator fun invoke(
        path: String,
        recordedDigest: String?,
    ): Result<OutputPathOccupant, LoadoutError> {
        val symbolicLinkPath = parentDirectories(path).firstOrNull(fileQueryRepository::isSymbolicLink)

        return when {
            symbolicLinkPath != null ->
                Result.Success(OutputPathOccupant.InsideSymbolicLink(path, symbolicLinkPath))

            !fileQueryRepository.fileExists(path) ->
                Result.Success(OutputPathOccupant.Vacant(path))

            !fileQueryRepository.isRegularFile(path) ->
                Result.Success(OutputPathOccupant.NotAFile(path))

            fileQueryRepository.isSymbolicLink(path) ->
                symbolicLinkTarget(path).flatMap { target ->
                    inspectContent(
                        path = path,
                        recordedDigest = recordedDigest,
                        original = ReplacedFile.SymbolicLink(target),
                        unmodifiedOutput = OutputPathOccupant.SymbolicLinkToUnmodifiedOutput(path),
                    )
                }

            else ->
                inspectContent(
                    path = path,
                    recordedDigest = recordedDigest,
                    original = ReplacedFile.RegularFile,
                    unmodifiedOutput = OutputPathOccupant.UnmodifiedOutput(path),
                )
        }
    }

    private fun inspectContent(
        path: String,
        recordedDigest: String?,
        original: ReplacedFile,
        unmodifiedOutput: OutputPathOccupant,
    ): Result<OutputPathOccupant, LoadoutError> =
        opensWithGeneratedOutputHeader(path).flatMap { hasHeader ->
            when {
                !hasHeader ->
                    Result.Success(OutputPathOccupant.UserContent(path, original, UserContentOrigin.HandWritten))

                recordedDigest == null ->
                    Result.Success(unmodifiedOutput)

                else ->
                    fileDigestRepository.digestFile(path).map { digest ->
                        if (digest == recordedDigest) {
                            unmodifiedOutput
                        } else {
                            OutputPathOccupant.UserContent(path, original, UserContentOrigin.EditedOutput)
                        }
                    }
            }
        }

    private fun opensWithGeneratedOutputHeader(path: String): Result<Boolean, LoadoutError> =
        fileQueryRepository
            .readFileBytes(path, GENERATED_OUTPUT_HEADER_PREFIX_BYTES)
            .map { leadingBytes -> hasGeneratedOutputHeader(leadingBytes.decodeToString()) }

    private fun symbolicLinkTarget(path: String): Result<String, LoadoutError> =
        fileQueryRepository.canonicalPath(path).flatMap { target ->
            fileQueryRepository.canonicalPath(WORKTREE_ROOT).map { worktreeRoot ->
                if (target.startsWith("$worktreeRoot/")) target.removePrefix("$worktreeRoot/") else target
            }
        }
}
