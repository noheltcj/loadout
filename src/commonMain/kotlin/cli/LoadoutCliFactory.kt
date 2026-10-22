package cli

import cli.commands.AddCommand
import cli.commands.ConfigCommand
import cli.commands.CopyFragmentCommand
import cli.commands.CreateCommand
import cli.commands.FragmentCommand
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
        readCurrentLoadoutStatus = applicationScope.readCurrentLoadoutStatus,
        checkLoadoutSync = applicationScope.checkLoadoutSync
    ).subcommands(
        InitCommand(
            initializeLoadoutProject = applicationScope.initializeLoadoutProject
        ),
        FragmentCommand().subcommands(
            CopyFragmentCommand(copyFragment = applicationScope.copyFragment),
            LinkCommand(linkFragmentToLoadout = applicationScope.linkFragmentToLoadout),
            UnlinkCommand(unlinkFragmentFromLoadout = applicationScope.unlinkFragmentFromLoadout),
        ),
        ListCommand(
            listLoadouts = applicationScope.listLoadouts
        ),
        CreateCommand(
            createLoadout = applicationScope.createLoadout
        ),
        UseCommand(
            useLoadout = applicationScope.useLoadout
        ),
        SyncCommand(
            syncLoadout = applicationScope.syncLoadout
        ),
        AddCommand(
            linkFragmentToLoadout = applicationScope.linkFragmentToLoadout
        ),
        RemoveCommand(
            removeLoadout = applicationScope.removeLoadout
        ),
        ConfigCommand(
            getRepositorySettings = applicationScope.getRepairableRepositorySettings,
            updateRepositorySettings = applicationScope.updateRepositorySettings,
            resolveOutputFiles = applicationScope.resolveOutputFiles,
            getUserSettings = applicationScope.getUserSettings,
            updateUserSettings = applicationScope.updateUserSettings
        )
    )
