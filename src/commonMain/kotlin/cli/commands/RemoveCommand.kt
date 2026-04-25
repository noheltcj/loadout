package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import domain.usecase.RemoveLoadoutUseCase

class RemoveCommand(
    private val removeLoadout: RemoveLoadoutUseCase,
) : CliktCommand(
    name = "remove",
) {
    override fun help(context: Context): String = "Remove a loadout"

    private val loadoutName by argument("name", help = "Name of the loadout to remove")

    override fun run() {
        removeLoadout(loadoutName).fold(
            onSuccess = { result ->
                echo("Removed loadout '${result.loadoutName}'")
                if (result.clearedCurrentLoadout) {
                    echo("Cleared the current loadout selection.")
                }
            },
            onError = { error ->
                echoError(error)
                throw ProgramResult(1)
            },
        )
    }
}
