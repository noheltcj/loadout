package data.repository

import cli.Constants
import data.serialization.JsonSerializer
import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileRepository
import domain.repository.RepositorySettingsRepository

class FileBasedRepositorySettingsRepository(
    private val fileRepository: FileRepository,
    private val serializer: JsonSerializer,
) : RepositorySettingsRepository {
    override fun loadRepositorySettings(): Result<RepositorySettings, LoadoutError> =
        if (!fileRepository.fileExists(Constants.REPOSITORY_SETTINGS_FILE)) {
            Result.Success(RepositorySettings(defaultLoadoutName = null))
        } else {
            fileRepository
                .readFile(Constants.REPOSITORY_SETTINGS_FILE)
                .flatMap { content ->
                    serializer
                        .deserialize(content, RepositorySettings.serializer())
                        .mapError {
                            LoadoutError.ConfigurationError(
                                "Failed to parse repository settings: ${it.message}",
                                it
                            )
                        }
                }
        }

    override fun saveRepositorySettings(settings: RepositorySettings): Result<Unit, LoadoutError> =
        serializer
            .serialize(settings, RepositorySettings.serializer())
            .mapError { LoadoutError.ConfigurationError("Failed to serialize repository settings: ${it.message}", it) }
            .flatMap { json ->
                fileRepository
                    .writeFile(Constants.REPOSITORY_SETTINGS_FILE, json)
                    .mapError {
                        LoadoutError.ConfigurationError(
                            "Failed to write repository settings file: ${it.message}",
                            it.cause,
                        )
                    }
            }
}
