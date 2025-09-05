package application

import common.Result
import domain.*
import infrastructure.LoadoutRepository
import infrastructure.ConfigRepository

class LoadoutService(
    private val loadoutRepository: LoadoutRepository,
    private val configRepository: ConfigRepository
) {
    
    fun createLoadout(
        name: String,
        description: String = "",
        fragments: List<String> = emptyList()
    ): Result<Loadout, LoadoutError> {
        return validateLoadoutName(name)
            .flatMap { validName ->
                if (loadoutRepository.exists(validName)) {
                    Result.Error(LoadoutError.LoadoutAlreadyExists(validName))
                } else {
                    val loadout = Loadout(
                        name = validName,
                        description = description,
                        fragments = fragments
                    )
                    
                    val validationErrors = loadout.validate()
                    if (validationErrors.isNotEmpty()) {
                        Result.Error(LoadoutError.ValidationError("loadout", validationErrors.first()))
                    } else {
                        loadoutRepository.save(loadout).map { loadout }
                    }
                }
            }
    }
    
    fun getLoadout(name: String): Result<Loadout, LoadoutError> {
        return loadoutRepository.findByName(name)
            .flatMap { loadout ->
                loadout?.let { Result.Success(it) }
                    ?: Result.Error(LoadoutError.LoadoutNotFound(name))
            }
    }
    
    fun getAllLoadouts(): Result<List<Loadout>, LoadoutError> {
        return loadoutRepository.findAll()
    }
    
    fun updateLoadout(loadout: Loadout): Result<Loadout, LoadoutError> {
        if (!loadoutRepository.exists(loadout.name)) {
            return Result.Error(LoadoutError.LoadoutNotFound(loadout.name))
        }
        
        val validationErrors = loadout.validate()
        if (validationErrors.isNotEmpty()) {
            return Result.Error(LoadoutError.ValidationError("loadout", validationErrors.first()))
        }
        
        return loadoutRepository.save(loadout).map { loadout }
    }
    
    fun deleteLoadout(name: String): Result<Unit, LoadoutError> {
        return loadoutRepository.delete(name)
    }
    
    fun addFragmentToLoadout(
        loadoutName: String,
        fragmentPath: String,
        afterFragment: String? = null
    ): Result<Loadout, LoadoutError> {
        return getLoadout(loadoutName)
            .map { loadout ->
                loadout.addFragment(fragmentPath, afterFragment)
            }
            .flatMap { updatedLoadout ->
                updateLoadout(updatedLoadout)
            }
    }
    
    fun removeFragmentFromLoadout(
        loadoutName: String,
        fragmentPath: String
    ): Result<Loadout, LoadoutError> {
        return getLoadout(loadoutName)
            .map { loadout ->
                loadout.removeFragment(fragmentPath)
            }
            .flatMap { updatedLoadout ->
                updateLoadout(updatedLoadout)
            }
    }
    
    fun setCurrentLoadout(name: String): Result<Unit, LoadoutError> {
        return if (!loadoutRepository.exists(name)) {
            Result.Error(LoadoutError.LoadoutNotFound(name))
        } else {
            configRepository.loadConfig()
                .flatMap { config ->
                    val updatedConfig = config.withCurrentLoadout(name)
                    configRepository.saveConfig(updatedConfig)
                }
        }
    }
    
    fun getCurrentLoadout(): Result<Loadout?, LoadoutError> {
        return configRepository.loadConfig()
            .flatMap { config ->
                config.currentLoadout?.let { currentName ->
                    getLoadout(currentName).map { it as Loadout? }
                } ?: Result.Success(null)
            }
    }
    
    private fun validateLoadoutName(name: String): Result<String, LoadoutError> {
        return if (name.isBlank()) {
            Result.Error(LoadoutError.ValidationError("name", "Name cannot be blank"))
        } else if (!name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            Result.Error(LoadoutError.ValidationError("name", 
                "Name must contain only alphanumeric characters, underscores, and hyphens"))
        } else {
            Result.Success(name)
        }
    }
}