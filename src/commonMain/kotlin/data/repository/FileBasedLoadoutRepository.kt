package data.repository

import data.serialization.JsonSerializer
import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.repository.LoadoutRepository

class FileBasedLoadoutRepository(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val serializer: JsonSerializer,
    private val loadoutsDirectory: String,
) : LoadoutRepository {
    override fun findAll(): Result<List<Loadout>, LoadoutError> {
        if (!fileQueryRepository.fileExists(loadoutsDirectory)) return Result.Success(emptyList())

        return fileQueryRepository
            .listFiles(loadoutsDirectory, "json")
            .flatMap { files ->
                val loadouts = mutableListOf<Loadout>()
                for (file in files) {
                    when (val result = loadLoadoutFromFile(file)) {
                        is Result.Success -> loadouts.add(result.value)
                        is Result.Error -> return result
                    }
                }
                Result.Success(loadouts.sortedBy { it.name })
            }
    }

    override fun findByName(name: String): Result<Loadout?, LoadoutError> {
        val filePath = getLoadoutFilePath(name)
        return if (!fileQueryRepository.fileExists(filePath)) {
            Result.Success(null)
        } else {
            loadLoadoutFromFile(filePath).map { it }
        }
    }

    override fun save(loadout: Loadout): Result<Unit, LoadoutError> {
        val filePath = getLoadoutFilePath(loadout.name)
        return serializer
            .serialize(loadout, Loadout.serializer())
            .mapError { serializationException ->
                LoadoutError.SerializationError(
                    message = "Failed to serialize loadout: ${serializationException.message}",
                    serializationException,
                ) as LoadoutError
            }.flatMap { json ->
                // Created on the first save, so commands run outside a repository leave nothing behind.
                fileMutationRepository
                    .createDirectory(loadoutsDirectory)
                    .flatMap { fileMutationRepository.writeFile(filePath, json) }
            }
    }

    override fun delete(name: String): Result<Unit, LoadoutError> {
        val filePath = getLoadoutFilePath(name)
        return if (!fileQueryRepository.fileExists(filePath)) {
            Result.Error(LoadoutError.LoadoutNotFound(name))
        } else {
            fileMutationRepository.deleteFile(filePath)
        }
    }

    override fun exists(name: String): Boolean = fileQueryRepository.fileExists(getLoadoutFilePath(name))

    private fun getLoadoutFilePath(name: String): String = "$loadoutsDirectory/$name.json"

    private fun loadLoadoutFromFile(filePath: String): Result<Loadout, LoadoutError> =
        fileQueryRepository
            .readFile(filePath)
            .flatMap { content ->
                serializer
                    .deserialize(content, Loadout.serializer())
                    .mapError { LoadoutError.FileSystemError("Failed to parse loadout: ${it.message}", it) }
            }
}
