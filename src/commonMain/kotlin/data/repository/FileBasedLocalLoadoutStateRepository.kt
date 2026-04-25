package data.repository

import cli.Constants
import data.serialization.JsonSerializer
import domain.entity.LocalLoadoutState
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileRepository
import domain.repository.LocalLoadoutStateRepository

class FileBasedLocalLoadoutStateRepository(
    private val fileRepository: FileRepository,
    private val serializer: JsonSerializer,
) : LocalLoadoutStateRepository {
    override fun loadLocalState(localStatePath: String?): Result<LocalLoadoutState, LoadoutError> {
        val path = localStatePath ?: Constants.LOCAL_LOADOUT_STATE_FILE

        return if (!fileRepository.fileExists(path)) {
            Result.Success(
                LocalLoadoutState(
                    activeLoadoutName = null,
                    lastComposedContentHash = null,
                )
            )
        } else {
            fileRepository
                .readFile(path)
                .flatMap { content ->
                    serializer
                        .deserialize(content, LocalLoadoutState.serializer())
                        .mapError { LoadoutError.ConfigurationError("Failed to parse local state: ${it.message}", it) }
                }
        }
    }

    override fun saveLocalState(
        localState: LocalLoadoutState,
        localStatePath: String?,
    ): Result<Unit, LoadoutError> {
        val path = localStatePath ?: Constants.LOCAL_LOADOUT_STATE_FILE

        return serializer
            .serialize(localState, LocalLoadoutState.serializer())
            .mapError { LoadoutError.ConfigurationError("Failed to serialize local state: ${it.message}", it) }
            .flatMap { json ->
                fileRepository
                    .writeFile(path, json)
                    .mapError {
                        LoadoutError.ConfigurationError(
                            "Failed to write local state file: ${it.message}",
                            it.cause,
                        )
                    }
            }
    }

    override fun getDefaultLocalStatePath(): String = Constants.LOCAL_LOADOUT_STATE_FILE
}
