package domain.repository

import domain.entity.LocalLoadoutState
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface LocalLoadoutStateRepository {
    fun loadLocalState(localStatePath: String? = null): Result<LocalLoadoutState, LoadoutError>

    fun saveLocalState(
        localState: LocalLoadoutState,
        localStatePath: String? = null,
    ): Result<Unit, LoadoutError>

    fun getDefaultLocalStatePath(): String
}
