package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import domain.entity.packaging.Result
import domain.service.LoadoutService

class RemoveCommand(
    private val loadoutService: LoadoutService,
) : CliktCommand(name = "remove") {
    override fun help(context: Context): String = "Remove a loadout"

    private val loadoutName by argument("name", help = "Name of the loadout to remove")

    override fun run() {
        when (val result = loadoutService.deleteLoadout(loadoutName)) {
            is Result.Success -> {
                echo("Removed loadout '${result.value.loadoutName}'")
                if (result.value.clearedCurrentLoadout) {
                    echo("Cleared the current loadout selection.")
                }
            }
            is Result.Error -> {
                echoError(result.error)
                throw ProgramResult(1)
            }
        }
    }
}
