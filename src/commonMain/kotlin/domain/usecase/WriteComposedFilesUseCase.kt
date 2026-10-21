package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.repository.LocalLoadoutStateRepository

class WriteComposedFilesUseCase(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val localLoadoutStateRepository: LocalLoadoutStateRepository,
) {
    /**
     * Writes the composed output to the specified file paths.
     *
     * @param composedOutput The composed content to write
     * @param outputPaths List of file paths to write the content to
     * @return Result indicating whether files were written or already up to date
     */
    operator fun invoke(
        composedOutput: ComposedOutput,
        outputPaths: List<String>,
    ): Result<WriteComposedFilesResult, LoadoutError> =
        localLoadoutStateRepository.loadLocalState().flatMap { state ->
            val currentHash = composedOutput.metadata.contentHash
            val storedHash = state.lastComposedContentHash

            val allFilesExist = outputPaths.all { fileQueryRepository.fileExists(it) }

            if (currentHash == storedHash && allFilesExist) {
                Result.Success(WriteComposedFilesResult.AlreadyUpToDate)
            } else {
                writeToAllPaths(composedOutput, outputPaths)
            }
        }

    private fun writeToAllPaths(
        composedOutput: ComposedOutput,
        outputPaths: List<String>,
    ): Result<WriteComposedFilesResult, LoadoutError> {
        val content = composedOutput.toFileContent(includeMetadata = true)

        // Write to all output paths sequentially
        for (path in outputPaths) {
            when (val result = fileMutationRepository.writeFile(path, content)) {
                is Result.Error -> return result
                is Result.Success -> { /* Continue to next file */ }
            }
        }

        return Result.Success(WriteComposedFilesResult.Overwritten)
    }
}
