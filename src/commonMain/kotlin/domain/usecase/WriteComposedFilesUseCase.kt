package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.ConfigRepository
import domain.repository.FileRepository

class WriteComposedFilesUseCase(
    private val fileRepository: FileRepository,
    private val configRepository: ConfigRepository,
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
        configRepository.loadConfig().flatMap { config ->
            val currentHash = composedOutput.metadata.contentHash
            val storedHash = config.compositionHash

            if (currentHash == storedHash) {
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
            when (val result = fileRepository.writeFile(path, content)) {
                is Result.Error -> return result
                is Result.Success -> { /* Continue to next file */ }
            }
        }

        return Result.Success(WriteComposedFilesResult.Overwritten)
    }
}
