package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.ConfigRepository

class GetCurrentLoadoutUseCase(
    private val configRepository: ConfigRepository,
    private val getLoadout: GetLoadoutUseCase,
) {
    operator fun invoke(): Result<CurrentLoadoutSelection, LoadoutError> =
        configRepository
            .loadConfig()
            .flatMap { config ->
                config.currentLoadoutName?.let { currentLoadoutName ->
                    getLoadout(currentLoadoutName).map(CurrentLoadoutSelection::Selected)
                } ?: Result.Success(CurrentLoadoutSelection.None)
            }
}
