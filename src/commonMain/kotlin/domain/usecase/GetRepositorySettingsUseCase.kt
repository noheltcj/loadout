package domain.usecase

import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.RepositorySettingsRepository

class GetRepositorySettingsUseCase(
    private val repositorySettingsRepository: RepositorySettingsRepository,
) {
    operator fun invoke(): Result<RepositorySettings, LoadoutError> =
        repositorySettingsRepository.loadRepositorySettings()
}
