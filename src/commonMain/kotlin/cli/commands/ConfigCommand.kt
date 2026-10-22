package cli.commands

import cli.commands.extension.displayPath
import cli.commands.extension.echoRepositorySettingsWarnings
import cli.commands.extension.echoUserSettingsWarnings
import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import domain.entity.RepositorySettings
import domain.entity.UserSettingsFile
import domain.entity.error.LoadoutError
import domain.policy.OutputFilePath
import domain.policy.RepositorySettingsRepair
import domain.usecase.GetRepairableRepositorySettingsUseCase
import domain.usecase.GetUserSettingsUseCase
import domain.usecase.LocatedUserSettings
import domain.usecase.OutputFilesResolution
import domain.usecase.OutputFilesSource
import domain.usecase.RepositorySettingsUpdateInput
import domain.usecase.ResolveOutputFilesUseCase
import domain.usecase.UpdateRepositorySettingsUseCase
import domain.usecase.UpdateUserSettingsUseCase
import domain.usecase.UserSettingsUpdateInput

private const val DEFAULT_LOADOUT_OPTION = "--default-loadout"
private const val UNSET_DEFAULT_LOADOUT_OPTION = "--unset-default-loadout"
private const val FRAGMENTS_HOME_OPTION = "--fragments-home"
private const val TRUST_FRAGMENT_HOST_OPTION = "--trust-fragment-host"
private const val UNTRUST_FRAGMENT_HOST_OPTION = "--untrust-fragment-host"
private const val MAXIMUM_FRAGMENT_BYTES_OPTION = "--maximum-fragment-bytes"

class ConfigCommand(
    private val getRepositorySettings: GetRepairableRepositorySettingsUseCase,
    private val updateRepositorySettings: UpdateRepositorySettingsUseCase,
    private val resolveOutputFiles: ResolveOutputFilesUseCase,
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
) : CliktCommand(
    name = "config",
) {
    override fun help(context: Context): String =
        "View or change this repository's Loadout settings, or your own with --user"

    private val user by option("--user")
        .flag(default = false)
        .help("View or change your own settings, which apply in every repository, instead of the repository's")

    private val defaultLoadoutName by option(DEFAULT_LOADOUT_OPTION)
        .help("Set the repository default loadout used by auto-sync hooks")

    private val unsetDefaultLoadout by option(UNSET_DEFAULT_LOADOUT_OPTION)
        .flag(default = false)
        .help("Clear the repository default loadout so auto-sync follows the active loadout")

    private val fragmentsHome by option(FRAGMENTS_HOME_OPTION)
        .help("Set the repository-relative directory used for copied fragments")

    private val trustedFragmentSourceHost by option(TRUST_FRAGMENT_HOST_OPTION)
        .help("Allow fragment copies from this exact HTTPS hostname")

    private val untrustedFragmentSourceHost by option(UNTRUST_FRAGMENT_HOST_OPTION)
        .help("Remove an HTTPS hostname from the fragment copy allowlist")

    private val maximumFragmentCopyBytes by option(MAXIMUM_FRAGMENT_BYTES_OPTION)
        .int()
        .help(
            "Set the fragment copy size limit, up to ${RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES} bytes"
        )

    private val outputFiles by option("--output-files")
        .convert { value -> value.split(',').map(String::trim).filter(String::isNotEmpty) }
        .help(
            "Set the comma-separated files to generate, such as " +
                "CLAUDE.md,AGENTS.md,GEMINI.md,.github/copilot-instructions.md"
        )

    private val unsetOutputFiles by option("--unset-output-files")
        .flag(default = false)
        .help("Remove the output files setting so the next layer applies: the repository's, then the built-in default")

    override fun run() {
        if (user) {
            runForUser()
        } else {
            runForRepository()
        }
    }

    private fun runForRepository() {
        if (outputFiles?.isEmpty() == true) {
            fail(
                "List at least one output file, or remove the repository setting with " +
                    "'loadout config --unset-output-files'."
            )
        }

        val requestedUpdates =
            listOfNotNull(
                defaultLoadoutName?.let(RepositorySettingsUpdateInput::DefaultLoadout),
                RepositorySettingsUpdateInput.DefaultLoadout(null).takeIf { unsetDefaultLoadout },
                fragmentsHome?.let(RepositorySettingsUpdateInput::FragmentsHome),
                trustedFragmentSourceHost?.let(RepositorySettingsUpdateInput::TrustFragmentSourceHost),
                untrustedFragmentSourceHost?.let(RepositorySettingsUpdateInput::UntrustFragmentSourceHost),
                maximumFragmentCopyBytes?.let(RepositorySettingsUpdateInput::MaximumFragmentCopyBytes),
                outputFiles?.let(RepositorySettingsUpdateInput::OutputFiles),
                RepositorySettingsUpdateInput.UnsetOutputFiles.takeIf { unsetOutputFiles },
            )

        if (requestedUpdates.size > 1) {
            fail("Change one repository setting per invocation.")
        }

        val result =
            requestedUpdates.singleOrNull()
                ?.let(updateRepositorySettings::invoke)
                ?: getRepositorySettings()

        result
            .flatMap { repair -> resolveOutputFiles(repair.settings).map { resolution -> repair to resolution } }
            .fold(
                onSuccess = { (repair, resolution) ->
                    echoRepositorySettings(repair)
                    echoOutputFiles(resolution)
                },
                onError = ::fail,
            )
    }

    private fun runForUser() {
        repositoryOnlyOptions().firstOrNull()?.let { option -> fail("$option is a repository setting; remove --user.") }

        if (outputFiles?.isEmpty() == true) {
            fail(
                "List at least one output file, or remove your user setting with " +
                    "'loadout config --user --unset-output-files'."
            )
        }

        val requestedUpdates =
            listOfNotNull(
                outputFiles?.let(UserSettingsUpdateInput::OutputFiles),
                UserSettingsUpdateInput.UnsetOutputFiles.takeIf { unsetOutputFiles },
            )

        if (requestedUpdates.size > 1) {
            fail("Change one user setting per invocation.")
        }

        val requestedUpdate = requestedUpdates.singleOrNull()
        val result = requestedUpdate?.let(updateUserSettings::invoke) ?: getUserSettings()

        result.fold(
            onSuccess = { userSettings ->
                echoUserSettings(userSettings)
                if (requestedUpdate != null) {
                    echo("Repositories apply this on their next sync.")
                }
            },
            onError = ::fail,
        )
    }

    private fun repositoryOnlyOptions(): List<String> =
        listOfNotNull(
            DEFAULT_LOADOUT_OPTION.takeIf { defaultLoadoutName != null },
            UNSET_DEFAULT_LOADOUT_OPTION.takeIf { unsetDefaultLoadout },
            FRAGMENTS_HOME_OPTION.takeIf { fragmentsHome != null },
            TRUST_FRAGMENT_HOST_OPTION.takeIf { trustedFragmentSourceHost != null },
            UNTRUST_FRAGMENT_HOST_OPTION.takeIf { untrustedFragmentSourceHost != null },
            MAXIMUM_FRAGMENT_BYTES_OPTION.takeIf { maximumFragmentCopyBytes != null },
        )

    private fun echoRepositorySettings(repair: RepositorySettingsRepair) {
        echoRepositorySettingsWarnings(repair.warnings)
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
    }

    private fun echoOutputFiles(resolution: OutputFilesResolution) {
        echoUserSettingsWarnings(resolution.userSettingsWarnings)
        echo("Output files: ${resolution.outputFiles.describe()} (${resolution.source.describe()})")
    }

    private fun echoUserSettings(userSettings: LocatedUserSettings) {
        echoUserSettingsWarnings(userSettings.repair.warnings)
        val notCreated = if (userSettings.file is UserSettingsFile.Absent) " (not created)" else ""
        echo("User settings: ${userSettings.file.location.displayPath}$notCreated")
        echo("Output files: ${userSettings.repair.settings.outputFiles?.joinToString() ?: "not set"}")
    }

    private fun fail(error: LoadoutError): Nothing {
        echoError(error)
        throw ProgramResult(1)
    }

    private fun fail(message: String): Nothing {
        echo(message, err = true)
        throw ProgramResult(1)
    }
}

private fun List<OutputFilePath>.describe(): String = joinToString(transform = OutputFilePath::value)

private fun OutputFilesSource.describe(): String =
    when (this) {
        OutputFilesSource.BuiltInDefault -> "built-in default"
        OutputFilesSource.RepositorySetting -> "repository setting"
        is OutputFilesSource.UserSetting ->
            overriddenRepositorySetting
                ?.let { repositoryOutputFiles ->
                    "your user setting; overrides the repository setting ${repositoryOutputFiles.describe()}"
                }
                ?: "your user setting"
    }
