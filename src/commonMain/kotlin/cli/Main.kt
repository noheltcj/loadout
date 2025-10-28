package cli

import cli.commands.AddCommand
import cli.commands.CreateCommand
import cli.commands.ListCommand
import cli.commands.RemoveCommand
import cli.commands.SyncCommand
import cli.commands.UseCommand
import cli.di.withApplicationScope
import com.github.ajalt.clikt.core.subcommands

fun main(args: Array<String>) {
    withApplicationScope {
        LoadoutCli(
            loadoutService = loadoutService,
            composeLoadout = loadoutCompositionService,
            checkLoadoutSync = checkLoadoutSync
        ).subcommands(
            ListCommand(
                loadoutService = loadoutService
            ),
            CreateCommand(
                loadoutService = loadoutService
            ),
            UseCommand(
                loadoutService = loadoutService,
                composeLoadout = loadoutCompositionService,
            ),
            SyncCommand(
                loadoutService = loadoutService,
                composeLoadout = loadoutCompositionService,
            ),
            AddCommand(
                loadoutService = loadoutService
            ),
            RemoveCommand(
                loadoutService = loadoutService
            )
        ).main(args)
    }
}
