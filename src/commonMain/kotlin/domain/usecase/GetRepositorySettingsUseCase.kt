package domain.usecase

import domain.entity.LoadoutFileLocations
import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.validateRepositorySettings
import domain.repository.RepositorySettingsRepository

class GetRepositorySettingsUseCase(
    private val repositorySettingsRepository: RepositorySettingsRepository,
    private val loadoutFileLocations: LoadoutFileLocations,
) {
    operator fun invoke(): Result<RepositorySettings, LoadoutError> =
        repositorySettingsRepository.loadRepositorySettings().flatMap { settings ->
            validateRepositorySettings(settings, loadoutFileLocations).mapError { error ->
                LoadoutError.RepairableRepositorySettingsError(
                    "Repository settings are invalid: ${error.message}",
                    error.cause,
                )
            }
        }
}
