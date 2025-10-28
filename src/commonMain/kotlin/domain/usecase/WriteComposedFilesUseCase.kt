package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.ConfigRepository
import domain.repository.FileRepository

class WriteComposedFilesUseCase(
    private val fileRepository: FileRepository,
    private val configRepository: ConfigRepository
) {

    operator fun invoke(
        composedOutput: ComposedOutput,
        outputDir: String
    ): Result<WriteComposedFilesResult, LoadoutError> {
        return configRepository.loadConfig().flatMap { config ->
            val currentHash = composedOutput.metadata.contentHash
            val storedHash = config.compositionHash

            if (currentHash == storedHash) {
                Result.Success(WriteComposedFilesResult.AlreadyUpToDate)
            } else {
                val content = composedOutput.toFileContent(includeMetadata = true)
                val claudePath = "$outputDir/CLAUDE.md"
                val agentsPath = "$outputDir/AGENTS.md"

                fileRepository.writeFile(claudePath, content)
                    .flatMap { fileRepository.writeFile(agentsPath, content) }
                    .map { WriteComposedFilesResult.Overwritten }
            }
        }
    }
}
