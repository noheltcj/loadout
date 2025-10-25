package cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.*
import domain.service.LoadoutService
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.service.LoadoutCompositionService

class LoadoutCli(
    private val loadoutService: LoadoutService,
    private val composeLoadout: LoadoutCompositionService
) : CliktCommand(
    name = "loadout",
    help = "Composable, shareable .md system prompts for agentic AI coding systems",
    invokeWithoutSubcommand = true
) {
    private val verbose by option("--verbose", "-v")
        .flag(default = false)
        .help("Enable verbose output")

    // TODO: Add --version option as documented in README

    override fun run() {
        if (currentContext.invokedSubcommand != null) return
        
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
                            echo("Warning: ${composeResult.error.message}", err = true)
                        }
                    }
                } else {
                    echo("No current loadout set. Use 'loadout list' to see available loadouts.")
                }
            }
            is Result.Error -> {
                handleError(result.error)
            }
        }
    }

    private fun handleError(error: LoadoutError) {
        echo("Error: ${error.message}", err = true)
        if (verbose && error.cause != null) {
            echo("Cause: ${error.cause!!.message}", err = true)
        }
        throw ProgramResult(1)
    }
}