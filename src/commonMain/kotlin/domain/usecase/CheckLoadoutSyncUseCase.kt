package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.ConfigRepository

class CheckLoadoutSyncUseCase(
    private val configRepository: ConfigRepository,
    private val getCurrentLoadout: GetCurrentLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
) {
    operator fun invoke(): Result<LoadoutSyncState, LoadoutError> =
        configRepository
            .loadConfig()
            .flatMap { config ->
                when {
                    config.currentLoadoutName == null -> Result.Success(LoadoutSyncState.NoCurrentLoadout)
                    else -> {
                        getCurrentLoadout().flatMap { selection ->
                            when (selection) {
                                CurrentLoadoutSelection.None -> Result.Success(LoadoutSyncState.NoCurrentLoadout)

                                is CurrentLoadoutSelection.Selected ->
                                    if (config.compositionHash == null) {
                                        Result.Success(LoadoutSyncState.OutOfSync)
                                    } else {
                                        composeLoadout(selection.loadout)
                                            .map { composedOutput ->
                                                if (composedOutput.metadata.contentHash == config.compositionHash) {
                                                    LoadoutSyncState.Synchronized
                                                } else {
                                                    LoadoutSyncState.OutOfSync
                                                }
                                            }
                                    }
                            }
                        }
                    }
                }
            }
}
