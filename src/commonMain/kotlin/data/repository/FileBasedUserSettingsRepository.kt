package data.repository

import data.serialization.JsonSerializer
import domain.entity.UserConfigDirectory
import domain.entity.UserSettings
import domain.entity.UserSettingsFile
import domain.entity.UserSettingsLocation
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.EnvironmentRepository
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.repository.UserSettingsRepository

private const val USER_SETTINGS_FILE_IN_CONFIG_DIRECTORY = "loadout/config.json"

/** Keeps user settings in `loadout/config.json` under the user config directory. */
class FileBasedUserSettingsRepository(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val environmentRepository: EnvironmentRepository,
    private val serializer: JsonSerializer,
) : UserSettingsRepository {
    override fun loadUserSettings(): UserSettingsFile =
        when (val configDirectory = environmentRepository.getUserConfigDirectory()) {
            is UserConfigDirectory.Undeterminable ->
                UserSettingsFile.Unlocatable(configDirectory.environmentVariables)

            is UserConfigDirectory.Determined -> loadUserSettings(configDirectory.userSettingsLocation())
        }

    override fun saveUserSettings(
        file: UserSettingsFile.Located,
        settings: UserSettings,
    ): Result<Unit, LoadoutError> {
        val path = file.location.path

        return serializer
            .serialize(settings, UserSettings.serializer())
            .mapError<LoadoutError> {
                LoadoutError.ConfigurationError("Failed to serialize user settings: ${it.message}", it)
            }
            .flatMap { json ->
                fileMutationRepository
                    .createDirectories(path.substringBeforeLast('/'))
                    .flatMap { fileMutationRepository.writeFile(path, json) }
                    .mapError { error ->
                        LoadoutError.ConfigurationError(
                            "Failed to write user settings file: ${error.message}",
                            error.cause,
                        )
                    }
            }
    }

    private fun loadUserSettings(location: UserSettingsLocation): UserSettingsFile.Located =
        if (!fileQueryRepository.fileExists(location.path)) {
            UserSettingsFile.Absent(location)
        } else {
            fileQueryRepository
                .readFile(location.path)
                .mapError { error -> LoadoutError.UnreadableUserSettings(location.path, error.cause) }
                .flatMap { content ->
                    serializer
                        .deserialize(content, UserSettings.serializer())
                        .mapError { error -> LoadoutError.UnreadableUserSettings(location.path, error) }
                }
                .fold(
                    onSuccess = { settings -> UserSettingsFile.Present(location, settings) },
                    onError = { error -> UserSettingsFile.Unreadable(location, error) },
                )
        }
}

private fun UserConfigDirectory.Determined.userSettingsLocation(): UserSettingsLocation =
    UserSettingsLocation(
        path = "$path/$USER_SETTINGS_FILE_IN_CONFIG_DIRECTORY",
        pathInHome = pathInHome?.let { directory -> "$directory/$USER_SETTINGS_FILE_IN_CONFIG_DIRECTORY" },
    )
