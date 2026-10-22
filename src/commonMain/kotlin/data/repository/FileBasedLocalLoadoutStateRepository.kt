package data.repository

import data.serialization.JsonSerializer
import domain.entity.LocalLoadoutState
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.repository.LocalLoadoutStateRepository

class FileBasedLocalLoadoutStateRepository(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val serializer: JsonSerializer,
    private val localStateFile: String,
) : LocalLoadoutStateRepository {
    override fun loadLocalState(): Result<LocalLoadoutState, LoadoutError.UnreadableLocalState> =
        if (!fileQueryRepository.fileExists(localStateFile)) {
            Result.Success(LocalLoadoutState.Inactive)
        } else {
            fileQueryRepository
                .readFile(localStateFile)
                .mapError { error -> LoadoutError.UnreadableLocalState(localStateFile, error.cause) }
                .flatMap { content ->
                    serializer
                        .deserialize(content, LocalLoadoutState.serializer())
                        .mapError { error -> LoadoutError.UnreadableLocalState(localStateFile, error) }
                }
        }

    override fun saveLocalState(localState: LocalLoadoutState): Result<Unit, LoadoutError> =
        serializer
            .serialize(localState, LocalLoadoutState.serializer())
            .mapError { LoadoutError.ConfigurationError("Failed to serialize local state: ${it.message}", it) }
            .flatMap { json ->
                fileMutationRepository
                    .writeFile(localStateFile, json)
                    .mapError {
                        LoadoutError.ConfigurationError(
                            "Failed to write local state file: ${it.message}",
                            it.cause,
                        )
                    }
            }
}
