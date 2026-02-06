package domain.service

import domain.entity.ComposedOutput
import domain.entity.Loadout
import domain.entity.LoadoutConfig
import domain.entity.LoadoutMetadata
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.ConfigRepository
import domain.repository.EnvironmentRepository
import domain.repository.LoadoutRepository
import domain.usecase.CheckLoadoutSyncUseCase
import domain.usecase.WriteComposedFilesUseCase
import kotlin.collections.plus

class LoadoutService(
    private val loadoutRepository: LoadoutRepository,
    private val configRepository: ConfigRepository,
    private val environmentRepository: EnvironmentRepository,
    private val writeComposedFiles: WriteComposedFilesUseCase,
) {
    fun createLoadout(
        name: String,
        description: String,
        fragments: List<String>,
    ): Result<Loadout, LoadoutError> =
        validateLoadoutName(name)
            .flatMap { validName ->
                if (loadoutRepository.exists(validName)) {
                    Result.Error(LoadoutError.LoadoutAlreadyExists(validName))
                } else {
                    val now = environmentRepository.currentTimeMillis()
                    val loadout =
                        Loadout(
                            name = validName,
                            description = description,
                            fragments = fragments,
                            metadata = LoadoutMetadata(createdAt = now, updatedAt = now)
                        )

                    val validationErrors = loadout.validate()
                    if (validationErrors.isNotEmpty()) {
                        Result.Error(LoadoutError.ValidationError("loadout", validationErrors.first()))
                    } else {
                        loadoutRepository.save(loadout).map { loadout }
                    }
                }
            }

    fun createLoadoutFromClone(
        name: String,
        cloneFrom: String,
        description: String? = null,
        additionalFragments: List<String> = emptyList(),
    ): Result<Loadout, LoadoutError> =
        getLoadout(cloneFrom)
            .flatMap { sourceLoadout ->
                validateLoadoutName(name)
                    .map { sourceLoadout to it }
            }.flatMap { (sourceLoadout, validName) ->
                if (loadoutRepository.exists(validName)) {
                    Result.Error(LoadoutError.LoadoutAlreadyExists(validName))
                } else {
                    val now = environmentRepository.currentTimeMillis()
                    val loadout =
                        Loadout(
                            name = validName,
                            description = description ?: sourceLoadout.description,
                            fragments = sourceLoadout.fragments + additionalFragments,
                            metadata = LoadoutMetadata(createdAt = now, updatedAt = now)
                        )

                    val validationErrors = loadout.validate()
                    if (validationErrors.isNotEmpty()) {
                        Result.Error(LoadoutError.ValidationError("loadout", validationErrors.first()))
                    } else {
                        loadoutRepository.save(loadout).map { loadout }
                    }
                }
            }

    fun getLoadout(name: String): Result<Loadout, LoadoutError> =
        loadoutRepository
            .findByName(name)
            .flatMap { loadout ->
                loadout
                    ?.let { Result.Success(it) }
                    ?: Result.Error(LoadoutError.LoadoutNotFound(name))
            }

    fun getAllLoadouts(): Result<List<Loadout>, LoadoutError> = loadoutRepository.findAll()

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

    fun deleteLoadout(name: String): Result<Unit, LoadoutError> = loadoutRepository.delete(name)

    fun addFragmentToLoadout(
        loadoutName: String,
        fragmentPath: String,
        afterFragment: String? = null,
    ): Result<Loadout, LoadoutError> {
        val now = environmentRepository.currentTimeMillis()
        return getLoadout(loadoutName)
            .map { loadout ->
                loadout.addFragment(fragmentPath, afterFragment, now)
            }.flatMap { updatedLoadout ->
                updateLoadout(updatedLoadout)
            }
    }

    fun removeFragmentFromLoadout(
        loadoutName: String,
        fragmentPath: String,
    ): Result<Loadout, LoadoutError> {
        val now = environmentRepository.currentTimeMillis()
        return getLoadout(loadoutName)
            .map { loadout ->
                loadout.removeFragment(fragmentPath, now)
            }.flatMap { updatedLoadout ->
                updateLoadout(updatedLoadout)
            }
    }

    fun setCurrentLoadout(
        composedOutput: ComposedOutput,
        outputPaths: List<String>,
    ): Result<WriteComposedFilesResult, LoadoutError> =
        writeComposedFiles(composedOutput, outputPaths)
            .map { composedOutput to it }
            .flatMap { (composedOutput, writeResult) ->
                when (writeResult) {
                    WriteComposedFilesResult.Overwritten -> {
                        configRepository
                            .saveConfig(
                                LoadoutConfig(
                                    currentLoadoutName = composedOutput.loadoutName,
                                    compositionHash = composedOutput.metadata.contentHash
                                )
                            )
                    }
                    WriteComposedFilesResult.AlreadyUpToDate -> {
                        configRepository
                            .loadConfig()
                            .flatMap { config ->
                                if (config.currentLoadoutName == composedOutput.loadoutName) {
                                    Result.Success(Unit)
                                } else {
                                    configRepository.saveConfig(
                                        LoadoutConfig(
                                            currentLoadoutName = composedOutput.loadoutName,
                                            compositionHash = composedOutput.metadata.contentHash
                                        )
                                    )
                                }
                            }
                    }
                }.map { writeResult }
            }

    fun getCurrentLoadout(): Result<Loadout?, LoadoutError> =
        configRepository
            .loadConfig()
            .flatMap { config ->
                config.currentLoadoutName?.let { currentName ->
                    getLoadout(currentName).map { it as Loadout? }
                } ?: Result.Success(null)
            }

    private fun validateLoadoutName(name: String): Result<String, LoadoutError> =
        if (name.isBlank()) {
            Result.Error(LoadoutError.ValidationError("name", "Name cannot be blank"))
        } else if (!name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            Result.Error(
                LoadoutError.ValidationError(
                    "name",
                    "Name must contain only alphanumeric characters, underscores, and hyphens"
                )
            )
        } else {
            Result.Success(name)
        }
}
