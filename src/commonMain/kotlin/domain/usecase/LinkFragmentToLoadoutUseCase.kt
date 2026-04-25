package domain.usecase

import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.normalizeFragmentPath
import domain.policy.normalizeStoredLoadout
import domain.policy.validateMarkdownFragmentPath
import domain.repository.EnvironmentRepository
import domain.repository.FragmentRepository
import domain.repository.LoadoutRepository

class LinkFragmentToLoadoutUseCase(
    private val fragmentRepository: FragmentRepository,
    private val environmentRepository: EnvironmentRepository,
    private val getLoadout: GetLoadoutUseCase,
    private val loadoutRepository: LoadoutRepository,
) {
    operator fun invoke(
        loadoutName: String,
        fragmentPath: String,
        afterFragment: String? = null,
    ): Result<Loadout, LoadoutError> {
        val now = environmentRepository.currentTimeMillis()

        return validateMarkdownFragmentPath(fragmentPath)
            .flatMap { normalizedFragmentPath ->
                ensureFragmentExists(normalizedFragmentPath)
                    .map { normalizedFragmentPath to afterFragment?.let(::normalizeFragmentPath) }
            }
            .flatMap { (normalizedFragmentPath, normalizedAfterFragment) ->
                getLoadout(loadoutName)
                    .flatMap { loadout ->
                        val normalizedLoadout = normalizeStoredLoadout(loadout)

                        if (normalizedFragmentPath in normalizedLoadout.fragments) {
                            Result.Error(
                                LoadoutError.FragmentAlreadyInLoadout(
                                    normalizedFragmentPath,
                                    loadoutName,
                                )
                            )
                        } else {
                            Result.Success(
                                normalizedLoadout.addFragment(
                                    fragmentPath = normalizedFragmentPath,
                                    afterFragment = normalizedAfterFragment,
                                    currentTimeMillis = now,
                                )
                            )
                        }
                    }.flatMap { updatedLoadout ->
                        loadoutRepository.save(updatedLoadout).map { updatedLoadout }
                    }
            }
    }

    private fun ensureFragmentExists(fragmentPath: String): Result<Unit, LoadoutError> =
        when (val result = fragmentRepository.findByPath(fragmentPath)) {
            is Result.Success ->
                if (result.value == null) {
                    Result.Error(LoadoutError.FragmentNotFound(fragmentPath))
                } else {
                    Result.Success(Unit)
                }

            is Result.Error -> result
        }
}
