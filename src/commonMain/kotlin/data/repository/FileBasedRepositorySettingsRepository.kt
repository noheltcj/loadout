package data.repository

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
    private val repositorySettingsFile: String,
) : RepositorySettingsRepository {
    override fun loadRepositorySettings(): Result<RepositorySettings, LoadoutError> =
        if (!fileQueryRepository.fileExists(repositorySettingsFile)) {
            Result.Success(RepositorySettings())
        } else {
            fileQueryRepository
                .readFile(repositorySettingsFile)
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
                    .writeFile(repositorySettingsFile, json)
                    .mapError {
                        LoadoutError.ConfigurationError(
                            "Failed to write repository settings file: ${it.message}",
                            it.cause,
                        )
                    }
            }
}
