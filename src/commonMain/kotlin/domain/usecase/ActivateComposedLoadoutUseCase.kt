package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.LoadoutConfig
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.ConfigRepository

data class ActivateComposedLoadoutInput(
    val composedOutput: ComposedOutput,
    val outputPaths: List<String>,
)

class ActivateComposedLoadoutUseCase(
    private val writeComposedFiles: WriteComposedFilesUseCase,
    private val configRepository: ConfigRepository,
) {
    operator fun invoke(input: ActivateComposedLoadoutInput): Result<WriteComposedFilesResult, LoadoutError> =
        writeComposedFiles(input.composedOutput, input.outputPaths)
            .map { input.composedOutput to it }
            .flatMap { (composedOutput, writeResult) ->
                when (writeResult) {
                    WriteComposedFilesResult.Overwritten -> {
                        configRepository.saveConfig(
                            LoadoutConfig(
                                currentLoadoutName = composedOutput.loadoutName,
                                compositionHash = composedOutput.metadata.contentHash,
                            )
                        )
                    }

                    WriteComposedFilesResult.AlreadyUpToDate -> {
                        configRepository
                            .loadConfig()
                            .flatMap { config ->
                                if (config.currentLoadoutName == composedOutput.loadoutName) {
                                    Result.Success(Unit)
                                } else {
                                    configRepository.saveConfig(
                                        LoadoutConfig(
                                            currentLoadoutName = composedOutput.loadoutName,
                                            compositionHash = composedOutput.metadata.contentHash,
                                        )
                                    )
                                }
                            }
                    }
                }.map { writeResult }
            }
}
