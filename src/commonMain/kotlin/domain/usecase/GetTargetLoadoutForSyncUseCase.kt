package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

class GetTargetLoadoutForSyncUseCase(
    private val getRepositorySettings: GetRepositorySettingsUseCase,
    private val getCurrentLoadout: GetCurrentLoadoutUseCase,
    private val getLoadout: GetLoadoutUseCase,
) {
    operator fun invoke(autoSync: Boolean): Result<CurrentLoadoutSelection, LoadoutError> =
        if (autoSync) {
            getRepositorySettings().flatMap { settings ->
                settings.defaultLoadoutName?.let { defaultName ->
                    getLoadout(defaultName).map { CurrentLoadoutSelection.Selected(it) }
                } ?: getCurrentLoadout()
            }
        } else {
            getCurrentLoadout()
        }
}
