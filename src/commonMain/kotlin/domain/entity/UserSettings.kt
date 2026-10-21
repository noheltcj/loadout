package domain.entity

import domain.entity.error.LoadoutError
import kotlinx.serialization.Serializable

/** Settings that follow one developer into every repository. */
@Serializable
data class UserSettings(
    /** The files this developer's repositories generate, or null to leave the choice to each repository. */
    val outputFiles: List<String>? = null,
)

/** Where the user settings file lives. [pathInHome] is [path] relative to the home directory when inside it. */
data class UserSettingsLocation(
    val path: String,
    val pathInHome: String?,
)

/** What the user settings file holds. Only `loadout config --user` creates it. */
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
