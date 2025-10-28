package cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.*
import domain.service.LoadoutService
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.service.LoadoutCompositionService
import domain.usecase.CheckLoadoutSyncUseCase
import platform.posix.exit
import kotlin.system.exitProcess

class LoadoutCli(
    private val loadoutService: LoadoutService,
    private val composeLoadout: LoadoutCompositionService,
    private val checkLoadoutSync: CheckLoadoutSyncUseCase
) : CliktCommand(
    name = "loadout",
    help = "Composable, shareable .md system prompts for agentic AI coding systems",
    invokeWithoutSubcommand = true,
) {
    private val verbose by option("--verbose", "-v")
        .flag(default = false)
        .help("Enable verbose output")

    // TODO: Add --version option as documented in README

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            when (val result = loadoutService.getCurrentLoadout()) {
                is Result.Success -> {
                    val currentLoadout = result.value
                    if (currentLoadout != null) {
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

                        when (val composeResult = composeLoadout(currentLoadout)) {
                            is Result.Success -> {
                                echo("\nComposed output: ${composeResult.value.content.length} characters")
                            }
                            is Result.Error -> {
                                echoError(composeResult.error)
                                throw ProgramResult(1)
                            }
                        }
                    } else {
                        echo("No current loadout set. Use 'loadout list' to see available loadouts.")
                        exitProcess(0)
                    }
                }
                is Result.Error -> {
                    echoError(result.error)
                    throw ProgramResult(1)
                }
            }
        }

        warnIfNotSynchronized()
    }

    private fun warnIfNotSynchronized() {
        when (val syncResult = checkLoadoutSync()) {
            is Result.Success -> {
                if (!syncResult.value) {
                    echo("\nWarning: Current loadout fragments have changed since the last composition.", err = true)
                    echo("To synchronize, run 'loadout sync' and restart your agent.", err = true)
                }
            }
            is Result.Error -> {
                echoError(syncResult.error)
                throw ProgramResult(1)
            }
        }
    }

    private fun echoError(error: LoadoutError) {
        val cause = error.cause
        echo(error.message, err = true)
        if (verbose && cause != null) {
            echo("Cause: ${cause.message}", err = true)
        }
    }
}