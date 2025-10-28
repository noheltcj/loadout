package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.ConfigRepository
import domain.repository.LoadoutRepository
import domain.service.LoadoutCompositionService

class CheckLoadoutSyncUseCase(
    private val configRepository: ConfigRepository,
    private val loadoutRepository: LoadoutRepository,
    private val compositionService: LoadoutCompositionService
) {
    operator fun invoke(): Result<Boolean, LoadoutError> {
        return configRepository.loadConfig()
            .flatMap { config ->
                when {
                    config.currentLoadoutName == null -> Result.Success(true)
                    config.compositionHash == null -> Result.Success(false)
                    else -> {
                        loadoutRepository.findByName(config.currentLoadoutName)
                            .flatMap { loadout ->
                                when (loadout) {
                                    null -> Result.Success(false)
                                    else -> compositionService(loadout)
                                        .map { composedOutput ->
                                            composedOutput.metadata.contentHash == config.compositionHash
                                        }
                                }
                            }
                    }
                }
            }
    }
}
