package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.LoadoutRepository
import domain.repository.LocalLoadoutStateRepository
import domain.service.LoadoutCompositionService

class CheckLoadoutSyncUseCase(
    private val localLoadoutStateRepository: LocalLoadoutStateRepository,
    private val loadoutRepository: LoadoutRepository,
    private val compositionService: LoadoutCompositionService,
) {
    operator fun invoke(): Result<Boolean, LoadoutError> =
        localLoadoutStateRepository
            .loadLocalState()
            .flatMap { localLoadoutState ->
                when {
                    localLoadoutState.activeLoadoutName == null -> Result.Success(true)
                    localLoadoutState.lastComposedContentHash == null -> Result.Success(false)
                    else -> {
                        loadoutRepository
                            .findByName(localLoadoutState.activeLoadoutName)
                            .flatMap { loadout ->
                                when (loadout) {
                                    null -> Result.Error(
                                        LoadoutError.LoadoutNotFound(localLoadoutState.activeLoadoutName)
                                    )
                                    else ->
                                        compositionService(loadout)
                                            .map { composedOutput ->
                                                composedOutput.metadata.contentHash ==
                                                    localLoadoutState.lastComposedContentHash
                                            }
                                }
                            }
                    }
                }
            }
}
