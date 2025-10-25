package data.repository

import domain.repository.FileRepository
import data.serialization.JsonSerializer
import domain.entity.packaging.Result
import domain.entity.LoadoutConfig
import domain.entity.error.LoadoutError
import domain.repository.ConfigRepository

class FileBasedConfigRepository(
    private val fileRepository: FileRepository,
    private val serializer: JsonSerializer
) : ConfigRepository {
    
    companion object {
        private const val DEFAULT_CONFIG_FILE = ".loadout.json"
    }
    
    override fun loadConfig(configPath: String?): Result<LoadoutConfig, LoadoutError> {
        val path = configPath ?: DEFAULT_CONFIG_FILE
        
        return if (!fileRepository.fileExists(path)) {
            Result.Success(LoadoutConfig(currentLoadoutName = null))
        } else {
            fileRepository.readFile(path)
                .flatMap { content ->
                    serializer.deserialize(content, LoadoutConfig.serializer())
                        .mapError { LoadoutError.ConfigurationError("Failed to parse config: ${it.message}", it) }
                }
        }
    }
    
    override fun saveConfig(config: LoadoutConfig, configPath: String?): Result<Unit, LoadoutError> {
        val path = configPath ?: DEFAULT_CONFIG_FILE
        
        return serializer.serialize(config, LoadoutConfig.serializer())
            .mapError { LoadoutError.ConfigurationError("Failed to serialize config: ${it.message}", it) }
            .flatMap { json ->
                fileRepository.writeFile(path, json)
                    .mapError { LoadoutError.ConfigurationError("Failed to write config file: ${it.message}", it.cause) }
            }
    }
    
    override fun getDefaultConfigPath(): String = DEFAULT_CONFIG_FILE
}