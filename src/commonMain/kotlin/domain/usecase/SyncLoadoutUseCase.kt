package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.Loadout
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

data class SyncLoadoutInput(
    val outputTarget: LoadoutOutputTarget,
    val shouldFallbackToDefault: Boolean = false,
)

sealed interface SyncLoadoutResult {
    data object NoCurrentLoadout : SyncLoadoutResult

    data class PrintedToStandardOutput(
        val loadout: Loadout,
        val composedOutput: ComposedOutput,
    ) : SyncLoadoutResult

    data class Activated(
        val loadout: Loadout,
        val composedOutput: ComposedOutput,
        val writeResult: WriteComposedFilesResult,
    ) : SyncLoadoutResult
}

class SyncLoadoutUseCase(
    private val getTargetLoadoutForSync: GetTargetLoadoutForSyncUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
    private val activateComposedLoadout: ActivateComposedLoadoutUseCase,
) {
    operator fun invoke(input: SyncLoadoutInput): Result<SyncLoadoutResult, LoadoutError> =
        getTargetLoadoutForSync(input.shouldFallbackToDefault).flatMap { selection ->
            selection.fold(
                onNone = { Result.Success(SyncLoadoutResult.NoCurrentLoadout) },
                onSelected = { loadout ->
                    composeLoadout(loadout).flatMap { composedOutput ->
                        when (val outputTarget = input.outputTarget) {
                            LoadoutOutputTarget.StandardOutput ->
                                Result.Success(
                                    SyncLoadoutResult.PrintedToStandardOutput(
                                        loadout = loadout,
                                        composedOutput = composedOutput,
                                    )
                                )

                            is LoadoutOutputTarget.FileSystem ->
                                activateComposedLoadout(
                                    composedOutput = composedOutput,
                                    outputPaths = outputTarget.outputPaths,
                                ).map { writeResult ->
                                    SyncLoadoutResult.Activated(
                                        loadout = loadout,
                                        composedOutput = composedOutput,
                                        writeResult = writeResult,
                                    )
                                }
                        }
                    }
                },
            )
        }
}
