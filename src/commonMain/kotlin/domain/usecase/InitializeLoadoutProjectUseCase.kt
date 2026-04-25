package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileRepository

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
            fileRepository.readFile(input.gitignorePath).fold(
                onSuccess = { content -> content },
                onError = { "" },
            )

        val missingPatterns = input.gitignorePatterns.missingFrom(existingContent)

        if (missingPatterns.none { it.isNotBlank() }) {
            return Result.Success(GitignoreConfigurationResult.AlreadyConfigured)
        }

        return fileRepository
            .writeFile(
                path = input.gitignorePath,
                content = existingContent.withLoadoutGitignoreBlock(
                    modeName = input.modeName,
                    patterns = missingPatterns,
                ),
            )
            .map { GitignoreConfigurationResult.Updated }
    }

    private fun createStarterFragment(
        input: InitializeLoadoutProjectInput,
    ): Result<StarterFragmentCreationResult, LoadoutError> {
        if (fileRepository.fileExists(input.starterFragmentPath)) {
            return Result.Success(StarterFragmentCreationResult.AlreadyExists)
        }

        return createParentDirectory(input.starterFragmentPath)
            .flatMap {
                fileRepository.writeFile(input.starterFragmentPath, input.starterFragmentContent)
            }
            .map { StarterFragmentCreationResult.Created }
    }

    private fun initializeDefaultLoadoutIfNeeded(
        input: InitializeLoadoutProjectInput,
    ): Result<DefaultLoadoutInitializationResult, LoadoutError> =
        listLoadouts().flatMap { loadouts ->
            loadouts
                .takeIf { it.isNotEmpty() }
                ?.let { existingLoadouts -> initializeExistingLoadouts(input, existingLoadouts.map { it.name }) }
                ?: createAndActivateDefaultLoadout(input)
        }

    private fun createParentDirectory(path: String): Result<Unit, LoadoutError> =
        path.parentDirectory()
            ?.let { directory -> fileRepository.createDirectory(directory) }
            ?: Result.Success(Unit)

    private fun initializeExistingLoadouts(
        input: InitializeLoadoutProjectInput,
        loadoutNames: List<String>,
    ): Result<DefaultLoadoutInitializationResult, LoadoutError> =
        applyRepositoryDefaultConfiguration(input.repositoryDefaultConfiguration(loadoutNames))
            .map { DefaultLoadoutInitializationResult.ExistingLoadoutsPresent(loadoutNames.size) }

    private fun createAndActivateDefaultLoadout(
        input: InitializeLoadoutProjectInput,
    ): Result<DefaultLoadoutInitializationResult, LoadoutError> =
        createLoadout(
            CreateLoadoutInput.New(
                name = input.defaultLoadoutName,
                description = input.defaultLoadoutDescription,
                fragmentPaths = listOf(input.starterFragmentPath),
            )
        ).flatMap { loadout ->
            composeLoadout(loadout).flatMap { composedOutput ->
                activateDefaultLoadout(input, composedOutput)
            }
        }

    private fun activateDefaultLoadout(
        input: InitializeLoadoutProjectInput,
        composedOutput: ComposedOutput,
    ): Result<DefaultLoadoutInitializationResult, LoadoutError> =
        activateComposedLoadout(
            composedOutput = composedOutput,
            outputPaths = input.outputPaths,
        ).flatMap { writeResult ->
            applyRepositoryDefaultConfiguration(input.defaultLoadoutRepositoryConfiguration())
                .map { writeResult }
        }.map { writeResult ->
            DefaultLoadoutInitializationResult.CreatedAndActivated(
                composedOutput = composedOutput,
                writeResult = writeResult,
            )
        }

    private fun applyRepositoryDefaultConfiguration(
        configuration: RepositoryDefaultConfiguration,
    ): Result<Unit, LoadoutError> =
        when (configuration) {
            RepositoryDefaultConfiguration.Unchanged ->
                Result.Success(Unit)

            is RepositoryDefaultConfiguration.UseLoadout ->
                updateRepositorySettings(configuration.loadoutName).map { Unit }

            RepositoryDefaultConfiguration.Clear ->
                updateRepositorySettings(null).map { Unit }
        }

    private fun configureGitHooksIfNeeded(
        input: InitializeLoadoutProjectInput,
    ): Result<ConfigureGitHooksResult?, LoadoutError> =
        when (input) {
            is InitializeLoadoutProjectInput.Shared ->
                configureGitHooks(
                    hooksDirectoryPath = input.hooksDirectoryPath,
                    hooks = input.hooks
                ).map<ConfigureGitHooksResult?> { it }
            is InitializeLoadoutProjectInput.Local ->
                Result.Success(null)
        }
}

private sealed interface RepositoryDefaultConfiguration {
    data object Unchanged : RepositoryDefaultConfiguration

    data class UseLoadout(
        val loadoutName: String,
    ) : RepositoryDefaultConfiguration

    data object Clear : RepositoryDefaultConfiguration
}

private val InitializeLoadoutProjectInput.modeName: String
    get() =
        when (this) {
            is InitializeLoadoutProjectInput.Shared -> "Shared"
            is InitializeLoadoutProjectInput.Local -> "Local"
        }

private fun InitializeLoadoutProjectInput.repositoryDefaultConfiguration(
    loadoutNames: List<String>,
): RepositoryDefaultConfiguration =
    when (this) {
        is InitializeLoadoutProjectInput.Shared ->
            when (loadoutNames.size) {
                0 -> RepositoryDefaultConfiguration.Unchanged
                1 -> RepositoryDefaultConfiguration.UseLoadout(loadoutNames.single())
                else -> RepositoryDefaultConfiguration.Clear
            }

        is InitializeLoadoutProjectInput.Local ->
            RepositoryDefaultConfiguration.Unchanged
    }

private fun InitializeLoadoutProjectInput.defaultLoadoutRepositoryConfiguration(): RepositoryDefaultConfiguration =
    when (this) {
        is InitializeLoadoutProjectInput.Shared ->
            RepositoryDefaultConfiguration.UseLoadout(defaultLoadoutName)

        is InitializeLoadoutProjectInput.Local ->
            RepositoryDefaultConfiguration.Unchanged
    }

private fun List<String>.missingFrom(content: String): List<String> =
    filter { pattern -> pattern.isBlank() || !content.contains(pattern.trim()) }

private fun String.withLoadoutGitignoreBlock(
    modeName: String,
    patterns: List<String>,
): String =
    buildString {
        appendGitignorePrefix(this@withLoadoutGitignoreBlock)
        append("# Loadout CLI - $modeName Mode\n")
        append(patterns.joinToString(separator = "\n", postfix = "\n"))
    }

private fun StringBuilder.appendGitignorePrefix(existingContent: String) {
    if (existingContent.isBlank()) {
        return
    }

    append(existingContent)
    if (!existingContent.endsWith("\n")) {
        append("\n")
    }
    append("\n")
}

private fun String.parentDirectory(): String? =
    substringBeforeLast("/", missingDelimiterValue = "")
        .ifBlank { null }
