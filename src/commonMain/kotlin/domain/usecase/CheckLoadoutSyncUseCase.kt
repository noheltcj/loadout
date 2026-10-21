package domain.usecase

import domain.entity.LocalLoadoutState
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.OutputFilePath

/** Reports what the next sync would change: the composed fragments, the set of output files, or both. */
class CheckLoadoutSyncUseCase(
    private val getLocalLoadoutState: GetLocalLoadoutStateUseCase,
    private val getLoadout: GetLoadoutUseCase,
    private val composeLoadout: ComposeLoadoutUseCase,
    private val getRepositorySettings: GetRepairableRepositorySettingsUseCase,
    private val resolveOutputFiles: ResolveOutputFilesUseCase,
) {
    operator fun invoke(): Result<LoadoutSyncState, LoadoutError> =
        when (val reading = getLocalLoadoutState()) {
            is LocalLoadoutStateReading.Unreadable ->
                Result.Success(LoadoutSyncState.UnreadableLocalState(reading.error))

            is LocalLoadoutStateReading.Readable ->
                when (val state = reading.state) {
                    LocalLoadoutState.Inactive -> Result.Success(LoadoutSyncState.NoCurrentLoadout)
                    is LocalLoadoutState.Active -> checkActiveLoadout(state)
                }
        }

    private fun checkActiveLoadout(state: LocalLoadoutState.Active): Result<LoadoutSyncState, LoadoutError> =
        getLoadout(state.loadoutName)
            .flatMap { loadout -> composeLoadout(loadout) }
            .flatMap { composedOutput ->
                getRepositorySettings()
                    .flatMap { repair -> resolveOutputFiles(repair.settings) }
                    .map { resolution ->
                        LoadoutSyncState.Active(
                            fragments =
                            if (composedOutput.metadata.contentHash == state.lastWrite.composedContentHash) {
                                FragmentsSyncState.UpToDate
                            } else {
                                FragmentsSyncState.ChangedSinceLastComposition
                            },
                            outputFiles = outputFilesSyncState(state.lastWrite.outputFiles, resolution.outputFiles),
                        )
                    }
            }
}

private fun outputFilesSyncState(
    writtenOutputFiles: Set<String>,
    outputFiles: List<OutputFilePath>,
): OutputFilesSyncState {
    val configuredOutputFiles = outputFiles.map(OutputFilePath::value)
    val addedOutputFiles = configuredOutputFiles.filterNot { outputFile -> outputFile in writtenOutputFiles }
    val removedOutputFiles = writtenOutputFiles.filterNot { outputFile -> outputFile in configuredOutputFiles }

    return if (addedOutputFiles.isEmpty() && removedOutputFiles.isEmpty()) {
        OutputFilesSyncState.UpToDate
    } else {
        OutputFilesSyncState.ChangedSinceLastWrite(addedOutputFiles, removedOutputFiles)
    }
}
