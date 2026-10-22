package domain.entity

data class LoadoutFileLocations(
    val localStateFile: String,
    val repositorySettingsFile: String,
    val loadoutsDirectory: String,
    val backupsDirectory: String,
    val hooksDirectory: String,
)
