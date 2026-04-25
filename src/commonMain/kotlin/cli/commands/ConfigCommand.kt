package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.usecase.GetRepositorySettingsUseCase
import domain.usecase.UpdateRepositorySettingsUseCase

class ConfigCommand(
    private val getRepositorySettings: GetRepositorySettingsUseCase,
    private val updateRepositorySettings: UpdateRepositorySettingsUseCase,
) : CliktCommand(
    name = "config",
) {
    override fun help(context: Context): String = "View or change repository-level Loadout settings"

    private val defaultLoadoutName by option("--default-loadout")
        .help("Set the repository default loadout used by auto-sync hooks")

    override fun run() {
        val result =
            if (defaultLoadoutName != null) {
                updateRepositorySettings(defaultLoadoutName).map { it.defaultLoadoutName }
            } else {
                getRepositorySettings().map { it.defaultLoadoutName }
            }

        result.fold(
            onSuccess = { configuredLoadoutName ->
                if (configuredLoadoutName == null) {
                    echo("No repository default loadout set.")
                } else {
                    echo("Default loadout: $configuredLoadoutName")
                }
            },
            onError = { error ->
                echoError(error)
                throw ProgramResult(1)
            },
        )
    }
}
