package unit.policy

import domain.entity.LoadoutFileLocations

val loadoutFileLocations =
    LoadoutFileLocations(
        localStateFile = ".loadout.local.json",
        repositorySettingsFile = ".loadout.json",
        loadoutsDirectory = ".loadouts",
        backupsDirectory = ".loadout-backups",
        hooksDirectory = ".githooks",
    )
