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
import domain.service.LoadoutCompositionService
import domain.service.LoadoutService

class SyncCommand(
    private val loadoutService: LoadoutService,
    private val composeLoadout: LoadoutCompositionService,
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

    private val autoSync by option("--auto")
        .flag(default = false)
        .help("Resolve the repo default loadout when no current loadout is set")

    override fun run() {
        if (stdOutOnly && outputDir != null) {
            echoError(LoadoutError.ValidationError("flags", "Cannot specify both --std-out and --output"))
            throw ProgramResult(1)
        }

        when (val currentResult = resolveTargetLoadout()) {
            is Result.Success -> {
                val currentLoadout = currentResult.value
                if (currentLoadout == null) {
                    if (autoSync) {
                        return
                    }

                    echo("No current loadout set. Use 'loadout list' to see available loadouts.", err = true)
                    throw ProgramResult(1)
                }

                val loadoutName = currentLoadout.name

                when (val composeResult = composeLoadout(currentLoadout)) {
                    is Result.Success -> {
                        val composedOutput = composeResult.value

                        if (stdOutOnly) {
                            echo(composedOutput.content)
                        } else {
                            val outputPaths = outputDir?.let { outputPaths(it) } ?: defaultOutputPaths

                            when (
                                val setLoadoutResult =
                                    loadoutService.setCurrentLoadout(composedOutput, outputPaths)
                            ) {
                                is Result.Success -> {
                                    echoComposedFilesWriteResult(
                                        result = setLoadoutResult.value,
                                        loadoutName = loadoutName,
                                        composedContentLength = composedOutput.content.length
                                    )
                                }
                                is Result.Error -> {
                                    echo("Failed to set loadout: ${setLoadoutResult.error.message}", err = true)
                                    throw ProgramResult(1)
                                }
                            }
                        }
                    }
                    is Result.Error -> {
                        echo("Failed to compose loadout: ${composeResult.error.message}", err = true)
                        throw ProgramResult(1)
                    }
                }
            }
            is Result.Error -> {
                echoError(currentResult.error)
                throw ProgramResult(1)
            }
        }
    }

    private fun resolveTargetLoadout() =
        if (autoSync) {
            loadoutService.getAutoSyncLoadout()
        } else {
            loadoutService.getCurrentLoadout()
        }
}
