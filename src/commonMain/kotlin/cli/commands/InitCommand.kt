package cli.commands

import cli.Constants
import cli.commands.extension.echoComposedFilesWriteResult
import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import domain.entity.error.LoadoutError
import domain.usecase.ConfigureGitHooksResult
import domain.usecase.DefaultLoadoutInitializationResult
import domain.usecase.GitHookDefinition
import domain.usecase.GitignoreConfigurationResult
import domain.usecase.InitializeLoadoutProjectInput
import domain.usecase.InitializeLoadoutProjectUseCase
import domain.usecase.RepositoryDefaultLoadoutOutcome

class InitCommand(
    private val initializeLoadoutProject: InitializeLoadoutProjectUseCase,
    private val defaultOutputPaths: List<String>,
) : CliktCommand(
    name = COMMAND_NAME,
) {
    override fun help(context: Context): String =
        "Initialize Loadout in a project by setting up .gitignore, hooks, and an empty default loadout"

    private val mode by option("--mode", "-m")
        .enum<InitMode>(ignoreCase = true)
        .default(InitMode.SHARED)
        .help("Mode: 'shared' (recommended) for team collaboration, 'local' for local-only configuration")

    override fun run() {
        val input = when (mode) {
            InitMode.SHARED -> InitializeLoadoutProjectInput.Shared(
                gitignorePath = GITIGNORE_PATH,
                fixedGitignorePatterns = mode.fixedGitignorePatterns,
                defaultLoadoutName = DEFAULT_LOADOUT_NAME,
                defaultLoadoutDescription = DEFAULT_LOADOUT_DESCRIPTION,
                outputPaths = defaultOutputPaths,
                hooksDirectoryPath = HOOKS_DIRECTORY_PATH,
                hooks = listOf(
                    GitHookDefinition(POST_CHECKOUT_HOOK_PATH, postCheckoutHookScript()),
                    GitHookDefinition(POST_MERGE_HOOK_PATH, postMergeHookScript())
                )
            )

            InitMode.LOCAL -> InitializeLoadoutProjectInput.Local(
                gitignorePath = GITIGNORE_PATH,
                fixedGitignorePatterns = mode.fixedGitignorePatterns,
                defaultLoadoutName = DEFAULT_LOADOUT_NAME,
                defaultLoadoutDescription = DEFAULT_LOADOUT_DESCRIPTION,
                outputPaths = defaultOutputPaths
            )
        }

        val result = initializeLoadoutProject(input)

        result.fold(
            onSuccess = { initResult ->
                when (initResult.gitignoreConfiguration) {
                    GitignoreConfigurationResult.AlreadyConfigured ->
                        echo("  .gitignore already configured for Loadout (${mode.displayName} mode)")

                    GitignoreConfigurationResult.Updated ->
                        echo("  Added Loadout patterns to .gitignore (${mode.displayName} mode)")
                }

                when (initResult.gitHooksConfiguration) {
                    ConfigureGitHooksResult.Configured ->
                        echo("  Configured tracked Git hooks at $HOOKS_DIRECTORY_PATH")

                    ConfigureGitHooksResult.AlreadyManagedByExternalTool -> {
                        echo("  Git hooks are already managed by an external tool. Skipping hook configuration.")
                        echo(
                            "  To use Loadout-managed tracked hooks instead, point core.hooksPath at $HOOKS_DIRECTORY_PATH"
                        )
                    }

                    ConfigureGitHooksResult.GitNotInitialized,
                    null,
                    -> Unit
                }

                when (val defaultLoadoutInitialization = initResult.defaultLoadoutInitialization) {
                    is DefaultLoadoutInitializationResult.ExistingLoadoutsPresent ->
                        echoRepositoryDefaultOutcome(defaultLoadoutInitialization.repositoryDefault)

                    is DefaultLoadoutInitializationResult.CreatedAndActivated -> {
                        echo("  Created empty '$DEFAULT_LOADOUT_NAME' loadout")
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
            },
            onError = ::fail,
        )
    }

    private fun echoRepositoryDefaultOutcome(outcome: RepositoryDefaultLoadoutOutcome) {
        if (mode != InitMode.SHARED) {
            return
        }

        when (outcome) {
            is RepositoryDefaultLoadoutOutcome.Preserved ->
                echo("  Kept the repository default loadout '${outcome.loadoutName}'")

            is RepositoryDefaultLoadoutOutcome.Selected ->
                echo("  Set the repository default loadout to '${outcome.loadoutName}'")

            RepositoryDefaultLoadoutOutcome.ClearedStaleSelection -> {
                echo("  Cleared the repository default loadout because it no longer exists")
                echoRepositoryDefaultGuidance()
            }

            RepositoryDefaultLoadoutOutcome.Unset -> echoRepositoryDefaultGuidance()
        }
    }

    private fun echoRepositoryDefaultGuidance() {
        echo("Set the repository default loadout with:")
        echo("  loadout config --default-loadout <name>")
    }

    private fun fail(error: LoadoutError) {
        echoError(error)
        throw ProgramResult(1)
    }

    private fun postCheckoutHookScript(): String =
        $$"""
        #!/bin/sh
        # Loadout Auto-Sync Hook (post-checkout)
        # Triggered on branch changes or worktree creation

        if [ "$3" = "0" ]; then
          exit 0
        fi

        if [ -n "$LOADOUT_BIN" ]; then
            helper_path="$LOADOUT_BIN"
        else
            helper_path="loadout"
            if ! command -v "$helper_path" >/dev/null 2>&1; then
                # Common installation paths for various environments
                for p in \
                    "/opt/homebrew/bin/loadout" \
                    "/usr/local/bin/loadout" \
                    "$HOME/.local/bin/loadout" \
                    "$HOME/bin/loadout" \
                    "/usr/bin/loadout"
                do
                    if [ -x "$p" ]; then
                        helper_path="$p"
                        break
                    fi
                done
            fi
        fi

        if command -v "$helper_path" >/dev/null 2>&1; then
            # Run sync. If it fails, report to stderr but don't block the git operation.
            if ! "$helper_path" sync --auto >/dev/null; then
                echo "[Loadout] Auto-sync failed. Run 'loadout sync' manually to see errors." >&2
            fi
        else
            echo "[Loadout] CLI not found in PATH or common locations. Skipping auto-sync." >&2
            echo "[Loadout] To fix, ensure 'loadout' is in your PATH or set LOADOUT_BIN." >&2
        fi
        exit 0
        """.trimIndent() + "\n"

    private fun postMergeHookScript(): String =
        $$"""
        #!/bin/sh
        # Loadout Auto-Sync Hook (post-merge)
        # Triggered after successful git pull or merge

        if [ -n "$LOADOUT_BIN" ]; then
            helper_path="$LOADOUT_BIN"
        else
            helper_path="loadout"
            if ! command -v "$helper_path" >/dev/null 2>&1; then
                # Common installation paths for various environments
                for p in \
                    "/opt/homebrew/bin/loadout" \
                    "/usr/local/bin/loadout" \
                    "$HOME/.local/bin/loadout" \
                    "$HOME/bin/loadout" \
                    "/usr/bin/loadout"
                do
                    if [ -x "$p" ]; then
                        helper_path="$p"
                        break
                    fi
                done
            fi
        fi

        if command -v "$helper_path" >/dev/null 2>&1; then
            # Run sync. If it fails, report to stderr but don't block the git operation.
            if ! "$helper_path" sync --auto >/dev/null; then
                echo "[Loadout] Auto-sync failed. Run 'loadout sync' manually to see errors." >&2
            fi
        else
            echo "[Loadout] CLI not found in PATH or common locations. Skipping auto-sync." >&2
            echo "[Loadout] To fix, ensure 'loadout' is in your PATH or set LOADOUT_BIN." >&2
        fi
        exit 0
        """.trimIndent() + "\n"

    companion object {
        private const val COMMAND_NAME = "init"
        private const val GITIGNORE_PATH = ".gitignore"
        private const val HOOKS_DIRECTORY_PATH = ".githooks"
        private const val POST_CHECKOUT_HOOK_PATH = "$HOOKS_DIRECTORY_PATH/post-checkout"
        private const val POST_MERGE_HOOK_PATH = "$HOOKS_DIRECTORY_PATH/post-merge"
        private const val DEFAULT_LOADOUT_NAME = "default"
        private const val DEFAULT_LOADOUT_DESCRIPTION = "Default loadout"
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

    val fixedGitignorePatterns: List<String>
        get() = when (this) {
            SHARED ->
                listOf(GITIGNORE_HEADER, Constants.LOCAL_LOADOUT_STATE_FILE) +
                    Constants.generatedMarkdownFiles
            LOCAL ->
                listOf(GITIGNORE_HEADER, Constants.LOCAL_LOADOUT_STATE_FILE) +
                    Constants.generatedMarkdownFiles +
                    listOf(
                        "",
                        REPOSITORY_SETTINGS_HEADER,
                        Constants.REPOSITORY_SETTINGS_FILE,
                        "",
                        LOCAL_ONLY_GITIGNORE_HEADER,
                        "${Constants.LOADOUTS_DIR}/",
                    )
        }

    companion object {
        private const val GITIGNORE_HEADER = "# Loadout local runtime state"
        private const val REPOSITORY_SETTINGS_HEADER = "# Loadout repository settings"
        private const val LOCAL_ONLY_GITIGNORE_HEADER = "# Loadout local-only definitions"
    }
}
