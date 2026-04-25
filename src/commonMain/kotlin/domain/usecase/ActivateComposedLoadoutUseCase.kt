package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.LocalLoadoutState
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.LocalLoadoutStateRepository

data class ActivateComposedLoadoutInput(
    val composedOutput: ComposedOutput,
    val outputPaths: List<String>,
)

class ActivateComposedLoadoutUseCase(
    private val writeComposedFiles: WriteComposedFilesUseCase,
    private val localLoadoutStateRepository: LocalLoadoutStateRepository,
) {
    operator fun invoke(input: ActivateComposedLoadoutInput): Result<WriteComposedFilesResult, LoadoutError> =
        writeComposedFiles(input.composedOutput, input.outputPaths)
            .map { input.composedOutput to it }
            .flatMap { (composedOutput, writeResult) ->
                when (writeResult) {
                    WriteComposedFilesResult.Overwritten -> {
                        localLoadoutStateRepository.saveLocalState(
                            LocalLoadoutState(
                                activeLoadoutName = composedOutput.loadoutName,
                                lastComposedContentHash = composedOutput.metadata.contentHash,
                            )
                        )
                    }

                    WriteComposedFilesResult.AlreadyUpToDate -> {
                        localLoadoutStateRepository
                            .loadLocalState()
                            .flatMap { state ->
                                if (state.activeLoadoutName == composedOutput.loadoutName) {
                                    Result.Success(Unit)
                                } else {
                                    localLoadoutStateRepository.saveLocalState(
                                        LocalLoadoutState(
                                            activeLoadoutName = composedOutput.loadoutName,
                                            lastComposedContentHash = composedOutput.metadata.contentHash,
                                        )
                                    )
                                }
                            }
                    }
                }.map { writeResult }
            }
}
