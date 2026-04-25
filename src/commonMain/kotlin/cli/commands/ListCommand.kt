package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.entity.Loadout
import domain.usecase.ListLoadoutsUseCase

class ListCommand(
    private val listLoadouts: ListLoadoutsUseCase,
) : CliktCommand(
    name = "list",
) {
    override fun help(context: Context): String = "List all available loadouts"

    private val verbose by option("--verbose", "-v")
        .flag(default = false)
        .help("Enable verbose output")

    // TODO: Inherit global --config option from main CLI

    override fun run() {
        listLoadouts().fold(
            onSuccess = { loadouts ->
                if (loadouts.isEmpty()) {
                    echo("No loadouts found. Create one with 'loadout create <name>'")
                    return
                }

                outputTable(loadouts)
            },
            onError = { error ->
                echoError(error, verbose)
                throw ProgramResult(1)
            },
        )
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
}
