package cli.commands

import cli.commands.extension.echoLoadoutActivation
import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.usecase.LoadoutOutputTarget
import domain.usecase.SyncLoadoutInput
import domain.usecase.SyncLoadoutResult
import domain.usecase.SyncLoadoutUseCase

class SyncCommand(
    private val syncLoadout: SyncLoadoutUseCase,
) : CliktCommand(
    name = "sync",
) {
    override fun help(context: Context): String = "Re-compose and synchronize the current loadout"

    private val stdOutOnly by option("--std-out")
        .flag(default = false)
        .help("Print to std-out without writing files")

    private val autoSync by option("--auto")
        .flag(default = false)
        .help("Resolve the repository default loadout when no current loadout is set")

    override fun run() {
        val outputTarget =
            if (stdOutOnly) {
                LoadoutOutputTarget.StandardOutput
            } else {
                LoadoutOutputTarget.ConfiguredOutputFiles
            }

        val result = syncLoadout(
            SyncLoadoutInput(
                outputTarget = outputTarget,
                shouldFallbackToDefault = autoSync
            )
        )

        result.fold(
            onSuccess = { syncResult ->
                when (syncResult) {
                    SyncLoadoutResult.NoCurrentLoadout -> {
                        if (autoSync) {
                            return
                        }
                        echo("No current loadout set. Use 'loadout list' to see available loadouts.", err = true)
                        throw ProgramResult(1)
                    }

                    is SyncLoadoutResult.PrintedToStandardOutput -> {
                        echo(syncResult.composedOutput.content)
                    }

                    is SyncLoadoutResult.Activated ->
                        echoLoadoutActivation(
                            activation = syncResult.activation,
                            loadoutName = syncResult.loadout.name,
                            composedContentLength = syncResult.composedOutput.content.length
                        )
                }
            },
            onError = { error ->
                echoError(error)
                throw ProgramResult(1)
            },
        )
    }
}
