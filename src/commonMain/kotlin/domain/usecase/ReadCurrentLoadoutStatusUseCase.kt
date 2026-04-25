package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

sealed interface CurrentLoadoutStatus {
    data object NoCurrentLoadout : CurrentLoadoutStatus

    data class Active(
        val loadout: Loadout,
        val composedOutput: ComposedOutput,
    ) : CurrentLoadoutStatus
}

class ReadCurrentLoadoutStatusUseCase(
    private val getCurrentLoadout: GetCurrentLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
) {
    operator fun invoke(): Result<CurrentLoadoutStatus, LoadoutError> =
        getCurrentLoadout().flatMap { selection ->
            selection.fold(
                onNone = { Result.Success(CurrentLoadoutStatus.NoCurrentLoadout) },
                onSelected = { loadout ->
                    composeLoadout(loadout).map { composedOutput ->
                        CurrentLoadoutStatus.Active(loadout, composedOutput)
                    }
                },
            )
        }
}
