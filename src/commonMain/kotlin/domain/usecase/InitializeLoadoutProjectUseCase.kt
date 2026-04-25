package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileRepository

enum class LoadoutInitializationMode {
    Shared,
    Local,
}

sealed interface InitializeLoadoutProjectInput {
    val gitignorePath: String
    val gitignorePatterns: List<String>
    val starterFragmentPath: String
    val starterFragmentContent: String
    val defaultLoadoutName: String
    val defaultLoadoutDescription: String
    val outputPaths: List<String>

    data class Shared(
        override val gitignorePath: String,
        override val gitignorePatterns: List<String>,
        override val starterFragmentPath: String,
        override val starterFragmentContent: String,
        override val defaultLoadoutName: String,
        override val defaultLoadoutDescription: String,
        override val outputPaths: List<String>,
        val hooksDirectoryPath: String,
        val hooks: List<GitHookDefinition>,
    ) : InitializeLoadoutProjectInput

    data class Local(
        override val gitignorePath: String,
        override val gitignorePatterns: List<String>,
        override val starterFragmentPath: String,
        override val starterFragmentContent: String,
        override val defaultLoadoutName: String,
        override val defaultLoadoutDescription: String,
        override val outputPaths: List<String>,
    ) : InitializeLoadoutProjectInput
}

sealed interface GitignoreConfigurationResult {
    data object Updated : GitignoreConfigurationResult
    data object AlreadyConfigured : GitignoreConfigurationResult
}

sealed interface StarterFragmentCreationResult {
    data object Created : StarterFragmentCreationResult
    data object AlreadyExists : StarterFragmentCreationResult
}

sealed interface DefaultLoadoutInitializationResult {
    data class ExistingLoadoutsPresent(val count: Int) : DefaultLoadoutInitializationResult

    data class CreatedAndActivated(
        val composedOutput: ComposedOutput,
        val writeResult: WriteComposedFilesResult,
    ) : DefaultLoadoutInitializationResult
}

data class InitializeLoadoutProjectResult(
    val gitignoreConfiguration: GitignoreConfigurationResult,
    val starterFragmentCreation: StarterFragmentCreationResult,
    val defaultLoadoutInitialization: DefaultLoadoutInitializationResult,
    val gitHooksConfiguration: ConfigureGitHooksResult?,
)

class InitializeLoadoutProjectUseCase(
    private val fileRepository: FileRepository,
    private val listLoadouts: ListLoadoutsUseCase,
    private val createLoadout: CreateLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
    private val activateComposedLoadout: ActivateComposedLoadoutUseCase,
    private val updateRepositorySettings: UpdateRepositorySettingsUseCase,
    private val configureGitHooks: ConfigureGitHooksUseCase,
) {
    operator fun invoke(input: InitializeLoadoutProjectInput): Result<InitializeLoadoutProjectResult, LoadoutError> =
        configureGitignore(input)
            .flatMap { gitignoreConfiguration ->
                createStarterFragment(input).flatMap { starterFragmentCreation ->
                    initializeDefaultLoadoutIfNeeded(input).flatMap { defaultLoadoutInitialization ->
                        configureGitHooksIfNeeded(input).map { gitHooksConfiguration ->
                            InitializeLoadoutProjectResult(
                                gitignoreConfiguration = gitignoreConfiguration,
                                starterFragmentCreation = starterFragmentCreation,
                                defaultLoadoutInitialization = defaultLoadoutInitialization,
                                gitHooksConfiguration = gitHooksConfiguration,
                            )
                        }
                    }
                }
            }

    private fun configureGitignore(
        input: InitializeLoadoutProjectInput,
    ): Result<GitignoreConfigurationResult, LoadoutError> {
        val existingContent =
            when (val result = fileRepository.readFile(input.gitignorePath)) {
                is Result.Success -> result.value
                is Result.Error -> ""
            }

        val newPatterns =
            input.gitignorePatterns.filter { pattern ->
                pattern.isBlank() || !existingContent.contains(pattern.trim())
            }

        if (newPatterns.all { it.isBlank() || existingContent.contains(it.trim()) }) {
            return Result.Success(GitignoreConfigurationResult.AlreadyConfigured)
        }

        val updatedContent =
            buildString {
                if (existingContent.isNotBlank()) {
                    append(existingContent)
                    if (!existingContent.endsWith("\n")) {
                        append("\n")
                    }
                    append("\n")
                }

                val modeName = when (input) {
                    is InitializeLoadoutProjectInput.Shared -> "Shared"
                    is InitializeLoadoutProjectInput.Local -> "Local"
                }
                append("# Loadout CLI - $modeName Mode\n")
                newPatterns.forEach { pattern ->
                    append(pattern)
                    append("\n")
                }
            }

        return fileRepository
            .writeFile(input.gitignorePath, updatedContent)
            .map { GitignoreConfigurationResult.Updated }
    }

    private fun createStarterFragment(
        input: InitializeLoadoutProjectInput,
    ): Result<StarterFragmentCreationResult, LoadoutError> {
        if (fileRepository.fileExists(input.starterFragmentPath)) {
            return Result.Success(StarterFragmentCreationResult.AlreadyExists)
        }

        val fragmentDirectory = input.starterFragmentPath.substringBeforeLast("/", missingDelimiterValue = "")
        if (fragmentDirectory.isNotBlank()) {
            when (val result = fileRepository.createDirectory(fragmentDirectory)) {
                is Result.Success -> Unit
                is Result.Error -> return result
            }
        }

        return fileRepository
            .writeFile(input.starterFragmentPath, input.starterFragmentContent)
            .map { StarterFragmentCreationResult.Created }
    }

    private fun initializeDefaultLoadoutIfNeeded(
        input: InitializeLoadoutProjectInput,
    ): Result<DefaultLoadoutInitializationResult, LoadoutError> =
        listLoadouts().flatMap { loadouts ->
            if (loadouts.isNotEmpty()) {
                configureRepoDefaultForExistingLoadouts(input, loadouts.map { it.name })
                    .map { DefaultLoadoutInitializationResult.ExistingLoadoutsPresent(loadouts.size) }
            } else {
                createLoadout(
                    CreateLoadoutInput.New(
                        name = input.defaultLoadoutName,
                        description = input.defaultLoadoutDescription,
                        fragmentPaths = listOf(input.starterFragmentPath),
                    )
                ).flatMap { loadout ->
                    composeLoadout(loadout).flatMap { composedOutput ->
                        activateComposedLoadout(
                            composedOutput = composedOutput,
                            outputPaths = input.outputPaths,
                        ).flatMap { writeResult ->
                            when (input) {
                                is InitializeLoadoutProjectInput.Shared ->
                                    updateRepositorySettings(input.defaultLoadoutName).map { writeResult }
                                is InitializeLoadoutProjectInput.Local ->
                                    Result.Success(writeResult)
                            }
                        }.map { writeResult ->
                            DefaultLoadoutInitializationResult.CreatedAndActivated(
                                composedOutput = composedOutput,
                                writeResult = writeResult,
                            )
                        }
                    }
                }
            }
        }

    private fun configureRepoDefaultForExistingLoadouts(
        input: InitializeLoadoutProjectInput,
        loadoutNames: List<String>,
    ): Result<Unit, LoadoutError> =
        when (input) {
            is InitializeLoadoutProjectInput.Shared ->
                when (loadoutNames.size) {
                    0 -> Result.Success(Unit)
                    1 -> updateRepositorySettings(loadoutNames.single()).map { Unit }
                    else -> updateRepositorySettings(null).map { Unit }
                }
            is InitializeLoadoutProjectInput.Local ->
                Result.Success(Unit)
        }

    private fun configureGitHooksIfNeeded(
        input: InitializeLoadoutProjectInput,
    ): Result<ConfigureGitHooksResult?, LoadoutError> =
        when (input) {
            is InitializeLoadoutProjectInput.Shared ->
                configureGitHooks(
                    hooksDirectoryPath = input.hooksDirectoryPath,
                    hooks = input.hooks
                ).map { it as ConfigureGitHooksResult? }
            is InitializeLoadoutProjectInput.Local ->
                Result.Success(null)
        }
}
