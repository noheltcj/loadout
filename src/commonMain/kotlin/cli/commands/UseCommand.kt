package cli.commands

import cli.commands.extension.echoComposedFilesWriteResult
import cli.outputPaths
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.usecase.LoadoutOutputTarget
import domain.usecase.UseLoadoutInput
import domain.usecase.UseLoadoutResult
import domain.usecase.UseLoadoutUseCase

class UseCommand(
    private val useLoadout: UseLoadoutUseCase,
    private val defaultOutputPaths: List<String>,
) : CliktCommand(
    name = "use",
) {
    override fun help(context: Context): String = "Switch to and compose a loadout"

    private val loadoutName by argument("name", help = "Name of the loadout to use")

    private val stdOutOnly by option("--std-out")
        .flag(default = false)
        .help("Print to std-out without writing files")

    private val outputDir by option("--output", "-o")
        .help("Override output directory")

    // TODO: Inherit global --config option from main CLI

    override fun run() {
        val outputTarget =
            if (stdOutOnly) {
                LoadoutOutputTarget.StandardOutput
            } else {
                LoadoutOutputTarget.FileSystem(outputDir?.let(::outputPaths) ?: defaultOutputPaths)
            }

        useLoadout(UseLoadoutInput(loadoutName = loadoutName, outputTarget = outputTarget)).fold(
            onSuccess = { useResult ->
                when (useResult) {
                    is UseLoadoutResult.PrintedToStandardOutput -> echo(useResult.composedOutput.content)

                    is UseLoadoutResult.Activated ->
                        echoComposedFilesWriteResult(
                            result = useResult.writeResult,
                            loadoutName = useResult.loadout.name,
                            composedContentLength = useResult.composedOutput.content.length
                        )
                }
            },
            onError = { error ->
                echo(error.message, err = true)
                throw ProgramResult(1)
            },
        )
    }
}
