package cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

class FragmentCommand : CliktCommand(name = "fragment") {
    override fun help(context: Context): String = "Copy fragments and manage their loadout membership"

    override fun run() = Unit
}
