package cli.commands

import cli.commands.extension.echoComposedFilesWriteResult
import cli.echoError
import cli.outputPaths
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.usecase.LoadoutOutputTarget
import domain.usecase.SyncCurrentLoadoutInput
import domain.usecase.SyncCurrentLoadoutResult
import domain.usecase.SyncCurrentLoadoutUseCase

class SyncCommand(
    private val syncCurrentLoadout: SyncCurrentLoadoutUseCase,
    private val defaultOutputPaths: List<String>,
) : CliktCommand(
        name = "sync",
    ) {
    override fun help(context: Context): String = "Re-compose and synchronize the current loadout"

    private val stdOutOnly by option("--std-out")
        .flag(default = false)
        .help("Print to std-out without writing files")

    private val outputDir by option("--output", "-o")
        .help("Override output directory")

    override fun run() {
        if (stdOutOnly && outputDir != null) {
            echoError(LoadoutError.ValidationError("flags", "Cannot specify both --std-out and --output"))
            throw ProgramResult(1)
        }

        val outputTarget =
            if (stdOutOnly) {
                LoadoutOutputTarget.StandardOutput
            } else {
                LoadoutOutputTarget.FileSystem(outputDir?.let(::outputPaths) ?: defaultOutputPaths)
            }

        when (val currentResult = syncCurrentLoadout(SyncCurrentLoadoutInput(outputTarget = outputTarget))) {
            is Result.Success -> {
                when (val syncResult = currentResult.value) {
                    SyncCurrentLoadoutResult.NoCurrentLoadout -> {
                        echo("No current loadout set. Use 'loadout list' to see available loadouts.", err = true)
                        throw ProgramResult(1)
                    }

                    is SyncCurrentLoadoutResult.PrintedToStandardOutput -> {
                        echo(syncResult.composedOutput.content)
                    }

                    is SyncCurrentLoadoutResult.Activated ->
                        echoComposedFilesWriteResult(
                            result = syncResult.writeResult,
                            loadoutName = syncResult.loadout.name,
                            composedContentLength = syncResult.composedOutput.content.length
                        )
                }
            }
            is Result.Error -> {
                echo(currentResult.error.message, err = true)
                throw ProgramResult(1)
            }
        }
    }
}
