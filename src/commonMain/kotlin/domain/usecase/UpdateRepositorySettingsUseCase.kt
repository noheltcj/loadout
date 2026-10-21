package domain.usecase

import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.RepositorySettingsRepair
import domain.policy.validateFragmentSourceHost
import domain.policy.validateLoadoutName
import domain.policy.validateRepositorySettings
import domain.repository.LoadoutRepository
import domain.repository.RepositorySettingsRepository

sealed interface RepositorySettingsUpdateInput {
    data class DefaultLoadout(val name: String?) : RepositorySettingsUpdateInput

    data class FragmentsHome(val path: String) : RepositorySettingsUpdateInput

    data class TrustFragmentSourceHost(val host: String) : RepositorySettingsUpdateInput

    data class UntrustFragmentSourceHost(val host: String) : RepositorySettingsUpdateInput

    data class MaximumFragmentCopyBytes(val bytes: Int) : RepositorySettingsUpdateInput
}

class UpdateRepositorySettingsUseCase(
    private val repositorySettingsRepository: RepositorySettingsRepository,
    private val loadoutRepository: LoadoutRepository,
    private val getRepairableRepositorySettings: GetRepairableRepositorySettingsUseCase,
) {
    operator fun invoke(input: RepositorySettingsUpdateInput): Result<RepositorySettingsRepair, LoadoutError> =
        getRepairableRepositorySettings().flatMap { repair ->
            val currentSettings = repair.settings
            when (input) {
                is RepositorySettingsUpdateInput.DefaultLoadout -> updateDefaultLoadout(currentSettings, input.name)
                is RepositorySettingsUpdateInput.FragmentsHome -> updateFragmentsHome(currentSettings, input.path)
                is RepositorySettingsUpdateInput.TrustFragmentSourceHost ->
                    trustFragmentSourceHost(currentSettings, input.host)
                is RepositorySettingsUpdateInput.UntrustFragmentSourceHost ->
                    untrustFragmentSourceHost(currentSettings, input.host)
                is RepositorySettingsUpdateInput.MaximumFragmentCopyBytes ->
                    updateMaximumFragmentCopyBytes(currentSettings, input.bytes)
            }.map { updatedSettings -> repair.copy(settings = updatedSettings) }
        }

    private fun updateDefaultLoadout(
        currentSettings: RepositorySettings,
        defaultLoadoutName: String?,
    ): Result<RepositorySettings, LoadoutError> =
        if (defaultLoadoutName == null) {
            save(currentSettings.copy(defaultLoadoutName = null))
        } else {
            validateLoadoutName(defaultLoadoutName).flatMap { validName ->
                if (!loadoutRepository.exists(validName)) {
                    Result.Error(LoadoutError.LoadoutNotFound(validName))
                } else {
                    save(currentSettings.copy(defaultLoadoutName = validName))
                }
            }
        }

    private fun updateFragmentsHome(
        currentSettings: RepositorySettings,
        path: String,
    ): Result<RepositorySettings, LoadoutError> = save(currentSettings.copy(fragmentsHome = path))

    private fun trustFragmentSourceHost(
        currentSettings: RepositorySettings,
        host: String,
    ): Result<RepositorySettings, LoadoutError> =
        save(
            currentSettings.copy(
                trustedFragmentSourceHosts = currentSettings.trustedFragmentSourceHosts + host,
            )
        )

    private fun untrustFragmentSourceHost(
        currentSettings: RepositorySettings,
        host: String,
    ): Result<RepositorySettings, LoadoutError> =
        validateFragmentSourceHost(host).flatMap { validHost ->
            save(
                currentSettings.copy(
                    trustedFragmentSourceHosts =
                    currentSettings.trustedFragmentSourceHosts.filterNot {
                        it.equals(validHost, ignoreCase = true)
                    }
                )
            )
        }

    private fun updateMaximumFragmentCopyBytes(
        currentSettings: RepositorySettings,
        bytes: Int,
    ): Result<RepositorySettings, LoadoutError> = save(currentSettings.copy(maximumFragmentCopyBytes = bytes))

    private fun save(settings: RepositorySettings): Result<RepositorySettings, LoadoutError> =
        validateRepositorySettings(settings).flatMap { validSettings ->
            repositorySettingsRepository.saveRepositorySettings(validSettings).map { validSettings }
        }
}
