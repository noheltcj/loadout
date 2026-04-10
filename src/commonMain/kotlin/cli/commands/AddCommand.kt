package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileRepository
import domain.service.LoadoutService

class AddCommand(
    private val loadoutService: LoadoutService,
    private val fileRepository: FileRepository,
) : CliktCommand(
        name = "add",
    ) {
    override fun help(context: Context): String = "Add a fragment to a loadout"

    private val fragmentPath by argument(help = "Path to the fragment to add")

    private val loadoutName by option("--to")
        .required()
        .help("Name of the loadout to add the fragment to")

    private val afterFragment by option("--after")
        .help("Insert the fragment after this existing fragment")

    override fun run() {
        if (!fileRepository.fileExists(fragmentPath)) {
            echoError(LoadoutError.FragmentNotFound(fragmentPath))
            throw ProgramResult(1)
        }

        when (
            val result =
                loadoutService.addFragmentToLoadout(
                    loadoutName = loadoutName,
                    fragmentPath = fragmentPath,
                    afterFragment = afterFragment,
                )
        ) {
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
                echoError(result.error)
                throw ProgramResult(1)
            }
        }
    }
}
