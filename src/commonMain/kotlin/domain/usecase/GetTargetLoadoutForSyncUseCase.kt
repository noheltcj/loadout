package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.RepositorySettingsRepository

class GetTargetLoadoutForSyncUseCase(
    private val repositorySettingsRepository: RepositorySettingsRepository,
    private val getCurrentLoadout: GetCurrentLoadoutUseCase,
    private val getLoadout: GetLoadoutUseCase,
) {
    operator fun invoke(autoSync: Boolean): Result<CurrentLoadoutSelection, LoadoutError> =
        if (autoSync) {
            repositorySettingsRepository.loadRepositorySettings().flatMap { settings ->
                settings.defaultLoadoutName?.let { defaultName ->
                    getLoadout(defaultName).map { CurrentLoadoutSelection.Selected(it) }
                } ?: getCurrentLoadout()
            }
        } else {
            getCurrentLoadout()
        }
}
