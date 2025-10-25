package cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.help
import domain.service.LoadoutService
import domain.entity.packaging.Result
import domain.entity.error.LoadoutError
import domain.entity.Loadout

class ListCommand(
    private val loadoutService: LoadoutService
) : CliktCommand(
    name = "list",
    help = "List all available loadouts"
) {

    private val verbose by option("--verbose", "-v")
        .flag(default = false)
        .help("Enable verbose output")
    
    // TODO: Inherit global --config option from main CLI

    override fun run() {
        when (val result = loadoutService.getAllLoadouts()) {
            is Result.Success -> {
                val loadouts = result.value
                
                if (loadouts.isEmpty()) {
                    echo("No loadouts found. Create one with 'loadout create <name>'")
                    return
                }
                
                outputTable(loadouts)
            }
            is Result.Error -> echoError(result.error)
        }
    }
    
    private fun outputTable(loadouts: List<Loadout>) {
        echo("Available loadouts:")
        echo()
        
        loadouts.forEach { loadout ->
            echo("• ${loadout.name}")
            if (loadout.description.isNotBlank()) {
                echo("  ${loadout.description}")
            }
            echo("  Fragments: ${loadout.fragments.size}")
            
            if (verbose) {
                loadout.fragments.forEachIndexed { index, fragment ->
                    echo("    ${index + 1}. $fragment")
                }
            }
            echo()
        }
    }

    private fun echoError(error: LoadoutError) {
        echo("Error: ${error.message}", err = true)
        throw ProgramResult(1)
    }
}
