package domain.policy

import domain.entity.LoadoutFileLocations
import domain.entity.UserSettings
import domain.entity.UserSettingsFile
import domain.entity.UserSettingsLocation
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

/**
 * Accepts user settings that are valid in every repository. Each repository's fragments home is reserved too, but it
 * differs by repository, so [repairUserSettingsForRepository] checks it when a repository resolves its output files.
 */
fun validateUserSettings(
    settings: UserSettings,
    loadoutFileLocations: LoadoutFileLocations,
): Result<UserSettings, LoadoutError> =
    validateOutputFileList(settings.outputFiles, reservedOutputDirectories(loadoutFileLocations))
        .map { validOutputFiles -> settings.copy(outputFiles = validOutputFiles) }

data class UserSettingsRepair(
    val settings: UserSettings,
    val warnings: List<UserSettingsRepairWarning>,
)

sealed interface UserSettingsRepairWarning {
    data class UnreadableSettingsIgnored(
        val location: UserSettingsLocation,
        val error: LoadoutError.UnreadableUserSettings,
    ) : UserSettingsRepairWarning

    data class UnreadableSettingsReplaced(
        val location: UserSettingsLocation,
        val error: LoadoutError.UnreadableUserSettings,
    ) : UserSettingsRepairWarning

    data class InvalidOutputFileIgnored(
        val invalidOutputFile: String,
        val error: LoadoutError.ValidationError,
    ) : UserSettingsRepairWarning
}

/** Sets aside what no repository can use. An unreadable file reads as empty settings. */
fun repairUserSettings(
    file: UserSettingsFile,
    loadoutFileLocations: LoadoutFileLocations,
): UserSettingsRepair = repairUserSettingsAgainst(file, reservedOutputDirectories(loadoutFileLocations))

/**
 * Sets aside what the repository whose fragments home is [fragmentsHome] cannot use. A list left with no valid entries
 * becomes unset, so the repository setting applies instead.
 */
fun repairUserSettingsForRepository(
    file: UserSettingsFile,
    loadoutFileLocations: LoadoutFileLocations,
    fragmentsHome: String,
): UserSettingsRepair = repairUserSettingsAgainst(file, reservedOutputDirectories(loadoutFileLocations) + fragmentsHome)

private fun repairUserSettingsAgainst(
    file: UserSettingsFile,
    reservedDirectories: List<String>,
): UserSettingsRepair =
    when (file) {
        is UserSettingsFile.Unlocatable,
        is UserSettingsFile.Absent,
        -> UserSettingsRepair(UserSettings(), warnings = emptyList())

        is UserSettingsFile.Unreadable ->
            UserSettingsRepair(
                settings = UserSettings(),
                warnings = listOf(UserSettingsRepairWarning.UnreadableSettingsIgnored(file.location, file.error)),
            )

        is UserSettingsFile.Present -> repairPresentUserSettings(file.settings, reservedDirectories)
    }

private fun repairPresentUserSettings(
    settings: UserSettings,
    reservedDirectories: List<String>,
): UserSettingsRepair {
    val repair = settings.outputFiles?.let { outputFiles -> repairOutputFileList(outputFiles, reservedDirectories) }

    return UserSettingsRepair(
        settings =
        settings.copy(outputFiles = repair?.outputFiles?.map(OutputFilePath::value)?.ifEmpty { null }),
        warnings =
        repair?.rejections.orEmpty().map { rejection ->
            UserSettingsRepairWarning.InvalidOutputFileIgnored(rejection.outputFile, rejection.error)
        },
    )
}
