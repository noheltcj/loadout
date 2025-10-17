package cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.*
import application.LoadoutService
import application.CompositionEngine
import infrastructure.*
import domain.LoadoutError
import common.Result

class LoadoutCli(
    private val loadoutService: LoadoutService,
    private val compositionEngine: CompositionEngine
) : CliktCommand(
    name = "loadout",
    help = "Composable, shareable .md system prompts for agentic AI coding systems",
    invokeWithoutSubcommand = true
) {
    
    private val configPath by option("--config", "-c")
        .help("Path to configuration file")
    
    private val outputDir by option("--output", "-o")
        .help("Override output directory for generated files")
    
    private val dryRun by option("--dry-run")
        .flag(default = false)
        .help("Preview actions without making changes")
    
    private val verbose by option("--verbose", "-v")
        .flag(default = false)
        .help("Enable verbose output")
    
    private val json by option("--json")
        .flag(default = false)
        .help("Output machine-readable JSON")
    
    // TODO: Add --version option as documented in README
    // TODO: Finish implementing the options above

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
                    
                    when (val composeResult = compositionEngine.composeLoadout(currentLoadout)) {
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