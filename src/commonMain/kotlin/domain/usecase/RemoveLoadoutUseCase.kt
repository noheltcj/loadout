package domain.usecase

import domain.entity.DeleteLoadoutResult
import domain.entity.LoadoutConfig
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.validateLoadoutName
import domain.repository.ConfigRepository
import domain.repository.LoadoutRepository

class RemoveLoadoutUseCase(
    private val loadoutRepository: LoadoutRepository,
    private val configRepository: ConfigRepository,
) {
    operator fun invoke(name: String): Result<DeleteLoadoutResult, LoadoutError> =
        validateLoadoutName(name).flatMap { validName ->
            configRepository.loadConfig().flatMap { config ->
                val clearedCurrentLoadout = config.currentLoadoutName == validName

                loadoutRepository.delete(validName).flatMap {
                    if (clearedCurrentLoadout) {
                        configRepository.saveConfig(
                            LoadoutConfig(
                                currentLoadoutName = null,
                                compositionHash = null,
                            )
                        )
                    } else {
                        Result.Success(Unit)
                    }
                }.map {
                    DeleteLoadoutResult(
                        loadoutName = validName,
                        clearedCurrentLoadout = clearedCurrentLoadout,
                    )
                }
            }
        }
}
