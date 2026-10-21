package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import domain.entity.RepositorySettings
import domain.policy.RepositorySettingsRepairWarning
import domain.usecase.GetRepairableRepositorySettingsUseCase
import domain.usecase.RepositorySettingsUpdateInput
import domain.usecase.UpdateRepositorySettingsUseCase

class ConfigCommand(
    private val getRepositorySettings: GetRepairableRepositorySettingsUseCase,
    private val updateRepositorySettings: UpdateRepositorySettingsUseCase,
) : CliktCommand(
    name = "config",
) {
    override fun help(context: Context): String = "View or change repository-level Loadout settings"

    private val defaultLoadoutName by option("--default-loadout")
        .help("Set the repository default loadout used by auto-sync hooks")

    private val unsetDefaultLoadout by option("--unset-default-loadout")
        .flag(default = false)
        .help("Clear the repository default loadout so auto-sync follows the active loadout")

    private val fragmentsHome by option("--fragments-home")
        .help("Set the repository-relative directory used for copied fragments")

    private val trustedFragmentSourceHost by option("--trust-fragment-host")
        .help("Allow fragment copies from this exact HTTPS hostname")

    private val untrustedFragmentSourceHost by option("--untrust-fragment-host")
        .help("Remove an HTTPS hostname from the fragment copy allowlist")

    private val maximumFragmentCopyBytes by option("--maximum-fragment-bytes")
        .int()
        .help(
            "Set the fragment copy size limit, up to ${RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES} bytes"
        )

    override fun run() {
        val requestedUpdates =
            listOfNotNull(
                defaultLoadoutName?.let(RepositorySettingsUpdateInput::DefaultLoadout),
                RepositorySettingsUpdateInput.DefaultLoadout(null).takeIf { unsetDefaultLoadout },
                fragmentsHome?.let(RepositorySettingsUpdateInput::FragmentsHome),
                trustedFragmentSourceHost?.let(RepositorySettingsUpdateInput::TrustFragmentSourceHost),
                untrustedFragmentSourceHost?.let(RepositorySettingsUpdateInput::UntrustFragmentSourceHost),
                maximumFragmentCopyBytes?.let(RepositorySettingsUpdateInput::MaximumFragmentCopyBytes),
            )

        if (requestedUpdates.size > 1) {
            echo("Change one repository setting per invocation.", err = true)
            throw ProgramResult(1)
        }

        val result =
            requestedUpdates.singleOrNull()
                ?.let(updateRepositorySettings::invoke)
                ?: getRepositorySettings()

        result.fold(
            onSuccess = { repair ->
                repair.warnings.forEach { warning -> echo("Warning: ${warning.describe()}", err = true) }
                val settings = repair.settings
                if (settings.defaultLoadoutName == null) {
                    echo("No repository default loadout set.")
                } else {
                    echo("Default loadout: ${settings.defaultLoadoutName}")
                }
                echo("Fragments home: ${settings.fragmentsHome}")
                echo(
                    "Trusted fragment source hosts: " +
                        settings.trustedFragmentSourceHosts.ifEmpty { listOf("none") }.joinToString()
                )
                echo("Maximum fragment copy size: ${settings.maximumFragmentCopyBytes} bytes")
            },
            onError = { error ->
                echoError(error)
                throw ProgramResult(1)
            },
        )
    }
}

private fun RepositorySettingsRepairWarning.describe(): String =
    when (this) {
        is RepositorySettingsRepairWarning.UnreadableSettingsReplaced ->
            "Repository settings could not be read; safe defaults are in use. ${error.message}"
        is RepositorySettingsRepairWarning.InvalidFragmentsHomeReplaced ->
            "Invalid fragments home '$invalidFragmentsHome' was replaced with " +
                "'${RepositorySettings.DEFAULT_FRAGMENTS_HOME}'."
        is RepositorySettingsRepairWarning.InvalidTrustedHostRemoved ->
            "Invalid trusted fragment host '$invalidHost' was removed."
        is RepositorySettingsRepairWarning.InvalidCopyLimitReplaced ->
            "Invalid fragment copy limit $invalidMaximumBytes was replaced with " +
                "${RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES} bytes."
    }
