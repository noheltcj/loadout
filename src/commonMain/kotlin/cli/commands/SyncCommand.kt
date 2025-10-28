package cli.commands

import cli.commands.extension.echoComposedFilesWriteResult
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.help
import domain.service.LoadoutService
import domain.entity.packaging.Result
import domain.service.LoadoutCompositionService
import domain.usecase.WriteComposedFilesUseCase

class SyncCommand(
    private val loadoutService: LoadoutService,
    private val composeLoadout: LoadoutCompositionService,
) : CliktCommand(
    name = "sync",
    help = "Re-compose and synchronize the current loadout"
) {

    private val stdOutOnly by option("--std-out")
        .flag(default = false)
        .help("Print to std-out without writing files")

    private val outputDir by option("--output", "-o")
        .help("Override output directory")

    override fun run() {
        when (val currentResult = loadoutService.getCurrentLoadout()) {
            is Result.Success -> {
                val currentLoadout = currentResult.value
                if (currentLoadout == null) {
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
                            when (val setLoadoutResult = loadoutService.setCurrentLoadout(composedOutput, outputDir ?: ".")) {
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
                echo("Failed to get current loadout: ${currentResult.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}
