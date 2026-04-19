package cli.commands

import cli.Constants
import cli.commands.extension.echoComposedFilesWriteResult
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import domain.entity.packaging.Result
import domain.usecase.DefaultLoadoutInitializationResult
import domain.usecase.GitignoreConfigurationResult
import domain.usecase.InitializeLoadoutProjectInput
import domain.usecase.InitializeLoadoutProjectUseCase
import domain.usecase.LoadoutInitializationMode
import domain.usecase.StarterFragmentCreationResult

class InitCommand(
    private val initializeLoadoutProject: InitializeLoadoutProjectUseCase,
    private val defaultOutputPaths: List<String>,
) : CliktCommand(
        name = COMMAND_NAME,
    ) {
    override fun help(context: Context): String =
        "Initialize Loadout in a project by setting up .gitignore, fragments, and default loadout"

    private val mode by option("--mode", "-m")
        .enum<InitMode>(ignoreCase = true)
        .default(InitMode.SHARED)
        .help("Mode: 'shared' (default) for team collaboration, 'local' for local-only configuration")

    override fun run() {
        val initializationMode =
            when (mode) {
                InitMode.SHARED -> LoadoutInitializationMode.Shared
                InitMode.LOCAL -> LoadoutInitializationMode.Local
            }

        when (
            val result =
                initializeLoadoutProject(
                    InitializeLoadoutProjectInput(
                        mode = initializationMode,
                        gitignorePath = GITIGNORE_PATH,
                        gitignorePatterns = mode.gitignorePatterns,
                        starterFragmentPath = ARCHITECT_FRAGMENT_PATH,
                        starterFragmentContent = ARCHITECT_FRAGMENT_CONTENT,
                        defaultLoadoutName = DEFAULT_LOADOUT_NAME,
                        defaultLoadoutDescription = DEFAULT_LOADOUT_DESCRIPTION,
                        outputPaths = defaultOutputPaths,
                    )
                )
        ) {
            is Result.Success -> {
                when (result.value.gitignoreConfiguration) {
                    GitignoreConfigurationResult.AlreadyConfigured ->
                        echo("  .gitignore already configured for Loadout (${mode.displayName} mode)")

                    GitignoreConfigurationResult.Updated ->
                        echo("  Added Loadout patterns to .gitignore (${mode.displayName} mode)")
                }

                when (result.value.starterFragmentCreation) {
                    StarterFragmentCreationResult.AlreadyExists ->
                        echo("  Starter fragment already exists at $ARCHITECT_FRAGMENT_PATH")

                    StarterFragmentCreationResult.Created ->
                        echo("  Created starter fragment at $ARCHITECT_FRAGMENT_PATH")
                }

                when (val defaultLoadoutInitialization = result.value.defaultLoadoutInitialization) {
                    DefaultLoadoutInitializationResult.ExistingLoadoutsPresent -> {
                        if (result.value.starterFragmentCreation is StarterFragmentCreationResult.Created) {
                            echo("")
                            echo("Existing loadouts found. Link the new fragment with:")
                            echo("  loadout link $ARCHITECT_FRAGMENT_PATH --to <loadout-name>")
                        }
                    }

                    is DefaultLoadoutInitializationResult.CreatedAndActivated -> {
                        echo("  Created '$DEFAULT_LOADOUT_NAME' loadout with starter fragment")
                        echoComposedFilesWriteResult(
                            result = defaultLoadoutInitialization.writeResult,
                            loadoutName = DEFAULT_LOADOUT_NAME,
                            composedContentLength = defaultLoadoutInitialization.composedOutput.content.length
                        )
                        echo("")
                        echo("Loadout initialized successfully!")
                        echo("Note: ${mode.completionNote}")
                    }
                }
            }

            is Result.Error -> {
                echo(result.error.message, err = true)
                throw ProgramResult(1)
            }
        }
    }

    companion object {
        private const val COMMAND_NAME = "init"
        private const val GITIGNORE_PATH = ".gitignore"
        private const val ARCHITECT_FRAGMENT_FILENAME = "loadout-architect.md"
        private const val ARCHITECT_FRAGMENT_PATH = "${Constants.FRAGMENTS_DIR}/$ARCHITECT_FRAGMENT_FILENAME"
        private const val DEFAULT_LOADOUT_NAME = "default"
        private const val DEFAULT_LOADOUT_DESCRIPTION = "Default loadout"

        private val ARCHITECT_FRAGMENT_CONTENT =
            """
            |## Loadout Architect
            |
            |You are working in a project that uses Loadout CLI to manage composable system prompts.
            |
            |### Managing Loadouts
            |
            |Use these commands to manage your loadouts:
            |- `loadout` - Display current loadout status
            |- `loadout list` - List all available loadouts
            |- `loadout use <name>` - Switch to a different loadout
            |- `loadout create <name> --desc "Description"` - Create a new loadout
            |- `loadout remove <name>` - Remove a loadout
            |- `loadout link <fragment-path> --to <loadout>` - Link a fragment into a loadout
            |- `loadout unlink <fragment-path> --from <loadout>` - Unlink a fragment from a loadout
            |- `loadout sync` - Re-compose and synchronize after fragment changes
            |
            |### Fragment Guidelines
            |
            |Fragments are modular markdown files that compose into the final CLAUDE.md, AGENTS.md, and GEMINI.md:
            |- Keep fragments focused on a single concern (coding style, project structure, etc.)
            |- Use clear, concise language that AI agents can follow
            |- Store project-specific fragments in `fragments/`
            |- Store personal/global fragments in `~/.loadout/fragments/`
            |
            |### Best Practices
            |
            |- Create task-specific loadouts (e.g., "refactoring", "testing", "documentation")
            |- Share loadouts with your team by committing `.loadouts/` and `fragments/`
            |- Run `loadout sync` after modifying any fragment content
            """.trimMargin()
    }
}

enum class InitMode(
    val displayName: String,
    val completionNote: String,
) {
    SHARED(
        displayName = "Shared",
        completionNote = "In shared mode, loadout configurations are committed and shared with your team."
    ),
    LOCAL(
        displayName = "Local",
        completionNote = "In local mode, loadout configurations are not shared with your team."
    ),
    ;

    val gitignorePatterns: List<String>
        get() =
            when (this) {
                SHARED ->
                    listOf(GITIGNORE_HEADER, Constants.CONFIG_FILE) + Constants.generatedMarkdownFiles
                LOCAL ->
                    listOf(GITIGNORE_HEADER, Constants.CONFIG_FILE) +
                        Constants.generatedMarkdownFiles +
                        listOf(
                            "",
                            LOCAL_ONLY_GITIGNORE_HEADER,
                            "${Constants.LOADOUTS_DIR}/",
                            "${Constants.FRAGMENTS_DIR}/",
                        )
            }

    companion object {
        private const val GITIGNORE_HEADER = "# Loadout CLI"
        private const val LOCAL_ONLY_GITIGNORE_HEADER = "# Loadout configuration (local-only)"
    }
}
