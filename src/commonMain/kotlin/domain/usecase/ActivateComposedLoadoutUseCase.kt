package domain.usecase

import domain.entity.ComposedOutput
import domain.entity.WriteComposedFilesResult
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.RepositorySettingsRepairWarning

data class LoadoutActivation(
    /** Invalid repository settings that this activation repaired on read, without writing the repair back. */
    val repositorySettingsWarnings: List<RepositorySettingsRepairWarning>,
    val outputFiles: OutputFilesResolution,
    val writeResult: WriteComposedFilesResult,
    val ignoreRules: ConfigureGitIgnoreRulesResult,
)

class ActivateComposedLoadoutUseCase(
    private val getRepositorySettings: GetRepairableRepositorySettingsUseCase,
    private val resolveOutputFiles: ResolveOutputFilesUseCase,
    private val writeComposedFiles: WriteComposedFilesUseCase,
    private val configureGitIgnoreRules: ConfigureGitIgnoreRulesUseCase,
) {
    operator fun invoke(
        composedOutput: ComposedOutput,
        modeSelection: RepositoryModeSelection = RepositoryModeSelection.Recorded,
    ): Result<LoadoutActivation, LoadoutError> =
        getRepositorySettings().flatMap { repair ->
            resolveOutputFiles(repair.settings).flatMap { resolution ->
                writeComposedFiles(composedOutput, resolution.outputFiles).map { writeResult ->
                    LoadoutActivation(
                        repositorySettingsWarnings = repair.warnings,
                        outputFiles = resolution,
                        writeResult = writeResult,
                        ignoreRules = configureGitIgnoreRules(resolution.outputFiles, modeSelection),
                    )
                }
            }
        }
}
