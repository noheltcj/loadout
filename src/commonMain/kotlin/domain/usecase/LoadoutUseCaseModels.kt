package domain.usecase

import domain.entity.Loadout
import domain.entity.error.LoadoutError

sealed interface LoadoutOutputTarget {
    data object StandardOutput : LoadoutOutputTarget

    data object ConfiguredOutputFiles : LoadoutOutputTarget
}

sealed interface CurrentLoadoutSelection {
    data object None : CurrentLoadoutSelection

    data class Selected(
        val loadout: Loadout,
    ) : CurrentLoadoutSelection
}

inline fun <T> CurrentLoadoutSelection.fold(
    onNone: () -> T,
    onSelected: (Loadout) -> T,
): T =
    when (this) {
        CurrentLoadoutSelection.None -> onNone()
        is CurrentLoadoutSelection.Selected -> onSelected(loadout)
    }

sealed interface LoadoutSyncState {
    data object NoCurrentLoadout : LoadoutSyncState

    data class UnreadableLocalState(
        val error: LoadoutError.UnreadableLocalState,
    ) : LoadoutSyncState

    data class Active(
        val fragments: FragmentsSyncState,
        val outputFiles: OutputFilesSyncState,
    ) : LoadoutSyncState
}

sealed interface FragmentsSyncState {
    data object UpToDate : FragmentsSyncState

    data object ChangedSinceLastComposition : FragmentsSyncState
}

sealed interface OutputFilesSyncState {
    data object UpToDate : OutputFilesSyncState

    data class ChangedSinceLastWrite(
        val addedOutputFiles: List<String>,
        val removedOutputFiles: List<String>,
    ) : OutputFilesSyncState
}
