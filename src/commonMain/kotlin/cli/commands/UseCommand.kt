package cli.commands

import cli.commands.extension.echoLoadoutActivation
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.usecase.LoadoutOutputTarget
import domain.usecase.UseLoadoutResult
import domain.usecase.UseLoadoutUseCase

class UseCommand(
    private val useLoadout: UseLoadoutUseCase,
) : CliktCommand(
    name = "use",
) {
    override fun help(context: Context): String = "Switch to and compose a loadout"

    private val loadoutName by argument("name", help = "Name of the loadout to use")

    private val stdOutOnly by option("--std-out")
        .flag(default = false)
        .help("Print to std-out without writing files")

    // TODO: Inherit global --config option from main CLI

    override fun run() {
        val outputTarget =
            if (stdOutOnly) {
                LoadoutOutputTarget.StandardOutput
            } else {
                LoadoutOutputTarget.ConfiguredOutputFiles
            }

        useLoadout(loadoutName = loadoutName, outputTarget = outputTarget).fold(
            onSuccess = { useResult ->
                when (useResult) {
                    is UseLoadoutResult.PrintedToStandardOutput -> echo(useResult.composedOutput.content)

                    is UseLoadoutResult.Activated ->
                        echoLoadoutActivation(
                            activation = useResult.activation,
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
