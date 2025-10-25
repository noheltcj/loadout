package domain.repository

import domain.entity.LoadoutConfig
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface ConfigRepository {
    fun loadConfig(configPath: String? = null): Result<LoadoutConfig, LoadoutError>
    fun saveConfig(config: LoadoutConfig, configPath: String? = null): Result<Unit, LoadoutError>
    fun getDefaultConfigPath(): String
}