package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.entity.packaging.Result
import domain.service.LoadoutService

class ConfigCommand(
    private val loadoutService: LoadoutService,
) : CliktCommand(
    name = "config",
) {
    override fun help(context: Context): String = "View or change repository-level Loadout settings"

    private val defaultLoadoutName by option("--default-loadout")
        .help("Set the repository default loadout used by auto-sync hooks")

    override fun run() {
        val result =
            defaultLoadoutName?.let { loadoutName ->
                loadoutService
                    .setRepositoryDefaultLoadoutName(loadoutName)
                    .map { settings -> settings.defaultLoadoutName }
            } ?: loadoutService.getRepositoryDefaultLoadoutName()

        when (result) {
            is Result.Success -> {
                val configuredLoadoutName = result.value
                if (configuredLoadoutName == null) {
                    echo("No repository default loadout set.")
                } else {
                    echo("Default loadout: $configuredLoadoutName")
                }
            }
            is Result.Error -> {
                echoError(result.error)
                throw ProgramResult(1)
            }
        }
    }
}
