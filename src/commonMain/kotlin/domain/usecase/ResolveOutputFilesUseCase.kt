package domain.usecase

import domain.entity.LoadoutFileLocations
import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.OutputFilePath
import domain.policy.UserSettingsRepairWarning
import domain.policy.repairUserSettingsForRepository
import domain.policy.validateOutputFilePaths
import domain.repository.UserSettingsRepository

/** The files Loadout writes when no settings layer lists any. */
private val builtInOutputFiles = listOf("CLAUDE.md", "AGENTS.md", "GEMINI.md").map(::OutputFilePath)

data class OutputFilesResolution(
    /** Never empty, and free of paths that differ only by case. */
    val outputFiles: List<OutputFilePath>,
    val source: OutputFilesSource,
    /** What the user layer set aside, such as an entry inside this repository's fragments home. */
    val userSettingsWarnings: List<UserSettingsRepairWarning>,
)

sealed interface OutputFilesSource {
    data object BuiltInDefault : OutputFilesSource

    data object RepositorySetting : OutputFilesSource

    data class UserSetting(
        /** The repository's list, which the user setting replaces, or null when the repository sets none. */
        val overriddenRepositorySetting: List<OutputFilePath>?,
    ) : OutputFilesSource
}

/**
 * Decides which files Loadout writes: the user setting, then the repository setting, then the built-in default. A
 * layer that sets a list sets the whole list.
 *
 * The user setting wins because the generated files are per-clone files that serve only this developer's agents. The
 * repository setting is the default for contributors who have not chosen. Without a user config directory, or with an
 * unreadable user settings file, the user layer is skipped.
 */
class ResolveOutputFilesUseCase(
    private val userSettingsRepository: UserSettingsRepository,
    private val loadoutFileLocations: LoadoutFileLocations,
) {
    /** [repositorySettings] must come from a repairing or validating read, which already checked the output files. */
    operator fun invoke(repositorySettings: RepositorySettings): Result<OutputFilesResolution, LoadoutError> {
        val userSettings =
            repairUserSettingsForRepository(
                file = userSettingsRepository.loadUserSettings(),
                loadoutFileLocations = loadoutFileLocations,
                fragmentsHome = repositorySettings.fragmentsHome,
            )

        return validateOptionalOutputFilePaths(repositorySettings.outputFiles).flatMap { repositoryOutputFiles ->
            validateOptionalOutputFilePaths(userSettings.settings.outputFiles).map { userOutputFiles ->
                when {
                    userOutputFiles != null ->
                        OutputFilesResolution(
                            outputFiles = userOutputFiles,
                            source = OutputFilesSource.UserSetting(overriddenRepositorySetting = repositoryOutputFiles),
                            userSettingsWarnings = userSettings.warnings,
                        )

                    repositoryOutputFiles != null ->
                        OutputFilesResolution(
                            outputFiles = repositoryOutputFiles,
                            source = OutputFilesSource.RepositorySetting,
                            userSettingsWarnings = userSettings.warnings,
                        )

                    else ->
                        OutputFilesResolution(
                            outputFiles = builtInOutputFiles,
                            source = OutputFilesSource.BuiltInDefault,
                            userSettingsWarnings = userSettings.warnings,
                        )
                }
            }
        }
    }
}

private fun validateOptionalOutputFilePaths(outputFiles: List<String>?): Result<List<OutputFilePath>?, LoadoutError> =
    outputFiles?.let(::validateOutputFilePaths) ?: Result.Success(null)
