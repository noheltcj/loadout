package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.LocalLoadoutStateRepository

class CheckLoadoutSyncUseCase(
    private val localLoadoutStateRepository: LocalLoadoutStateRepository,
    private val getCurrentLoadout: GetCurrentLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
) {
    operator fun invoke(): Result<LoadoutSyncState, LoadoutError> =
        localLoadoutStateRepository
            .loadLocalState()
            .flatMap { state ->
                if (state.activeLoadoutName == null) {
                    Result.Success(LoadoutSyncState.NoCurrentLoadout)
                } else {
                    getCurrentLoadout().flatMap { selection ->
                        selection.fold(
                            onNone = { Result.Success(LoadoutSyncState.NoCurrentLoadout) },
                            onSelected = { loadout ->
                                if (state.lastComposedContentHash == null) {
                                    Result.Success(LoadoutSyncState.OutOfSync)
                                } else {
                                    composeLoadout(loadout)
                                        .map { composedOutput ->
                                            if (composedOutput.metadata.contentHash == state.lastComposedContentHash) {
                                                LoadoutSyncState.Synchronized
                                            } else {
                                                LoadoutSyncState.OutOfSync
                                            }
                                        }
                                }
                            },
                        )
                    }
                }
            }
}
