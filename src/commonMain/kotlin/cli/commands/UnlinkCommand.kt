package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import domain.usecase.UnlinkFragmentFromLoadoutInput
import domain.usecase.UnlinkFragmentFromLoadoutUseCase

class UnlinkCommand(
    private val unlinkFragmentFromLoadout: UnlinkFragmentFromLoadoutUseCase,
) : CliktCommand(
    name = "unlink",
) {
    override fun help(context: Context): String = "Unlink a fragment from a loadout"

    private val fragmentPath by argument(help = "Path to the fragment to unlink")

    // TODO: Make this optional and default to the current loadout
    private val loadoutName by option("--from")
        .required()
        .help("Name of the loadout to unlink the fragment from")

    override fun run() {
        unlinkFragmentFromLoadout(
            UnlinkFragmentFromLoadoutInput(
                loadoutName = loadoutName,
                fragmentPath = fragmentPath
            )
        ).fold(
            onSuccess = { updatedLoadout ->
                val normalizedInput = fragmentPath.removePrefix("./")
                echo("Unlinked fragment '$normalizedInput' from loadout '$loadoutName'")

                if (updatedLoadout.fragments.isEmpty()) {
                    echo("Loadout is now empty.")
                } else {
                    echo("Remaining fragments:")
                    updatedLoadout.fragments.forEachIndexed { index, fragment ->
                        echo("  ${index + 1}. $fragment")
                    }
                }
            },
            onError = { error ->
                echoError(error)
                throw ProgramResult(1)
            },
        )
    }
}
