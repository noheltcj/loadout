package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.Loadout
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
        val activation: LoadoutActivation,
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
                        when (input.outputTarget) {
                            LoadoutOutputTarget.StandardOutput ->
                                Result.Success(
                                    SyncLoadoutResult.PrintedToStandardOutput(
                                        loadout = loadout,
                                        composedOutput = composedOutput,
                                    )
                                )

                            LoadoutOutputTarget.ConfiguredOutputFiles ->
                                activateComposedLoadout(composedOutput).map { activation ->
                                    SyncLoadoutResult.Activated(
                                        loadout = loadout,
                                        composedOutput = composedOutput,
                                        activation = activation,
                                    )
                                }
                        }
                    }
                },
            )
        }
}
