package domain.repository

import domain.entity.RepoSettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface RepoSettingsRepository {
    fun loadSettings(): Result<RepoSettings, LoadoutError>

    fun saveSettings(settings: RepoSettings): Result<Unit, LoadoutError>
}
