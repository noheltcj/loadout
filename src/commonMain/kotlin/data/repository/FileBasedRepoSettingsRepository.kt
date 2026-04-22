package data.repository

import cli.Constants
import data.serialization.JsonSerializer
import domain.entity.RepoSettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileRepository
import domain.repository.RepoSettingsRepository

class FileBasedRepoSettingsRepository(
    private val fileRepository: FileRepository,
    private val serializer: JsonSerializer,
) : RepoSettingsRepository {
    override fun loadSettings(path: String?): Result<RepoSettings, LoadoutError> {
        val settingsPath = path ?: Constants.REPO_SETTINGS_FILE

        return if (!fileRepository.fileExists(settingsPath)) {
            Result.Success(RepoSettings(defaultLoadoutName = null))
        } else {
            fileRepository
                .readFile(settingsPath)
                .flatMap { content ->
                    serializer
                        .deserialize(content, RepoSettings.serializer())
                        .mapError {
                            LoadoutError.ConfigurationError(
                                "Failed to parse repo settings: ${it.message}",
                                it
                            )
                        }
                }
        }
    }

    override fun saveSettings(settings: RepoSettings, path: String?): Result<Unit, LoadoutError> {
        val settingsPath = path ?: Constants.REPO_SETTINGS_FILE

        return serializer
            .serialize(settings, RepoSettings.serializer())
            .mapError { LoadoutError.ConfigurationError("Failed to serialize repo settings: ${it.message}", it) }
            .flatMap { json ->
                fileRepository
                    .writeFile(settingsPath, json)
                    .mapError {
                        LoadoutError.ConfigurationError(
                            "Failed to write repo settings file: ${it.message}",
                            it.cause,
                        )
                    }
            }
    }

    override fun getDefaultPath(): String = Constants.REPO_SETTINGS_FILE
}
