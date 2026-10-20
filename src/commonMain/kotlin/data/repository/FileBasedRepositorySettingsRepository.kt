package data.repository

import cli.Constants
import data.serialization.JsonSerializer
import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.repository.RepositorySettingsRepository

class FileBasedRepositorySettingsRepository(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val serializer: JsonSerializer,
) : RepositorySettingsRepository {
    override fun loadRepositorySettings(): Result<RepositorySettings, LoadoutError> =
        if (!fileQueryRepository.fileExists(Constants.REPOSITORY_SETTINGS_FILE)) {
            Result.Success(RepositorySettings())
        } else {
            fileQueryRepository
                .readFile(Constants.REPOSITORY_SETTINGS_FILE)
                .flatMap { content ->
                    serializer
                        .deserialize(content, RepositorySettings.serializer())
                        .mapError<LoadoutError> {
                            LoadoutError.RepairableRepositorySettingsError(
                                "Failed to parse repository settings: ${it.message}",
                                it
                            )
                        }
                }
        }

    override fun saveRepositorySettings(settings: RepositorySettings): Result<Unit, LoadoutError> =
        serializer
            .serialize(settings, RepositorySettings.serializer())
            .mapError<LoadoutError> {
                LoadoutError.ConfigurationError("Failed to serialize repository settings: ${it.message}", it)
            }
            .flatMap { json ->
                fileMutationRepository
                    .writeFile(Constants.REPOSITORY_SETTINGS_FILE, json)
                    .mapError {
                        LoadoutError.ConfigurationError(
                            "Failed to write repository settings file: ${it.message}",
                            it.cause,
                        )
                    }
            }
}
