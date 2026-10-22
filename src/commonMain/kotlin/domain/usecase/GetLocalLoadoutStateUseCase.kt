package domain.usecase

import domain.entity.LocalLoadoutState
import domain.entity.error.LoadoutError
import domain.repository.LocalLoadoutStateRepository

sealed interface LocalLoadoutStateReading {
    val state: LocalLoadoutState

    data class Readable(
        override val state: LocalLoadoutState,
    ) : LocalLoadoutStateReading

    data class Unreadable(
        val error: LoadoutError.UnreadableLocalState,
    ) : LocalLoadoutStateReading {
        override val state: LocalLoadoutState
            get() = LocalLoadoutState.Inactive
    }
}

/**
 * Local state is runtime state rather than a source of truth, so state that cannot be read resets the worktree to
 * inactive instead of failing the command.
 */
class GetLocalLoadoutStateUseCase(
    private val localLoadoutStateRepository: LocalLoadoutStateRepository,
) {
    operator fun invoke(): LocalLoadoutStateReading =
        localLoadoutStateRepository.loadLocalState().fold(
            onSuccess = { state -> LocalLoadoutStateReading.Readable(state) },
            onError = { error -> LocalLoadoutStateReading.Unreadable(error) },
        )
}
