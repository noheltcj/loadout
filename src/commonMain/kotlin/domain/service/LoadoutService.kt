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
        description: String?,
        fragments: List<String>,
    ): Result<Loadout, LoadoutError> =
        validateLoadoutName(name)
            .flatMap { validName ->
                description?.let { validateDescription(it) }?.map { validName }
                    ?: Result.Success(validName)
            }
            .flatMap { validName -> validateFragmentExtensions(fragments).map { validName } }
            .flatMap { validName ->
                val normalizedFragments = fragments.map { normalizeFragmentPath(it) }
                validateNoDuplicateFragments(normalizedFragments)
                    .map { validName to normalizedFragments }
            }
            .flatMap { (validName, normalizedFragments) ->
                if (loadoutRepository.exists(validName)) {
                    Result.Error(LoadoutError.LoadoutAlreadyExists(validName))
                } else {
                    val now = environmentRepository.currentTimeMillis()
                    val loadout =
                        Loadout(
                            name = validName,
                            description = description.orEmpty(),
                            fragments = normalizedFragments,
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
            }
            .flatMap { pair ->
                description
                    ?.let { validateDescription(it) }
                    ?.map { pair }
                    ?: Result.Success(pair)
            }
            .flatMap { pair -> validateFragmentExtensions(additionalFragments).map { pair } }
            .flatMap { (sourceLoadout, validName) ->
                val normalizedAdditional = additionalFragments.map { normalizeFragmentPath(it) }
                val allFragments = normalizeFragmentPaths(sourceLoadout.fragments) + normalizedAdditional
                validateNoDuplicateFragments(allFragments)
                    .map { Triple(sourceLoadout, validName, allFragments) }
            }
            .flatMap { (sourceLoadout, validName, allFragments) ->
                if (loadoutRepository.exists(validName)) {
                    Result.Error(LoadoutError.LoadoutAlreadyExists(validName))
                } else {
                    val now = environmentRepository.currentTimeMillis()
                    val loadout =
                        Loadout(
                            name = validName,
                            description = description ?: sourceLoadout.description,
                            fragments = allFragments,
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
        validateLoadoutName(name).flatMap { validName ->
            loadoutRepository
                .findByName(validName)
                .flatMap { loadout ->
                    loadout
                        ?.let { Result.Success(it) }
                        ?: Result.Error(LoadoutError.LoadoutNotFound(validName))
                }
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

    fun deleteLoadout(name: String): Result<Unit, LoadoutError> =
        validateLoadoutName(name).flatMap { validName -> loadoutRepository.delete(validName) }

    fun addFragmentToLoadout(
        loadoutName: String,
        fragmentPath: String,
        afterFragment: String? = null,
    ): Result<Loadout, LoadoutError> {
        val now = environmentRepository.currentTimeMillis()

        return validateFragmentExtension(fragmentPath)
            .flatMap {
                val normalizedPath = normalizeFragmentPath(fragmentPath)
                val normalizedAfter = afterFragment?.let { normalizeFragmentPath(it) }

                getLoadout(loadoutName)
                    .flatMap { loadout ->
                        val normalizedLoadout = normalizeStoredLoadout(loadout)

                        if (normalizedPath in normalizedLoadout.fragments) {
                            Result.Error(LoadoutError.FragmentAlreadyInLoadout(normalizedPath, loadoutName))
                        } else {
                            Result.Success(normalizedLoadout.addFragment(normalizedPath, normalizedAfter, now))
                        }
                    }.flatMap { updatedLoadout ->
                        updateLoadout(updatedLoadout)
                    }
            }
    }

    fun removeFragmentFromLoadout(
        loadoutName: String,
        fragmentPath: String,
    ): Result<Loadout, LoadoutError> {
        val now = environmentRepository.currentTimeMillis()
        val normalizedPath = normalizeFragmentPath(fragmentPath)

        return getLoadout(loadoutName)
            .flatMap { loadout ->
                val normalizedLoadout = normalizeStoredLoadout(loadout)

                if (normalizedPath !in normalizedLoadout.fragments) {
                    Result.Error(LoadoutError.FragmentNotInLoadout(normalizedPath, loadoutName))
                } else {
                    Result.Success(normalizedLoadout.removeFragment(normalizedPath, now))
                }
            }
            .flatMap { updatedLoadout ->
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

    private fun validateDescription(description: String): Result<Unit, LoadoutError> =
        if (description.isBlank()) {
            Result.Error(LoadoutError.ValidationError("description", "Description cannot be blank if provided"))
        } else {
            Result.Success(Unit)
        }

    private fun validateFragmentExtension(path: String): Result<Unit, LoadoutError> =
        if (!path.endsWith(".md", ignoreCase = true)) {
            Result.Error(LoadoutError.ValidationError("fragment", "Fragment must be a markdown file (.md)"))
        } else {
            Result.Success(Unit)
        }

    private fun validateFragmentExtensions(paths: List<String>): Result<Unit, LoadoutError> {
        for (path in paths) {
            val result = validateFragmentExtension(path)
            if (result is Result.Error) return result
        }
        return Result.Success(Unit)
    }

    private fun validateNoDuplicateFragments(normalizedPaths: List<String>): Result<Unit, LoadoutError> {
        val duplicates = normalizedPaths.groupingBy { it }.eachCount().filter { it.value > 1 }
        return if (duplicates.isNotEmpty()) {
            Result.Error(
                LoadoutError.ValidationError(
                    "fragment",
                    "Duplicate fragments provided: ${duplicates.keys.joinToString()}"
                )
            )
        } else {
            Result.Success(Unit)
        }
    }

    private fun normalizeStoredLoadout(loadout: Loadout): Loadout =
        loadout.copy(fragments = normalizeFragmentPaths(loadout.fragments))

    private fun normalizeFragmentPaths(paths: List<String>): List<String> = paths.map { normalizeFragmentPath(it) }

    private fun normalizeFragmentPath(path: String): String =
        path
            .replace(Regex("(^\\./)+"), "")
            .replace(Regex("/\\./"), "/")
            .replace(Regex("/{2,}"), "/")
}
