package cli.commands

import cli.Constants
import cli.commands.extension.echoComposedFilesWriteResult
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import domain.entity.packaging.Result
import domain.repository.FileRepository
import domain.service.LoadoutCompositionService
import domain.service.LoadoutService

class InitCommand(
    private val fileRepository: FileRepository,
    private val loadoutService: LoadoutService,
    private val composeLoadout: LoadoutCompositionService,
    private val defaultOutputPaths: List<String>,
) : CliktCommand(
        name = COMMAND_NAME,
        help = "Initialize Loadout in a project by setting up .gitignore, fragments, and default loadout"
    ) {
    private val mode by option("--mode", "-m")
        .enum<InitMode>(ignoreCase = true)
        .default(InitMode.SHARED)
        .help("Mode: 'shared' (default) for team collaboration, 'local' for local-only configuration")

    override fun run() {
        setupGitignore()
        val fragmentCreated = createStarterFragment()
        setupDefaultLoadoutIfNeeded(fragmentCreated)
    }

    private fun setupGitignore() {
        val existingContent =
            when (val result = fileRepository.readFile(GITIGNORE_PATH)) {
                is Result.Success -> result.value
                is Result.Error -> ""
            }

        val patterns = mode.gitignorePatterns
        val newPatterns =
            patterns.filter { pattern ->
                pattern.isBlank() || !existingContent.contains(pattern.trim())
            }

        if (newPatterns.all { it.isBlank() || existingContent.contains(it.trim()) }) {
            echo("  .gitignore already configured for Loadout (${mode.displayName} mode)")
            return
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

                append("# Loadout CLI - ${mode.displayName} Mode\n")
                newPatterns.forEach { pattern ->
                    append(pattern)
                    append("\n")
                }
            }

        when (val writeResult = fileRepository.writeFile(GITIGNORE_PATH, updatedContent)) {
            is Result.Success -> {
                echo("  Added Loadout patterns to .gitignore (${mode.displayName} mode)")
            }
            is Result.Error -> {
                echo("Failed to write .gitignore: ${writeResult.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }

    private fun createStarterFragment(): Boolean {
        if (fileRepository.fileExists(ARCHITECT_FRAGMENT_PATH)) {
            echo("  Starter fragment already exists at $ARCHITECT_FRAGMENT_PATH")
            return false
        }

        when (val result = fileRepository.createDirectory(Constants.FRAGMENTS_DIR)) {
            is Result.Success -> { /* Directory created or already exists */ }
            is Result.Error -> {
                echo("Failed to create fragments directory: ${result.error.message}", err = true)
                throw ProgramResult(1)
            }
        }

        when (val result = fileRepository.writeFile(ARCHITECT_FRAGMENT_PATH, ARCHITECT_FRAGMENT_CONTENT)) {
            is Result.Success -> {
                echo("  Created starter fragment at $ARCHITECT_FRAGMENT_PATH")
                return true
            }
            is Result.Error -> {
                echo("Failed to create starter fragment: ${result.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }

    private fun setupDefaultLoadoutIfNeeded(fragmentCreated: Boolean) {
        val loadouts =
            when (val result = loadoutService.getAllLoadouts()) {
                is Result.Success -> result.value
                is Result.Error -> {
                    echo("Failed to check existing loadouts: ${result.error.message}", err = true)
                    throw ProgramResult(1)
                }
            }

        if (loadouts.isNotEmpty()) {
            if (fragmentCreated) {
                echo("")
                echo("Existing loadouts found. Add the new fragment with:")
                echo("  loadout add $ARCHITECT_FRAGMENT_PATH --to <loadout-name>")
            }
            return
        }

        when (
            val createResult =
                loadoutService.createLoadout(
                    name = DEFAULT_LOADOUT_NAME,
                    description = DEFAULT_LOADOUT_DESCRIPTION,
                    fragments = listOf(ARCHITECT_FRAGMENT_PATH)
                )
        ) {
            is Result.Success -> {
                val loadout = createResult.value
                echo("  Created '$DEFAULT_LOADOUT_NAME' loadout with starter fragment")

                when (val composeResult = composeLoadout(loadout)) {
                    is Result.Success -> {
                        val composedOutput = composeResult.value
                        when (
                            val setResult =
                                loadoutService.setCurrentLoadout(
                                    composedOutput,
                                    defaultOutputPaths
                                )
                        ) {
                            is Result.Success -> {
                                echoComposedFilesWriteResult(
                                    result = setResult.value,
                                    loadoutName = DEFAULT_LOADOUT_NAME,
                                    composedContentLength = composedOutput.content.length
                                )
                            }
                            is Result.Error -> {
                                echo("Failed to activate loadout: ${setResult.error.message}", err = true)
                                throw ProgramResult(1)
                            }
                        }
                    }
                    is Result.Error -> {
                        echo("Failed to compose loadout: ${composeResult.error.message}", err = true)
                        throw ProgramResult(1)
                    }
                }
            }
            is Result.Error -> {
                echo("Failed to create default loadout: ${createResult.error.message}", err = true)
                throw ProgramResult(1)
            }
        }

        echo("")
        echo("Loadout initialized successfully!")
        echo("Note: ${mode.completionNote}")
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
            |- `loadout add <fragment-path> --to <loadout>` - Add a fragment to a loadout
            |- `loadout remove <fragment-path> --from <loadout>` - Remove a fragment
            |- `loadout sync` - Re-compose and synchronize after fragment changes
            |
            |### Fragment Guidelines
            |
            |Fragments are modular markdown files that compose into the final CLAUDE.md/AGENTS.md:
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
                    listOf(
                        GITIGNORE_HEADER,
                        Constants.CONFIG_FILE,
                        Constants.CLAUDE_MD,
                        Constants.AGENTS_MD,
                    )
                LOCAL ->
                    listOf(
                        GITIGNORE_HEADER,
                        Constants.CONFIG_FILE,
                        Constants.CLAUDE_MD,
                        Constants.AGENTS_MD,
                        "",
                        "# Loadout configuration (local-only)",
                        "${Constants.LOADOUTS_DIR}/",
                        "${Constants.FRAGMENTS_DIR}/",
                    )
            }

    companion object {
        private const val GITIGNORE_HEADER = "# Loadout CLI"
    }
}
