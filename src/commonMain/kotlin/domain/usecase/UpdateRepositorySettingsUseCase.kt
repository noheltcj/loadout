package domain.usecase

import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.validateLoadoutName
import domain.repository.LoadoutRepository
import domain.repository.RepositorySettingsRepository

class UpdateRepositorySettingsUseCase(
    private val repositorySettingsRepository: RepositorySettingsRepository,
    private val loadoutRepository: LoadoutRepository,
) {
    operator fun invoke(defaultLoadoutName: String?): Result<RepositorySettings, LoadoutError> =
        if (defaultLoadoutName != null) {
            validateLoadoutName(defaultLoadoutName).flatMap { validName ->
                if (!loadoutRepository.exists(validName)) {
                    Result.Error(LoadoutError.LoadoutNotFound(validName))
                } else {
                    val settings = RepositorySettings(defaultLoadoutName = validName)
                    repositorySettingsRepository.saveRepositorySettings(settings).map { settings }
                }
            }
        } else {
            val settings = RepositorySettings(defaultLoadoutName = null)
            repositorySettingsRepository.saveRepositorySettings(settings).map { settings }
        }
}
