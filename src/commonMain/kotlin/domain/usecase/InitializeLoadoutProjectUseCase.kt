package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.RepositoryMode
import domain.entity.RepositorySettings
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.RepositorySettingsRepairWarning

sealed interface InitializeLoadoutProjectInput {
    val defaultLoadoutName: String
    val defaultLoadoutDescription: String

    data class Shared(
        override val defaultLoadoutName: String,
        override val defaultLoadoutDescription: String,
        val hooksDirectoryPath: String,
        val hooks: List<GitHookDefinition>,
    ) : InitializeLoadoutProjectInput

    data class Local(
        override val defaultLoadoutName: String,
        override val defaultLoadoutDescription: String,
    ) : InitializeLoadoutProjectInput
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
    val repositorySettingsWarnings: List<RepositorySettingsRepairWarning>,
    val outputFiles: OutputFilesResolution,
    val ignoreRulesConfiguration: ConfigureGitIgnoreRulesResult,
    val defaultLoadoutInitialization: DefaultLoadoutInitializationResult,
    val gitHooksConfiguration: ConfigureGitHooksResult?,
)

/** Init configures ignore rules once: while activating the default loadout it creates, or on their own otherwise. */
private data class LoadoutsInitialization(
    val outputFiles: OutputFilesResolution,
    val defaultLoadoutInitialization: DefaultLoadoutInitializationResult,
    val ignoreRulesConfiguration: ConfigureGitIgnoreRulesResult,
)

class InitializeLoadoutProjectUseCase(
    private val listLoadouts: ListLoadoutsUseCase,
    private val createLoadout: CreateLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
    private val activateComposedLoadout: ActivateComposedLoadoutUseCase,
    private val resolveOutputFiles: ResolveOutputFilesUseCase,
    private val getRepositorySettings: GetRepairableRepositorySettingsUseCase,
    private val updateRepositorySettings: UpdateRepositorySettingsUseCase,
    private val configureGitHooks: ConfigureGitHooksUseCase,
    private val configureGitIgnoreRules: ConfigureGitIgnoreRulesUseCase,
) {
    operator fun invoke(input: InitializeLoadoutProjectInput): Result<InitializeLoadoutProjectResult, LoadoutError> =
        getRepositorySettings().flatMap { repair ->
            initializeDefaultLoadoutIfNeeded(input, repair.settings).flatMap { loadoutsInitialization ->
                configureGitHooksIfNeeded(input).map { gitHooksConfiguration ->
                    InitializeLoadoutProjectResult(
                        repositorySettingsWarnings = repair.warnings,
                        outputFiles = loadoutsInitialization.outputFiles,
                        ignoreRulesConfiguration = loadoutsInitialization.ignoreRulesConfiguration,
                        defaultLoadoutInitialization = loadoutsInitialization.defaultLoadoutInitialization,
                        gitHooksConfiguration = gitHooksConfiguration,
                    )
                }
            }
        }

    private fun initializeDefaultLoadoutIfNeeded(
        input: InitializeLoadoutProjectInput,
        settings: RepositorySettings,
    ): Result<LoadoutsInitialization, LoadoutError> =
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
    ): Result<LoadoutsInitialization, LoadoutError> {
        val repositoryDefault =
            input.repositoryDefaultOutcome(
                configuredDefaultLoadoutName = settings.defaultLoadoutName,
                loadoutNames = loadoutNames,
            )

        return applyRepositoryDefault(repositoryDefault)
            .flatMap { resolveOutputFiles(settings) }
            .map { resolution ->
                LoadoutsInitialization(
                    outputFiles = resolution,
                    defaultLoadoutInitialization = DefaultLoadoutInitializationResult.ExistingLoadoutsPresent(
                        repositoryDefault
                    ),
                    ignoreRulesConfiguration =
                    configureGitIgnoreRules(resolution.outputFiles, input.requestedModeSelection),
                )
            }
    }

    private fun createAndActivateDefaultLoadout(
        input: InitializeLoadoutProjectInput,
    ): Result<LoadoutsInitialization, LoadoutError> =
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
    ): Result<LoadoutsInitialization, LoadoutError> =
        activateComposedLoadout(
            composedOutput = composedOutput,
            modeSelection = input.requestedModeSelection,
        ).flatMap { activation ->
            applyRepositoryDefault(input.createdDefaultLoadoutOutcome())
                .map { activation }
        }.map { activation ->
            LoadoutsInitialization(
                outputFiles = activation.outputFiles,
                defaultLoadoutInitialization = DefaultLoadoutInitializationResult.CreatedAndActivated(
                    composedOutput = composedOutput,
                    writeResult = activation.writeResult,
                ),
                ignoreRulesConfiguration = activation.ignoreRules,
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

private val InitializeLoadoutProjectInput.requestedModeSelection: RepositoryModeSelection
    get() =
        when (this) {
            is InitializeLoadoutProjectInput.Shared -> RepositoryModeSelection.Requested(RepositoryMode.Shared)
            is InitializeLoadoutProjectInput.Local -> RepositoryModeSelection.Requested(RepositoryMode.Local)
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
