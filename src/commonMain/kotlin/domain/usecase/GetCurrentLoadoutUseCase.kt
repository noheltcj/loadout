package domain.usecase

import domain.entity.LocalLoadoutState
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

class GetCurrentLoadoutUseCase(
    private val getLocalLoadoutState: GetLocalLoadoutStateUseCase,
    private val getLoadout: GetLoadoutUseCase,
) {
    operator fun invoke(): Result<CurrentLoadoutSelection, LoadoutError> =
        when (val state = getLocalLoadoutState().state) {
            LocalLoadoutState.Inactive -> Result.Success(CurrentLoadoutSelection.None)
            is LocalLoadoutState.Active -> getLoadout(state.loadoutName).map { CurrentLoadoutSelection.Selected(it) }
        }
}
