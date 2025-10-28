package data.repository

import data.serialization.JsonSerializer
import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileRepository
import domain.repository.LoadoutRepository

class FileBasedLoadoutRepository(
    private val fileRepository: FileRepository,
    private val serializer: JsonSerializer,
    private val loadoutsDirectory: String = ".loadouts",
) : LoadoutRepository {
    init {
        fileRepository.createDirectory(loadoutsDirectory)
    }

    override fun findAll(): Result<List<Loadout>, LoadoutError> {
        return fileRepository
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
        return if (!fileRepository.fileExists(filePath)) {
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
                fileRepository.writeFile(filePath, json)
            }
    }

    override fun delete(name: String): Result<Unit, LoadoutError> {
        val filePath = getLoadoutFilePath(name)
        return if (!fileRepository.fileExists(filePath)) {
            Result.Error(LoadoutError.LoadoutNotFound(name))
        } else {
            fileRepository.deleteFile(filePath)
        }
    }

    override fun exists(name: String): Boolean = fileRepository.fileExists(getLoadoutFilePath(name))

    private fun getLoadoutFilePath(name: String): String = "$loadoutsDirectory/$name.json"

    private fun loadLoadoutFromFile(filePath: String): Result<Loadout, LoadoutError> =
        fileRepository
            .readFile(filePath)
            .flatMap { content ->
                serializer
                    .deserialize(content, Loadout.serializer())
                    .mapError { LoadoutError.FileSystemError("Failed to parse loadout: ${it.message}", it) }
            }
}
