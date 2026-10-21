package domain.entity

/** Worktree-relative locations of the files Loadout keeps for itself. */
data class LoadoutFileLocations(
    val localStateFile: String,
    val repositorySettingsFile: String,
    val loadoutsDirectory: String,
    val backupsDirectory: String,
    val hooksDirectory: String,
)
