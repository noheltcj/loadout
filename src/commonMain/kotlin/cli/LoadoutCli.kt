package cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.entity.error.LoadoutError
import domain.usecase.CheckLoadoutSyncUseCase
import domain.usecase.CurrentLoadoutStatus
import domain.usecase.FragmentsSyncState
import domain.usecase.LoadoutSyncState
import domain.usecase.OutputFilesSyncState
import domain.usecase.ReadCurrentLoadoutStatusUseCase

class LoadoutCli(
    private val readCurrentLoadoutStatus: ReadCurrentLoadoutStatusUseCase,
    private val checkLoadoutSync: CheckLoadoutSyncUseCase,
) : CliktCommand(name = "loadout") {
    override val invokeWithoutSubcommand: Boolean = true

    override fun help(context: Context): String =
        "Composable, shareable .md system prompts for agentic AI coding systems"

    private val verbose by option("--verbose", "-v")
        .flag(default = false)
        .help("Enable verbose output")

    /* TODO: Add --version option as documented in README */

    override fun run() {
        currentContext.callOnClose { warnIfNotSynchronized() }

        if (currentContext.invokedSubcommand == null) {
            readCurrentLoadoutStatus().fold(
                onSuccess = { status ->
                    when (status) {
                        CurrentLoadoutStatus.NoCurrentLoadout -> {
                            echo("No current loadout set. Use 'loadout list' to see available loadouts.")
                            return
                        }

                        is CurrentLoadoutStatus.Active -> {
                            val currentLoadout = status.loadout
                            echo("Current loadout: ${currentLoadout.name}")
                            if (currentLoadout.description.isNotBlank()) {
                                echo("Description: ${currentLoadout.description}")
                            }
                            echo("Fragments: ${currentLoadout.fragments.size}")

                            if (verbose) {
                                currentLoadout.fragments.forEachIndexed { index, fragment ->
                                    echo("  ${index + 1}. $fragment")
                                }
                            }

                            echo("\nComposed output: ${status.composedOutput.content.length} characters")
                        }
                    }
                },
                onError = { error ->
                    echoError(error, verbose)
                    throw ProgramResult(1)
                },
            )
        }
    }

    private fun warnIfNotSynchronized() {
        val subcommandName = currentContext.invokedSubcommand?.name
        if (subcommandName in setOf("config", "list")) return

        checkLoadoutSync().fold(
            onSuccess = { syncState ->
                when (syncState) {
                    LoadoutSyncState.NoCurrentLoadout -> Unit
                    is LoadoutSyncState.UnreadableLocalState -> echoUnreadableLocalStateWarning(syncState)
                    is LoadoutSyncState.Active -> echoPendingSyncWarnings(syncState)
                }
            },
            onError = { error ->
                when (error) {
                    is LoadoutError.FragmentNotFound -> echoError(error, verbose)
                    is LoadoutError.FragmentNotInLoadout -> echoError(error, verbose)
                    is LoadoutError.FragmentAlreadyInLoadout -> echoError(error, verbose)
                    is LoadoutError.LoadoutNotFound -> return
                    is LoadoutError.ConfigurationError,
                    is LoadoutError.RepairableRepositorySettingsError,
                    is LoadoutError.UnreadableLocalState,
                    is LoadoutError.UnreadableUserSettings,
                    is LoadoutError.UserConfigDirectoryNotFound,
                    is LoadoutError.FileSystemError,
                    is LoadoutError.InvalidFragment,
                    is LoadoutError.InvalidFragmentSource,
                    is LoadoutError.UntrustedFragmentSourceHost,
                    is LoadoutError.FragmentCopyTooLarge,
                    is LoadoutError.FragmentDestinationAlreadyExists,
                    is LoadoutError.LoadoutAlreadyExists,
                    is LoadoutError.SerializationError,
                    is LoadoutError.ValidationError,
                    -> {
                        echoError(error, verbose)
                        throw ProgramResult(1)
                    }
                }
            },
        )
    }

    private fun echoUnreadableLocalStateWarning(syncState: LoadoutSyncState.UnreadableLocalState) {
        echo("", err = true)
        echo("Warning: ${syncState.error.message}, so this worktree has no current loadout.", err = true)
        echo("Run 'loadout use <name>' to choose one.", err = true)
        syncState.error.cause
            ?.takeIf { verbose }
            ?.let { cause -> echo("Cause: ${cause.message}", err = true) }
    }

    private fun echoPendingSyncWarnings(syncState: LoadoutSyncState.Active) {
        val fragmentsChanged = syncState.fragments == FragmentsSyncState.ChangedSinceLastComposition
        val outputFilesChange = syncState.outputFiles as? OutputFilesSyncState.ChangedSinceLastWrite
        if (!fragmentsChanged && outputFilesChange == null) return

        echo("", err = true)
        if (fragmentsChanged) {
            echo("Warning: Current loadout fragments have changed since the last composition.", err = true)
        }
        outputFilesChange?.let { change ->
            echo("Warning: Your output files changed since the last sync (${change.describe()}).", err = true)
        }
        if (fragmentsChanged) {
            echo("To synchronize, run 'loadout sync' and restart your agent.", err = true)
        } else {
            echo("Run 'loadout sync' to apply.", err = true)
        }
    }
}

private fun OutputFilesSyncState.ChangedSinceLastWrite.describe(): String =
    listOfNotNull(
        addedOutputFiles.takeIf { it.isNotEmpty() }?.joinToString(prefix = "adds "),
        removedOutputFiles.takeIf { it.isNotEmpty() }?.joinToString(prefix = "removes "),
    ).joinToString("; ")
