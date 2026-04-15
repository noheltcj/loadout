package cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import domain.usecase.LinkFragmentToLoadoutUseCase

class AddCommand(
    private val linkFragmentToLoadout: LinkFragmentToLoadoutUseCase,
) : CliktCommand(
        name = "add",
    ) {
    override val hiddenFromHelp: Boolean = true
    override val treatUnknownOptionsAsArgs: Boolean = true

    override fun help(context: Context): String = "Legacy redirect to loadout link"

    @Suppress("unused")
    private val legacyArguments by argument()
        .multiple()

    override fun run() {
        echo("The 'add' subcommand has been replaced by 'link'.", err = true)
        echo("", err = true)
        echo(redirectHelp(), err = true)
        throw ProgramResult(1)
    }

    private fun redirectHelp(): String {
        val help =
            LinkCommand(linkFragmentToLoadout).getFormattedHelp()
                ?: "Usage: loadout link <fragment-path> --to <loadout> [--after <fragment>]"

        val usagePrefix = "Usage: link"
        return if (help.startsWith(usagePrefix)) {
            help.replaceFirst(usagePrefix, "Usage: loadout link")
        } else {
            help
        }
    }
}
