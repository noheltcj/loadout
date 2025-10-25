package cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.help
import domain.service.LoadoutService
import domain.entity.packaging.Result

class AddCommand(
    private val loadoutService: LoadoutService
) : CliktCommand(
    name = "add",
    help = "Add a fragment to a loadout"
) {
    
    private val fragmentPath by argument(help = "Path to the fragment to add")
    
    private val loadoutName by option("--to")
        .required()
        .help("Name of the loadout to add the fragment to")
    
    private val afterFragment by option("--after")
        .help("Insert the fragment after this existing fragment")
    
    override fun run() {
        when (val result = loadoutService.addFragmentToLoadout(
            loadoutName = loadoutName,
            fragmentPath = fragmentPath,
            afterFragment = afterFragment
        )) {
            is Result.Success -> {
                val updatedLoadout = result.value
                echo("Added fragment '$fragmentPath' to loadout '$loadoutName'")
                echo("Loadout now has ${updatedLoadout.fragments.size} fragments:")
                updatedLoadout.fragments.forEachIndexed { index, fragment ->
                    val marker = if (fragment == fragmentPath) "← NEW" else ""
                    echo("  ${index + 1}. $fragment $marker")
                }
            }
            is Result.Error -> {
                echo("Failed to add fragment: ${result.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}