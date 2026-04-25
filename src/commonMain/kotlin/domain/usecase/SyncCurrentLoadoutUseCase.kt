package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.Loadout
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

data class SyncCurrentLoadoutInput(
    val outputTarget: LoadoutOutputTarget,
    val autoSync: Boolean = false,
)

sealed interface SyncCurrentLoadoutResult {
    data object NoCurrentLoadout : SyncCurrentLoadoutResult

    data class PrintedToStandardOutput(
        val loadout: Loadout,
        val composedOutput: ComposedOutput,
    ) : SyncCurrentLoadoutResult

    data class Activated(
        val loadout: Loadout,
        val composedOutput: ComposedOutput,
        val writeResult: WriteComposedFilesResult,
    ) : SyncCurrentLoadoutResult
}

class SyncCurrentLoadoutUseCase(
    private val getTargetLoadoutForSync: GetTargetLoadoutForSyncUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
    private val activateComposedLoadout: ActivateComposedLoadoutUseCase,
) {
    operator fun invoke(input: SyncCurrentLoadoutInput): Result<SyncCurrentLoadoutResult, LoadoutError> =
        getTargetLoadoutForSync(input.autoSync).flatMap { selection ->
            selection.fold(
                onNone = { Result.Success(SyncCurrentLoadoutResult.NoCurrentLoadout) },
                onSelected = { loadout ->
                    composeLoadout(loadout).flatMap { composedOutput ->
                        when (val outputTarget = input.outputTarget) {
                            LoadoutOutputTarget.StandardOutput ->
                                Result.Success(
                                    SyncCurrentLoadoutResult.PrintedToStandardOutput(
                                        loadout = loadout,
                                        composedOutput = composedOutput,
                                    )
                                )

                            is LoadoutOutputTarget.FileSystem ->
                                activateComposedLoadout(
                                    ActivateComposedLoadoutInput(
                                        composedOutput = composedOutput,
                                        outputPaths = outputTarget.outputPaths,
                                    )
                                ).map { writeResult ->
                                    SyncCurrentLoadoutResult.Activated(
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
