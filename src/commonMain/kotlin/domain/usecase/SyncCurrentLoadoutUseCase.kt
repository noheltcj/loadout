package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.Loadout
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

data class SyncCurrentLoadoutInput(
    val outputTarget: LoadoutOutputTarget,
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
    private val getCurrentLoadout: GetCurrentLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
    private val activateComposedLoadout: ActivateComposedLoadoutUseCase,
) {
    operator fun invoke(input: SyncCurrentLoadoutInput): Result<SyncCurrentLoadoutResult, LoadoutError> =
        getCurrentLoadout().flatMap { selection ->
            when (selection) {
                CurrentLoadoutSelection.None -> Result.Success(SyncCurrentLoadoutResult.NoCurrentLoadout)

                is CurrentLoadoutSelection.Selected ->
                    composeLoadout(selection.loadout).flatMap { composedOutput ->
                        when (val outputTarget = input.outputTarget) {
                            LoadoutOutputTarget.StandardOutput ->
                                Result.Success(
                                    SyncCurrentLoadoutResult.PrintedToStandardOutput(
                                        loadout = selection.loadout,
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
                                        loadout = selection.loadout,
                                        composedOutput = composedOutput,
                                        writeResult = writeResult,
                                    )
                                }
                        }
                    }
            }
        }
}
