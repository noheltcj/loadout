package cli.commands

import cli.Constants
import cli.commands.extension.echoComposedFilesWriteResult
import cli.commands.extension.echoGitIgnoreRulesWarning
import cli.commands.extension.echoRepositorySettingsWarnings
import cli.commands.extension.echoUserSettingsWarnings
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
import domain.usecase.ConfigureGitIgnoreRulesResult
import domain.usecase.DefaultLoadoutInitializationResult
import domain.usecase.GitHookDefinition
import domain.usecase.InitializeLoadoutProjectInput
import domain.usecase.InitializeLoadoutProjectUseCase
import domain.usecase.OutputFilesSource
import domain.usecase.RepositoryDefaultLoadoutOutcome

class InitCommand(
    private val initializeLoadoutProject: InitializeLoadoutProjectUseCase,
) : CliktCommand(
    name = COMMAND_NAME,
) {
    override fun help(context: Context): String =
        "Initialize Loadout in a project by setting up git ignore rules, hooks, and an empty default loadout"

    private val mode by option("--mode", "-m")
        .enum<InitMode>(ignoreCase = true)
        .default(InitMode.SHARED)
        .help("Mode: 'shared' (recommended) for team collaboration, 'local' for local-only configuration")

    override fun run() {
        val input = when (mode) {
            InitMode.SHARED -> InitializeLoadoutProjectInput.Shared(
                defaultLoadoutName = DEFAULT_LOADOUT_NAME,
                defaultLoadoutDescription = DEFAULT_LOADOUT_DESCRIPTION,
                hooksDirectoryPath = Constants.HOOKS_DIR,
                hooks = listOf(
                    GitHookDefinition(POST_CHECKOUT_HOOK_PATH, postCheckoutHookScript()),
                    GitHookDefinition(POST_MERGE_HOOK_PATH, postMergeHookScript())
                )
            )

            InitMode.LOCAL -> InitializeLoadoutProjectInput.Local(
                defaultLoadoutName = DEFAULT_LOADOUT_NAME,
                defaultLoadoutDescription = DEFAULT_LOADOUT_DESCRIPTION,
            )
        }

        val result = initializeLoadoutProject(input)

        result.fold(
            onSuccess = { initResult ->
                echoRepositorySettingsWarnings(initResult.repositorySettingsWarnings)
                echoUserSettingsWarnings(initResult.outputFiles.userSettingsWarnings)

                when (val ignoreRules = initResult.ignoreRulesConfiguration) {
                    is ConfigureGitIgnoreRulesResult.Configured ->
                        echo(
                            "  Configured git to ignore Loadout files in ${ignoreRules.excludeFilePath} " +
                                "(${mode.displayName} mode)"
                        )

                    is ConfigureGitIgnoreRulesResult.AlreadyConfigured ->
                        echo(
                            "  Git already ignores Loadout files in ${ignoreRules.excludeFilePath} " +
                                "(${mode.displayName} mode)"
                        )

                    ConfigureGitIgnoreRulesResult.NotAGitRepository ->
                        echo("  Skipped git ignore rules: not a git repository")

                    is ConfigureGitIgnoreRulesResult.Failed -> echoGitIgnoreRulesWarning(ignoreRules)
                }

                when (initResult.gitHooksConfiguration) {
                    ConfigureGitHooksResult.Configured ->
                        echo("  Configured tracked Git hooks at ${Constants.HOOKS_DIR}")

                    ConfigureGitHooksResult.AlreadyManagedByExternalTool -> {
                        echo("  Git hooks are already managed by an external tool. Skipping hook configuration.")
                        echo(
                            "  To use Loadout-managed tracked hooks instead, point core.hooksPath at ${Constants.HOOKS_DIR}"
                        )
                    }

                    ConfigureGitHooksResult.NotAGitRepository,
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

                if (initResult.outputFiles.source == OutputFilesSource.BuiltInDefault) {
                    echo("Only use some of these agents? Run: loadout config --user --output-files CLAUDE.md")
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
        private const val POST_CHECKOUT_HOOK_PATH = "${Constants.HOOKS_DIR}/post-checkout"
        private const val POST_MERGE_HOOK_PATH = "${Constants.HOOKS_DIR}/post-merge"
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
}
