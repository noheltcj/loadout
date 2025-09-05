package cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.help
import application.LoadoutService
import common.Result

class CreateCommand(
    private val loadoutService: LoadoutService
) : CliktCommand(
    name = "create",
    help = "Create a new loadout"
) {
    
    private val name by argument(help = "Name of the loadout to create")
    
    private val description by option("--desc", "--description")
        .help("Short description of the loadout")
    
    private val fragments by option("--fragment", "-f")
        .multiple()
        .help("Initial fragments to include (can be specified multiple times)")
    
    // TODO: Add --clone option to create by copying an existing loadout
    // TODO: Inherit global --config option from main CLI
    // TODO: Inherit global --dry-run option from main CLI
    // TODO: Inherit global --json option from main CLI
    // TODO: Support --fragments as documented in README (currently --fragment)
    
    override fun run() {
        when (val result = loadoutService.createLoadout(
            name = name,
            description = description.orEmpty(),
            fragments = fragments
        )) {
            is Result.Success -> {
                val loadout = result.value
                echo("Created loadout '${loadout.name}'")
                
                if (loadout.description.isNotBlank()) {
                    echo("Description: ${loadout.description}")
                }
                
                if (loadout.fragments.isNotEmpty()) {
                    echo("Fragments:")
                    loadout.fragments.forEachIndexed { index, fragment ->
                        echo("  ${index + 1}. $fragment")
                    }
                }
                
                echo("\nUse 'loadout use $name' to activate this loadout.")
            }
            is Result.Error -> {
                echo("Failed to create loadout: ${result.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}