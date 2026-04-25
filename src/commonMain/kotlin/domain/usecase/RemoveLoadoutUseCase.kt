package domain.usecase

import domain.entity.DeleteLoadoutResult
import domain.entity.LocalLoadoutState
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.validateLoadoutName
import domain.repository.LoadoutRepository
import domain.repository.LocalLoadoutStateRepository

class RemoveLoadoutUseCase(
    private val loadoutRepository: LoadoutRepository,
    private val localLoadoutStateRepository: LocalLoadoutStateRepository,
) {
    operator fun invoke(name: String): Result<DeleteLoadoutResult, LoadoutError> =
        validateLoadoutName(name).flatMap { validName ->
            removeLoadout(validName)
        }

    private fun removeLoadout(validName: String): Result<DeleteLoadoutResult, LoadoutError> =
        localLoadoutStateRepository
            .loadLocalState()
            .map { state -> state.removingLoadout(validName) }
            .flatMap { localStateUpdate ->
                loadoutRepository
                    .delete(validName)
                    .flatMap { persistLocalStateUpdate(localStateUpdate) }
                    .map { localStateUpdate.toDeleteResult(validName) }
            }

    private fun persistLocalStateUpdate(update: LocalStateUpdateAfterLoadoutRemoval): Result<Unit, LoadoutError> =
        when (update) {
            LocalStateUpdateAfterLoadoutRemoval.Unchanged ->
                Result.Success(Unit)

            LocalStateUpdateAfterLoadoutRemoval.ClearCurrentLoadout ->
                localLoadoutStateRepository.saveLocalState(clearedLocalLoadoutState)
        }
}

private sealed interface LocalStateUpdateAfterLoadoutRemoval {
    val clearedCurrentLoadout: Boolean

    data object Unchanged : LocalStateUpdateAfterLoadoutRemoval {
        override val clearedCurrentLoadout: Boolean = false
    }

    data object ClearCurrentLoadout : LocalStateUpdateAfterLoadoutRemoval {
        override val clearedCurrentLoadout: Boolean = true
    }
}

private val clearedLocalLoadoutState =
    LocalLoadoutState(
        activeLoadoutName = null,
        lastComposedContentHash = null,
    )

private fun LocalLoadoutState.removingLoadout(loadoutName: String): LocalStateUpdateAfterLoadoutRemoval =
    if (activeLoadoutName == loadoutName) {
        LocalStateUpdateAfterLoadoutRemoval.ClearCurrentLoadout
    } else {
        LocalStateUpdateAfterLoadoutRemoval.Unchanged
    }

private fun LocalStateUpdateAfterLoadoutRemoval.toDeleteResult(loadoutName: String): DeleteLoadoutResult =
    DeleteLoadoutResult(
        loadoutName = loadoutName,
        clearedCurrentLoadout = clearedCurrentLoadout,
    )
