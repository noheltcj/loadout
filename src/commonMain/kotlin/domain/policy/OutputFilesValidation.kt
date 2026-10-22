package domain.policy

import domain.entity.LoadoutFileLocations
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.entity.packaging.mapResults

private const val OUTPUT_FILES_FIELD = "output-files"
private const val GIT_DIRECTORY = ".git"

/** Leaves out the fragments home, which only each repository's settings know, so callers that have it add it. */
internal fun reservedOutputDirectories(loadoutFileLocations: LoadoutFileLocations): List<String> =
    listOf(
        GIT_DIRECTORY,
        loadoutFileLocations.hooksDirectory,
        loadoutFileLocations.loadoutsDirectory,
        loadoutFileLocations.backupsDirectory,
    )

/** Only types each entry: the list must already have passed a validating or repairing settings read. */
fun validateOutputFilePaths(outputFiles: List<String>): Result<List<OutputFilePath>, LoadoutError> =
    outputFiles.mapResults { outputFile -> validateOutputFilePath(outputFile, OUTPUT_FILES_FIELD) }

internal fun validateOutputFileList(
    outputFiles: List<String>?,
    reservedDirectories: List<String>,
): Result<List<String>?, LoadoutError> {
    if (outputFiles == null) return Result.Success(null)

    val repair = repairOutputFileList(outputFiles, reservedDirectories)

    return when {
        repair.rejections.isNotEmpty() -> Result.Error(repair.rejections.first().error)
        repair.outputFiles.isEmpty() ->
            Result.Error(LoadoutError.ValidationError(OUTPUT_FILES_FIELD, "List at least one output file"))
        else -> Result.Success(repair.outputFiles.map(OutputFilePath::value))
    }
}

internal data class OutputFilesRepair(
    val outputFiles: List<OutputFilePath>,
    val rejections: List<OutputFileRejection>,
)

internal data class OutputFileRejection(
    val outputFile: String,
    val error: LoadoutError.ValidationError,
)

/**
 * Entries are compared case-insensitively because macOS and Windows treat paths that differ only by case as one file,
 * so removing one of them would delete the other.
 */
internal fun repairOutputFileList(
    outputFiles: List<String>,
    reservedDirectories: List<String>,
): OutputFilesRepair =
    outputFiles.fold(OutputFilesRepair(outputFiles = emptyList(), rejections = emptyList())) { repair, outputFile ->
        validateOutputFile(outputFile, reservedDirectories).fold(
            onSuccess = { candidate -> repair.including(outputFile, candidate) },
            onError = { error -> repair.rejecting(outputFile, error) },
        )
    }

private fun validateOutputFile(
    outputFile: String,
    reservedDirectories: List<String>,
): Result<OutputFilePath, LoadoutError.ValidationError> =
    validateOutputFilePath(outputFile, OUTPUT_FILES_FIELD).flatMap { candidate ->
        reservedDirectories
            .firstOrNull { directory -> candidate.isInside(directory) }
            ?.let { directory ->
                Result.Error(
                    LoadoutError.ValidationError(
                        OUTPUT_FILES_FIELD,
                        "'$outputFile' is inside $directory/, where output files are not allowed",
                    )
                )
            }
            ?: Result.Success(candidate)
    }

private fun OutputFilePath.isInside(directory: String): Boolean =
    value.equals(directory, ignoreCase = true) || value.startsWith("$directory/", ignoreCase = true)

private fun OutputFilesRepair.including(
    outputFile: String,
    candidate: OutputFilePath,
): OutputFilesRepair {
    val sameFile = outputFiles.firstOrNull { accepted -> accepted.value.equals(candidate.value, ignoreCase = true) }

    return when (sameFile) {
        null -> copy(outputFiles = outputFiles + candidate)
        candidate -> this
        else ->
            rejecting(
                outputFile,
                LoadoutError.ValidationError(
                    OUTPUT_FILES_FIELD,
                    "'$outputFile' and '${sameFile.value}' differ only by case, so macOS and Windows treat them " +
                        "as one file",
                ),
            )
    }
}

private fun OutputFilesRepair.rejecting(
    outputFile: String,
    error: LoadoutError.ValidationError,
): OutputFilesRepair = copy(rejections = rejections + OutputFileRejection(outputFile, error))
