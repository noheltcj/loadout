package domain.usecase

import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.normalizeFragmentPath
import domain.policy.normalizeStoredLoadout
import domain.repository.EnvironmentRepository

class UnlinkFragmentFromLoadoutUseCase(
    private val environmentRepository: EnvironmentRepository,
    private val getLoadout: GetLoadoutUseCase,
    private val updateLoadout: UpdateLoadoutUseCase,
) {
    operator fun invoke(
        loadoutName: String,
        fragmentPath: String,
    ): Result<Loadout, LoadoutError> {
        val now = environmentRepository.currentTimeMillis()
        val normalizedFragmentPath = normalizeFragmentPath(fragmentPath)

        return getLoadout(loadoutName)
            .flatMap { loadout ->
                val normalizedLoadout = normalizeStoredLoadout(loadout)

                if (normalizedFragmentPath !in normalizedLoadout.fragments) {
                    Result.Error(LoadoutError.FragmentNotInLoadout(normalizedFragmentPath, loadoutName))
                } else {
                    Result.Success(normalizedLoadout.removeFragment(normalizedFragmentPath, now))
                }
            }
            .flatMap(updateLoadout::invoke)
    }
}
