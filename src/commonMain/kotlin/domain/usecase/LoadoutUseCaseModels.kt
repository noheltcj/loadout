package domain.usecase

import domain.entity.Loadout

sealed interface LoadoutOutputTarget {
    data object StandardOutput : LoadoutOutputTarget

    data class FileSystem(
        val outputPaths: List<String>,
    ) : LoadoutOutputTarget
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
    data object Synchronized : LoadoutSyncState
    data object OutOfSync : LoadoutSyncState
}
