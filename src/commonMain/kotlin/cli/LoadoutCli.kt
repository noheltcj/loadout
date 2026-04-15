package cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.usecase.CheckLoadoutSyncUseCase
import domain.usecase.CurrentLoadoutStatus
import domain.usecase.LoadoutSyncState
import domain.usecase.ReadCurrentLoadoutStatusUseCase

class LoadoutCli(
    private val readCurrentLoadoutStatus: ReadCurrentLoadoutStatusUseCase,
    private val checkLoadoutSync: CheckLoadoutSyncUseCase,
) : CliktCommand(
        name = "loadout",
    ) {
    override fun help(context: Context): String =
        "Composable, shareable .md system prompts for agentic AI coding systems"

    override val invokeWithoutSubcommand: Boolean = true

    private val verbose by option("--verbose", "-v")
        .flag(default = false)
        .help("Enable verbose output")

    // TODO: Add --version option as documented in README

    override fun run() {
        currentContext.callOnClose { warnIfNotSynchronized() }

        if (currentContext.invokedSubcommand == null) {
            when (val result = readCurrentLoadoutStatus()) {
                is Result.Success -> {
                    when (val status = result.value) {
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
                }
                is Result.Error -> {
                    echoError(result.error, verbose)
                    throw ProgramResult(1)
                }
            }
        }
    }

    private fun warnIfNotSynchronized() {
        when (val syncResult = checkLoadoutSync()) {
            is Result.Success -> {
                when (syncResult.value) {
                    LoadoutSyncState.OutOfSync -> {
                        echo("\nWarning: Current loadout fragments have changed since the last composition.", err = true)
                        echo("To synchronize, run 'loadout sync' and restart your agent.", err = true)
                    }

                    LoadoutSyncState.NoCurrentLoadout,
                    LoadoutSyncState.Synchronized,
                    -> Unit
                }
            }
            is Result.Error -> {
                when (syncResult.error) {
                    is LoadoutError.FragmentNotFound -> echoError(syncResult.error, verbose)
                    is LoadoutError.FragmentNotInLoadout -> echoError(syncResult.error, verbose)
                    is LoadoutError.FragmentAlreadyInLoadout -> echoError(syncResult.error, verbose)
                    is LoadoutError.LoadoutNotFound -> return
                    is LoadoutError.ConfigurationError,
                    is LoadoutError.FileSystemError,
                    is LoadoutError.InvalidFragment,
                    is LoadoutError.LoadoutAlreadyExists,
                    is LoadoutError.SerializationError,
                    is LoadoutError.ValidationError,
                    -> {
                        echoError(syncResult.error, verbose)
                        throw ProgramResult(1)
                    }
                }
            }
        }
    }
}
