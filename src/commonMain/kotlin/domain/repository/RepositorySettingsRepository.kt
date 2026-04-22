package domain.repository

import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface RepositorySettingsRepository {
    fun loadRepositorySettings(): Result<RepositorySettings, LoadoutError>

    fun saveRepositorySettings(settings: RepositorySettings): Result<Unit, LoadoutError>
}
