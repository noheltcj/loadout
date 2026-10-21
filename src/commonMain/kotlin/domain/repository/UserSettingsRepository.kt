package domain.repository

import domain.entity.UserSettings
import domain.entity.UserSettingsFile
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface UserSettingsRepository {
    fun loadUserSettings(): UserSettingsFile

    /** Writes [settings] to the located file, creating its directories as needed. */
    fun saveUserSettings(
        file: UserSettingsFile.Located,
        settings: UserSettings,
    ): Result<Unit, LoadoutError>
}
