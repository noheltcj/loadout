package cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.help
import application.LoadoutService
import common.Result

class RemoveCommand(
    private val loadoutService: LoadoutService
) : CliktCommand(
    name = "remove",
    help = "Remove a fragment from a loadout"
) {
    
    private val fragmentPath by argument(help = "Path to the fragment to remove")
    
    private val loadoutName by option("--from")
        .required()
        .help("Name of the loadout to remove the fragment from")
    
    // TODO: Inherit global --config option from main CLI
    // TODO: Inherit global --dry-run option from main CLI
    // TODO: Inherit global --json option from main CLI
    
    override fun run() {
        when (val result = loadoutService.removeFragmentFromLoadout(
            loadoutName = loadoutName,
            fragmentPath = fragmentPath
        )) {
            is Result.Success -> {
                val updatedLoadout = result.value
                echo("Removed fragment '$fragmentPath' from loadout '$loadoutName'")
                
                if (updatedLoadout.fragments.isEmpty()) {
                    echo("Loadout is now empty.")
                } else {
                    echo("Remaining fragments:")
                    updatedLoadout.fragments.forEachIndexed { index, fragment ->
                        echo("  ${index + 1}. $fragment")
                    }
                }
            }
            is Result.Error -> {
                echo("Failed to remove fragment: ${result.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}