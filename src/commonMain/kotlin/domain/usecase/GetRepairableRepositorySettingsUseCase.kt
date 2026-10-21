package domain.usecase

import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.RepositorySettingsRepair
import domain.policy.RepositorySettingsRepairWarning
import domain.policy.repairRepositorySettings
import domain.repository.RepositorySettingsRepository

class GetRepairableRepositorySettingsUseCase(
    private val repositorySettingsRepository: RepositorySettingsRepository,
) {
    operator fun invoke(): Result<RepositorySettingsRepair, LoadoutError> =
        when (val result = repositorySettingsRepository.loadRepositorySettings()) {
            is Result.Success -> Result.Success(repairRepositorySettings(result.value))
            is Result.Error ->
                when (val error = result.error) {
                    is LoadoutError.RepairableRepositorySettingsError ->
                        Result.Success(
                            RepositorySettingsRepair(
                                settings = RepositorySettings(),
                                warnings = listOf(RepositorySettingsRepairWarning.UnreadableSettingsReplaced(error)),
                            )
                        )
                    else -> result
                }
        }
}
