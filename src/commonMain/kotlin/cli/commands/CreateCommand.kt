package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileRepository
import domain.service.LoadoutService

class CreateCommand(
    private val loadoutService: LoadoutService,
    private val fileRepository: FileRepository,
) : CliktCommand(
    name = "create",
) {
    override fun help(context: Context): String = "Create a new loadout"

    private val name by argument(help = "Name of the loadout to create")

    private val description by option("--desc", "--description")
        .help("Short description of the loadout")

    private val fragments by option("--fragment", "-f")
        .multiple()
        .help("Initial fragments to include (can be specified multiple times)")

    private val cloneFrom by option("--clone")
        .help("Create by copying an existing loadout")

    override fun run() {
        val missingFragment = fragments.firstOrNull { !fileRepository.fileExists(it) }
        if (missingFragment != null) {
            echoError(LoadoutError.FragmentNotFound(missingFragment))
            throw ProgramResult(1)
        }

        val result =
            if (cloneFrom != null) {
                loadoutService.createLoadoutFromClone(
                    name = name,
                    cloneFrom = cloneFrom!!,
                    description = description,
                    additionalFragments = fragments,
                )
            } else {
                loadoutService.createLoadout(
                    name = name,
                    description = description,
                    fragments = fragments,
                )
            }

        when (result) {
            is Result.Success -> {
                val loadout = result.value
                if (cloneFrom != null) {
                    echo("Created loadout '${loadout.name}' (cloned from '$cloneFrom')")
                } else {
                    echo("Created loadout '${loadout.name}'")
                }

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
                echoError(result.error)
                throw ProgramResult(1)
            }
        }
    }
}
