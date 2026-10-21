package domain.usecase

import domain.entity.LoadoutFileLocations
import domain.entity.UserSettings
import domain.entity.UserSettingsFile
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.UserSettingsRepair
import domain.policy.UserSettingsRepairWarning
import domain.policy.validateUserSettings
import domain.repository.UserSettingsRepository

sealed interface UserSettingsUpdateInput {
    data class OutputFiles(val outputFiles: List<String>) : UserSettingsUpdateInput

    data object UnsetOutputFiles : UserSettingsUpdateInput
}

/** Changes one user setting, replacing an unreadable file rather than failing on it. */
class UpdateUserSettingsUseCase(
    private val userSettingsRepository: UserSettingsRepository,
    private val loadoutFileLocations: LoadoutFileLocations,
) {
    operator fun invoke(input: UserSettingsUpdateInput): Result<LocatedUserSettings, LoadoutError> =
        when (val file = userSettingsRepository.loadUserSettings()) {
            is UserSettingsFile.Unlocatable ->
                Result.Error(LoadoutError.UserConfigDirectoryNotFound(file.environmentVariables))

            is UserSettingsFile.Located ->
                validateUserSettings(file.currentSettings().updatedBy(input), loadoutFileLocations)
                    .flatMap { validSettings ->
                        userSettingsRepository.saveUserSettings(file, validSettings).map {
                            LocatedUserSettings(
                                file = UserSettingsFile.Present(file.location, validSettings),
                                repair = UserSettingsRepair(validSettings, warnings = file.replacementWarnings()),
                            )
                        }
                    }
        }
}

private fun UserSettingsFile.Located.currentSettings(): UserSettings =
    when (this) {
        is UserSettingsFile.Present -> settings
        is UserSettingsFile.Absent,
        is UserSettingsFile.Unreadable,
        -> UserSettings()
    }

private fun UserSettingsFile.Located.replacementWarnings(): List<UserSettingsRepairWarning> =
    when (this) {
        is UserSettingsFile.Unreadable -> listOf(UserSettingsRepairWarning.UnreadableSettingsReplaced(location, error))
        is UserSettingsFile.Present,
        is UserSettingsFile.Absent,
        -> emptyList()
    }

private fun UserSettings.updatedBy(input: UserSettingsUpdateInput): UserSettings =
    when (input) {
        is UserSettingsUpdateInput.OutputFiles -> copy(outputFiles = input.outputFiles)
        UserSettingsUpdateInput.UnsetOutputFiles -> copy(outputFiles = null)
    }
