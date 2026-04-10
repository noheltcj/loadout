package cli

import cli.commands.CreateCommand
import cli.commands.InitCommand
import cli.commands.LinkCommand
import cli.commands.ListCommand
import cli.commands.RemoveCommand
import cli.commands.SyncCommand
import cli.commands.UnlinkCommand
import cli.commands.UseCommand
import cli.di.ApplicationScope
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

fun createLoadoutCommand(applicationScope: ApplicationScope): CliktCommand =
    LoadoutCli(
        loadoutService = applicationScope.loadoutService,
        composeLoadout = applicationScope.loadoutCompositionService,
        checkLoadoutSync = applicationScope.checkLoadoutSync
    ).subcommands(
        InitCommand(
            fileRepository = applicationScope.fileRepository,
            loadoutService = applicationScope.loadoutService,
            composeLoadout = applicationScope.loadoutCompositionService,
            defaultOutputPaths = applicationScope.defaultOutputPaths
        ),
        ListCommand(
            loadoutService = applicationScope.loadoutService
        ),
        CreateCommand(
            loadoutService = applicationScope.loadoutService,
            fileRepository = applicationScope.fileRepository
        ),
        UseCommand(
            loadoutService = applicationScope.loadoutService,
            composeLoadout = applicationScope.loadoutCompositionService,
            defaultOutputPaths = applicationScope.defaultOutputPaths
        ),
        SyncCommand(
            loadoutService = applicationScope.loadoutService,
            composeLoadout = applicationScope.loadoutCompositionService,
            defaultOutputPaths = applicationScope.defaultOutputPaths
        ),
        LinkCommand(
            loadoutService = applicationScope.loadoutService,
            fileRepository = applicationScope.fileRepository
        ),
        UnlinkCommand(
            loadoutService = applicationScope.loadoutService
        ),
        RemoveCommand(
            loadoutService = applicationScope.loadoutService
        )
    )
