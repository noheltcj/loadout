package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.RepositorySettings
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository

sealed interface InitializeLoadoutProjectInput {
    val gitignorePath: String
    val fixedGitignorePatterns: List<String>
    val defaultLoadoutName: String
    val defaultLoadoutDescription: String
    val outputPaths: List<String>

    data class Shared(
        override val gitignorePath: String,
        override val fixedGitignorePatterns: List<String>,
        override val defaultLoadoutName: String,
        override val defaultLoadoutDescription: String,
        override val outputPaths: List<String>,
        val hooksDirectoryPath: String,
        val hooks: List<GitHookDefinition>,
    ) : InitializeLoadoutProjectInput

    data class Local(
        override val gitignorePath: String,
        override val fixedGitignorePatterns: List<String>,
        override val defaultLoadoutName: String,
        override val defaultLoadoutDescription: String,
        override val outputPaths: List<String>,
    ) : InitializeLoadoutProjectInput
}

sealed interface GitignoreConfigurationResult {
    data object Updated : GitignoreConfigurationResult
    data object AlreadyConfigured : GitignoreConfigurationResult
}

sealed interface RepositoryDefaultLoadoutOutcome {
    data class Preserved(val loadoutName: String) : RepositoryDefaultLoadoutOutcome

    data class Selected(val loadoutName: String) : RepositoryDefaultLoadoutOutcome

    data object ClearedStaleSelection : RepositoryDefaultLoadoutOutcome

    data object Unset : RepositoryDefaultLoadoutOutcome
}

sealed interface DefaultLoadoutInitializationResult {
    data class ExistingLoadoutsPresent(
        val repositoryDefault: RepositoryDefaultLoadoutOutcome,
    ) : DefaultLoadoutInitializationResult

    data class CreatedAndActivated(
        val composedOutput: ComposedOutput,
        val writeResult: WriteComposedFilesResult,
    ) : DefaultLoadoutInitializationResult
}

data class InitializeLoadoutProjectResult(
    val gitignoreConfiguration: GitignoreConfigurationResult,
    val defaultLoadoutInitialization: DefaultLoadoutInitializationResult,
    val gitHooksConfiguration: ConfigureGitHooksResult?,
)

class InitializeLoadoutProjectUseCase(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val listLoadouts: ListLoadoutsUseCase,
    private val createLoadout: CreateLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
    private val activateComposedLoadout: ActivateComposedLoadoutUseCase,
    private val getRepositorySettings: GetRepositorySettingsUseCase,
    private val updateRepositorySettings: UpdateRepositorySettingsUseCase,
    private val configureGitHooks: ConfigureGitHooksUseCase,
) {
    operator fun invoke(input: InitializeLoadoutProjectInput): Result<InitializeLoadoutProjectResult, LoadoutError> =
        getRepositorySettings().flatMap { settings ->
            configureGitignore(input, settings).flatMap { gitignoreConfiguration ->
                initializeDefaultLoadoutIfNeeded(input, settings).flatMap { defaultLoadoutInitialization ->
                    configureGitHooksIfNeeded(input).map { gitHooksConfiguration ->
                        InitializeLoadoutProjectResult(
                            gitignoreConfiguration = gitignoreConfiguration,
                            defaultLoadoutInitialization = defaultLoadoutInitialization,
                            gitHooksConfiguration = gitHooksConfiguration,
                        )
                    }
                }
            }
        }

    private fun configureGitignore(
        input: InitializeLoadoutProjectInput,
        settings: RepositorySettings,
    ): Result<GitignoreConfigurationResult, LoadoutError> {
        val existingContent =
            fileQueryRepository.readFile(input.gitignorePath).fold(
                onSuccess = { content -> content },
                onError = { "" },
            )

        val missingPatterns = input.gitignorePatterns(settings).missingFrom(existingContent)

        if (missingPatterns.none { it.isNotBlank() }) {
            return Result.Success(GitignoreConfigurationResult.AlreadyConfigured)
        }

        return fileMutationRepository
            .writeFile(
                path = input.gitignorePath,
                content = existingContent.withLoadoutGitignoreBlock(
                    modeName = input.modeName,
                    patterns = missingPatterns,
                ),
            )
            .map { GitignoreConfigurationResult.Updated }
    }

    private fun initializeDefaultLoadoutIfNeeded(
        input: InitializeLoadoutProjectInput,
        settings: RepositorySettings,
    ): Result<DefaultLoadoutInitializationResult, LoadoutError> =
        listLoadouts().flatMap { loadouts ->
            loadouts
                .takeIf { it.isNotEmpty() }
                ?.let { existingLoadouts ->
                    initializeExistingLoadouts(input, settings, existingLoadouts.map { it.name })
                }
                ?: createAndActivateDefaultLoadout(input)
        }

    private fun initializeExistingLoadouts(
        input: InitializeLoadoutProjectInput,
        settings: RepositorySettings,
        loadoutNames: List<String>,
    ): Result<DefaultLoadoutInitializationResult, LoadoutError> {
        val repositoryDefault =
            input.repositoryDefaultOutcome(
                configuredDefaultLoadoutName = settings.defaultLoadoutName,
                loadoutNames = loadoutNames,
            )

        return applyRepositoryDefault(repositoryDefault).map {
            DefaultLoadoutInitializationResult.ExistingLoadoutsPresent(repositoryDefault)
        }
    }

    private fun createAndActivateDefaultLoadout(
        input: InitializeLoadoutProjectInput,
    ): Result<DefaultLoadoutInitializationResult, LoadoutError> =
        createLoadout(
            CreateLoadoutInput.New(
                name = input.defaultLoadoutName,
                description = input.defaultLoadoutDescription,
                fragmentPaths = emptyList(),
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
            applyRepositoryDefault(input.createdDefaultLoadoutOutcome())
                .map { writeResult }
        }.map { writeResult ->
            DefaultLoadoutInitializationResult.CreatedAndActivated(
                composedOutput = composedOutput,
                writeResult = writeResult,
            )
        }

    private fun applyRepositoryDefault(outcome: RepositoryDefaultLoadoutOutcome): Result<Unit, LoadoutError> =
        when (outcome) {
            is RepositoryDefaultLoadoutOutcome.Preserved,
            RepositoryDefaultLoadoutOutcome.Unset,
            -> Result.Success(Unit)

            is RepositoryDefaultLoadoutOutcome.Selected ->
                updateRepositorySettings(RepositorySettingsUpdateInput.DefaultLoadout(outcome.loadoutName))
                    .map { Unit }

            RepositoryDefaultLoadoutOutcome.ClearedStaleSelection ->
                updateRepositorySettings(RepositorySettingsUpdateInput.DefaultLoadout(null)).map { Unit }
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

private fun InitializeLoadoutProjectInput.gitignorePatterns(settings: RepositorySettings): List<String> =
    when (this) {
        is InitializeLoadoutProjectInput.Shared -> fixedGitignorePatterns
        is InitializeLoadoutProjectInput.Local -> fixedGitignorePatterns + "${settings.fragmentsHome}/"
    }

private val InitializeLoadoutProjectInput.modeName: String
    get() =
        when (this) {
            is InitializeLoadoutProjectInput.Shared -> "Shared"
            is InitializeLoadoutProjectInput.Local -> "Local"
        }

/**
 * Decides what happens to the repository default loadout when init runs against existing loadouts.
 *
 * An explicitly configured default that still resolves is always preserved: re-running init must never
 * discard a selection the user made with `loadout config --default-loadout`.
 */
private fun InitializeLoadoutProjectInput.repositoryDefaultOutcome(
    configuredDefaultLoadoutName: String?,
    loadoutNames: List<String>,
): RepositoryDefaultLoadoutOutcome =
    when {
        configuredDefaultLoadoutName != null && configuredDefaultLoadoutName in loadoutNames ->
            RepositoryDefaultLoadoutOutcome.Preserved(configuredDefaultLoadoutName)

        // Local mode keeps repository settings untracked, so init never writes them.
        this is InitializeLoadoutProjectInput.Local ->
            RepositoryDefaultLoadoutOutcome.Unset

        loadoutNames.size == 1 ->
            RepositoryDefaultLoadoutOutcome.Selected(loadoutNames.single())

        configuredDefaultLoadoutName != null ->
            RepositoryDefaultLoadoutOutcome.ClearedStaleSelection

        else -> RepositoryDefaultLoadoutOutcome.Unset
    }

private fun InitializeLoadoutProjectInput.createdDefaultLoadoutOutcome(): RepositoryDefaultLoadoutOutcome =
    when (this) {
        is InitializeLoadoutProjectInput.Shared ->
            RepositoryDefaultLoadoutOutcome.Selected(defaultLoadoutName)

        is InitializeLoadoutProjectInput.Local ->
            RepositoryDefaultLoadoutOutcome.Unset
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
