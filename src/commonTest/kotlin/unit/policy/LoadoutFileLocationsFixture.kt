package unit.policy

import domain.entity.LoadoutFileLocations

/** Loadout's own files at their usual locations, which output file validation reserves. */
val loadoutFileLocations =
    LoadoutFileLocations(
        localStateFile = ".loadout.local.json",
        repositorySettingsFile = ".loadout.json",
        loadoutsDirectory = ".loadouts",
        backupsDirectory = ".loadout-backups",
        hooksDirectory = ".githooks",
    )
