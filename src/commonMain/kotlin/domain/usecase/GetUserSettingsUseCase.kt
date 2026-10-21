package domain.usecase

import domain.entity.LoadoutFileLocations
import domain.entity.UserSettingsFile
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.UserSettingsRepair
import domain.policy.repairUserSettings
import domain.repository.UserSettingsRepository

data class LocatedUserSettings(
    val file: UserSettingsFile.Located,
    val repair: UserSettingsRepair,
)

class GetUserSettingsUseCase(
    private val userSettingsRepository: UserSettingsRepository,
    private val loadoutFileLocations: LoadoutFileLocations,
) {
    operator fun invoke(): Result<LocatedUserSettings, LoadoutError> =
        when (val file = userSettingsRepository.loadUserSettings()) {
            is UserSettingsFile.Unlocatable ->
                Result.Error(LoadoutError.UserConfigDirectoryNotFound(file.environmentVariables))

            is UserSettingsFile.Located ->
                Result.Success(LocatedUserSettings(file, repairUserSettings(file, loadoutFileLocations)))
        }
}
