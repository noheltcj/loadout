package infrastructure

import common.Result
import domain.LoadoutConfig
import domain.LoadoutError

interface ConfigRepository {
    fun loadConfig(configPath: String? = null): Result<LoadoutConfig, LoadoutError>
    fun saveConfig(config: LoadoutConfig, configPath: String? = null): Result<Unit, LoadoutError>
    fun getDefaultConfigPath(): String
}

class FileBasedConfigRepository(
    private val fileSystem: FileSystem,
    private val serializer: JsonSerializer
) : ConfigRepository {
    
    companion object {
        private const val DEFAULT_CONFIG_FILE = ".loadout.json"
    }
    
    override fun loadConfig(configPath: String?): Result<LoadoutConfig, LoadoutError> {
        val path = configPath ?: DEFAULT_CONFIG_FILE
        
        return if (!fileSystem.fileExists(path)) {
            Result.Success(LoadoutConfig())
        } else {
            fileSystem.readFile(path)
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
                fileSystem.writeFile(path, json)
                    .mapError { LoadoutError.ConfigurationError("Failed to write config file: ${it.message}", it.cause) }
            }
    }
    
    override fun getDefaultConfigPath(): String = DEFAULT_CONFIG_FILE
}