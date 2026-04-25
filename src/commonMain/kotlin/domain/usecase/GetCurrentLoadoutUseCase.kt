package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.LocalLoadoutStateRepository

class GetCurrentLoadoutUseCase(
    private val localLoadoutStateRepository: LocalLoadoutStateRepository,
    private val getLoadout: GetLoadoutUseCase,
) {
    operator fun invoke(): Result<CurrentLoadoutSelection, LoadoutError> =
        localLoadoutStateRepository.loadLocalState().flatMap { state ->
            state.activeLoadoutName?.let { name ->
                getLoadout(name).map { CurrentLoadoutSelection.Selected(it) }
            } ?: Result.Success(CurrentLoadoutSelection.None)
        }
}
