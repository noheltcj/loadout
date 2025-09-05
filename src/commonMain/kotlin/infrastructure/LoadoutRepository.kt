package infrastructure

import common.Result
import domain.Loadout
import domain.LoadoutError

interface LoadoutRepository {
    fun findAll(): Result<List<Loadout>, LoadoutError>
    fun findByName(name: String): Result<Loadout?, LoadoutError>
    fun save(loadout: Loadout): Result<Unit, LoadoutError>
    fun delete(name: String): Result<Unit, LoadoutError>
    fun exists(name: String): Boolean
}

class FileBasedLoadoutRepository(
    private val fileSystem: FileSystem,
    private val serializer: JsonSerializer,
    private val loadoutsDirectory: String = ".loadouts"
) : LoadoutRepository {
    
    init {
        fileSystem.createDirectory(loadoutsDirectory)
    }
    
    override fun findAll(): Result<List<Loadout>, LoadoutError> {
        return fileSystem.listFiles(loadoutsDirectory, "json")
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
        return if (!fileSystem.fileExists(filePath)) {
            Result.Success(null)
        } else {
            loadLoadoutFromFile(filePath).map { it }
        }
    }
    
    override fun save(loadout: Loadout): Result<Unit, LoadoutError> {
        val filePath = getLoadoutFilePath(loadout.name)
        return serializer.serialize(loadout, Loadout.serializer())
            .mapError { LoadoutError.FileSystemError("Failed to serialize loadout: ${it.message}", it) }
            .flatMap { json ->
                fileSystem.writeFile(filePath, json)
                    .mapError { LoadoutError.FileSystemError("Failed to write loadout file: ${it.message}", it.cause) }
            }
    }
    
    override fun delete(name: String): Result<Unit, LoadoutError> {
        val filePath = getLoadoutFilePath(name)
        return if (!fileSystem.fileExists(filePath)) {
            Result.Error(LoadoutError.LoadoutNotFound(name))
        } else {
            fileSystem.deleteFile(filePath)
        }
    }
    
    override fun exists(name: String): Boolean {
        return fileSystem.fileExists(getLoadoutFilePath(name))
    }
    
    private fun getLoadoutFilePath(name: String): String = "$loadoutsDirectory/$name.json"
    
    private fun loadLoadoutFromFile(filePath: String): Result<Loadout, LoadoutError> {
        return fileSystem.readFile(filePath)
            .flatMap { content ->
                serializer.deserialize(content, Loadout.serializer())
                    .mapError { LoadoutError.FileSystemError("Failed to parse loadout: ${it.message}", it) }
            }
    }
}