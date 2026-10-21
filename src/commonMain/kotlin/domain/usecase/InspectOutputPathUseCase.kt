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

/** What stands at an output path. */
sealed interface OutputPathOccupant {
    val path: String

    /** Nothing to replace: the path is free, or holds a symbolic link to nothing. */
    data class Vacant(
        override val path: String,
    ) : OutputPathOccupant

    data class UnmodifiedOutput(
        override val path: String,
    ) : OutputPathOccupant

    /** A link to Loadout's unmodified output: the link is replaced, but there is nothing of the user's to back up. */
    data class SymbolicLinkToUnmodifiedOutput(
        override val path: String,
    ) : OutputPathOccupant

    /** Content Loadout must not lose. */
    data class UserContent(
        override val path: String,
        val original: ReplacedFile,
        val origin: UserContentOrigin,
    ) : OutputPathOccupant

    /** A directory or another entry that is not a file. */
    data class NotAFile(
        override val path: String,
    ) : OutputPathOccupant

    /** A path inside a symbolically linked directory, which Loadout never writes through. */
    data class InsideSymbolicLink(
        override val path: String,
        val symbolicLinkPath: String,
    ) : OutputPathOccupant
}

enum class UserContentOrigin {
    /** The content has no Loadout header, so Loadout never wrote it. */
    HandWritten,

    /** The content is Loadout's output, edited after Loadout wrote it. */
    EditedOutput,
}

/**
 * Tells Loadout's unmodified output apart from content it must not lose.
 *
 * Content is Loadout's unmodified output when it opens with Loadout's header and, if Loadout has a record of writing
 * the path, its bytes still match that record. A symbolic link is judged by the content it points to.
 */
class InspectOutputPathUseCase(
    private val fileQueryRepository: FileQueryRepository,
    private val fileDigestRepository: FileDigestRepository,
) {
    /** [recordedDigest] is the digest of the bytes Loadout last wrote to [path], or null when it has no record. */
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
