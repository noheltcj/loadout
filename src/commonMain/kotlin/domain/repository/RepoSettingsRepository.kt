package domain.repository

import domain.entity.RepoSettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface RepoSettingsRepository {
    fun loadSettings(path: String? = null): Result<RepoSettings, LoadoutError>

    fun saveSettings(settings: RepoSettings, path: String? = null): Result<Unit, LoadoutError>

    fun getDefaultPath(): String
}
