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
    override fun loadSettings(): Result<RepoSettings, LoadoutError> =
        if (!fileRepository.fileExists(Constants.REPO_SETTINGS_FILE)) {
            Result.Success(RepoSettings(defaultLoadoutName = null))
        } else {
            fileRepository
                .readFile(Constants.REPO_SETTINGS_FILE)
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

    override fun saveSettings(settings: RepoSettings): Result<Unit, LoadoutError> =
        serializer
            .serialize(settings, RepoSettings.serializer())
            .mapError { LoadoutError.ConfigurationError("Failed to serialize repo settings: ${it.message}", it) }
            .flatMap { json ->
                fileRepository
                    .writeFile(Constants.REPO_SETTINGS_FILE, json)
                    .mapError {
                        LoadoutError.ConfigurationError(
                            "Failed to write repo settings file: ${it.message}",
                            it.cause,
                        )
                    }
            }
}
