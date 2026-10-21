package domain.entity

import domain.entity.error.LoadoutError
import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    /** Null leaves the choice to each repository. */
    val outputFiles: List<String>? = null,
)

/** [pathInHome] is [path] relative to the home directory when it lies inside it. */
data class UserSettingsLocation(
    val path: String,
    val pathInHome: String?,
)

sealed interface UserSettingsFile {
    data class Unlocatable(
        val environmentVariables: List<String>,
    ) : UserSettingsFile

    sealed interface Located : UserSettingsFile {
        val location: UserSettingsLocation
    }

    data class Absent(
        override val location: UserSettingsLocation,
    ) : Located

    data class Present(
        override val location: UserSettingsLocation,
        val settings: UserSettings,
    ) : Located

    data class Unreadable(
        override val location: UserSettingsLocation,
        val error: LoadoutError.UnreadableUserSettings,
    ) : Located
}
