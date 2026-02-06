package cli.commands

import cli.commands.extension.echoComposedFilesWriteResult
import cli.outputPaths
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.entity.packaging.Result
import domain.service.LoadoutCompositionService
import domain.service.LoadoutService

class UseCommand(
    private val loadoutService: LoadoutService,
    private val composeLoadout: LoadoutCompositionService,
    private val defaultOutputPaths: List<String>,
) : CliktCommand(
        name = "use",
        help = "Switch to and compose a loadout"
    ) {
    private val loadoutName by argument("name", help = "Name of the loadout to use")

    private val stdOutOnly by option("--std-out")
        .flag(default = false)
        .help("Print to std-out without writing files")

    private val outputDir by option("--output", "-o")
        .help("Override output directory")

    // TODO: Inherit global --config option from main CLI

    override fun run() {
        // TODO: Implement a guard function to flatten this overly nested logic
        when (val result = loadoutService.getLoadout(loadoutName)) {
            is Result.Success -> {
                val loadout = result.value

                when (val composeResult = composeLoadout(loadout)) {
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
                echo("Failed to get loadout: ${result.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}
