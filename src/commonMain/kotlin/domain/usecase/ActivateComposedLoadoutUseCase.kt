package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.LocalLoadoutState
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.LocalLoadoutStateRepository

class ActivateComposedLoadoutUseCase(
    private val writeComposedFiles: WriteComposedFilesUseCase,
    private val localLoadoutStateRepository: LocalLoadoutStateRepository,
) {
    operator fun invoke(
        composedOutput: ComposedOutput,
        outputPaths: List<String>,
    ): Result<WriteComposedFilesResult, LoadoutError> =
        writeComposedFiles(composedOutput, outputPaths)
            .map { composedOutput to it }
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
