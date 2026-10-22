package domain.repository

import domain.entity.LocalLoadoutState
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface LocalLoadoutStateRepository {
    fun loadLocalState(): Result<LocalLoadoutState, LoadoutError.UnreadableLocalState>

    fun saveLocalState(localState: LocalLoadoutState): Result<Unit, LoadoutError>
}
