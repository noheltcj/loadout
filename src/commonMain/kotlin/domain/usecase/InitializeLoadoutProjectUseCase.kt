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

data class InitializeLoadoutProjectInput(
    val mode: LoadoutInitializationMode,
    val gitignorePath: String,
    val gitignorePatterns: List<String>,
    val starterFragmentPath: String,
    val starterFragmentContent: String,
    val defaultLoadoutName: String,
    val defaultLoadoutDescription: String,
    val outputPaths: List<String>,
)

sealed interface GitignoreConfigurationResult {
    data object Updated : GitignoreConfigurationResult
    data object AlreadyConfigured : GitignoreConfigurationResult
}

sealed interface StarterFragmentCreationResult {
    data object Created : StarterFragmentCreationResult
    data object AlreadyExists : StarterFragmentCreationResult
}

sealed interface DefaultLoadoutInitializationResult {
    data object ExistingLoadoutsPresent : DefaultLoadoutInitializationResult

    data class CreatedAndActivated(
        val composedOutput: ComposedOutput,
        val writeResult: WriteComposedFilesResult,
    ) : DefaultLoadoutInitializationResult
}

data class InitializeLoadoutProjectResult(
    val gitignoreConfiguration: GitignoreConfigurationResult,
    val starterFragmentCreation: StarterFragmentCreationResult,
    val defaultLoadoutInitialization: DefaultLoadoutInitializationResult,
)

class InitializeLoadoutProjectUseCase(
    private val fileRepository: FileRepository,
    private val listLoadouts: ListLoadoutsUseCase,
    private val createLoadout: CreateLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
    private val activateComposedLoadout: ActivateComposedLoadoutUseCase,
) {
    operator fun invoke(input: InitializeLoadoutProjectInput): Result<InitializeLoadoutProjectResult, LoadoutError> =
        configureGitignore(input)
            .flatMap { gitignoreConfiguration ->
                createStarterFragment(input).flatMap { starterFragmentCreation ->
                    initializeDefaultLoadoutIfNeeded(input).map { defaultLoadoutInitialization ->
                        InitializeLoadoutProjectResult(
                            gitignoreConfiguration = gitignoreConfiguration,
                            starterFragmentCreation = starterFragmentCreation,
                            defaultLoadoutInitialization = defaultLoadoutInitialization,
                        )
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

                append("# Loadout CLI - ${input.mode.name} Mode\n")
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
                Result.Success(DefaultLoadoutInitializationResult.ExistingLoadoutsPresent)
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
                            ActivateComposedLoadoutInput(
                                composedOutput = composedOutput,
                                outputPaths = input.outputPaths,
                            )
                        ).map { writeResult ->
                            DefaultLoadoutInitializationResult.CreatedAndActivated(
                                composedOutput = composedOutput,
                                writeResult = writeResult,
                            )
                        }
                    }
                }
            }
        }
}
