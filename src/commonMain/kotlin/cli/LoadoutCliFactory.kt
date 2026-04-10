package cli

import cli.commands.AddCommand
import cli.commands.CreateCommand
import cli.commands.InitCommand
import cli.commands.ListCommand
import cli.commands.RemoveCommand
import cli.commands.SyncCommand
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
            loadoutService = applicationScope.loadoutService
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
        AddCommand(
            loadoutService = applicationScope.loadoutService,
            fileRepository = applicationScope.fileRepository
        ),
        RemoveCommand(
            loadoutService = applicationScope.loadoutService
        )
    )
