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
            localLoadoutStateRepository.loadLocalState().flatMap { state ->
                val clearedCurrentLoadout = state.activeLoadoutName == validName

                loadoutRepository.delete(validName).flatMap {
                    if (clearedCurrentLoadout) {
                        localLoadoutStateRepository.saveLocalState(
                            LocalLoadoutState(
                                activeLoadoutName = null,
                                lastComposedContentHash = null,
                            )
                        )
                    } else {
                        Result.Success(Unit)
                    }
                }.map {
                    DeleteLoadoutResult(
                        loadoutName = validName,
                        clearedCurrentLoadout = clearedCurrentLoadout,
                    )
                }
            }
        }
}
