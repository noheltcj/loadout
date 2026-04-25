package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.Loadout
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

sealed interface UseLoadoutResult {
    val loadout: Loadout
    val composedOutput: ComposedOutput

    data class PrintedToStandardOutput(
        override val loadout: Loadout,
        override val composedOutput: ComposedOutput,
    ) : UseLoadoutResult

    data class Activated(
        override val loadout: Loadout,
        override val composedOutput: ComposedOutput,
        val writeResult: WriteComposedFilesResult,
    ) : UseLoadoutResult
}

class UseLoadoutUseCase(
    private val getLoadout: GetLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
    private val activateComposedLoadout: ActivateComposedLoadoutUseCase,
) {
    operator fun invoke(
        loadoutName: String,
        outputTarget: LoadoutOutputTarget,
    ): Result<UseLoadoutResult, LoadoutError> =
        getLoadout(loadoutName)
            .flatMap { loadout ->
                composeLoadout(loadout).flatMap { composedOutput ->
                    when (outputTarget) {
                        LoadoutOutputTarget.StandardOutput ->
                            Result.Success(UseLoadoutResult.PrintedToStandardOutput(loadout, composedOutput))

                        is LoadoutOutputTarget.FileSystem ->
                            activateComposedLoadout(
                                composedOutput = composedOutput,
                                outputPaths = outputTarget.outputPaths,
                            ).map { writeResult ->
                                UseLoadoutResult.Activated(
                                    loadout = loadout,
                                    composedOutput = composedOutput,
                                    writeResult = writeResult,
                                )
                            }
                    }
                }
            }
}
