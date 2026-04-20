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

class LinkCommand(
    private val loadoutService: LoadoutService,
    private val fileRepository: FileRepository,
) : CliktCommand(
    name = "link",
) {
    override fun help(context: Context): String = "Link a fragment into a loadout"

    private val fragmentPath by argument(help = "Path to the fragment to link")

    private val loadoutName by option("--to")
        .required()
        .help("Name of the loadout to link the fragment into")

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
                val normalizedInput = fragmentPath.removePrefix("./")
                echo("Linked fragment '$normalizedInput' to loadout '$loadoutName'")
                echo("Loadout now has ${updatedLoadout.fragments.size} fragments:")
                updatedLoadout.fragments.forEachIndexed { index, fragment ->
                    val marker = if (fragment == normalizedInput) " ← NEW" else ""
                    echo("  ${index + 1}. $fragment$marker")
                }
            }
            is Result.Error -> {
                echoError(result.error)
                throw ProgramResult(1)
            }
        }
    }
}
