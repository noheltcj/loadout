package domain.usecase

import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.validateLoadoutName
import domain.repository.LoadoutRepository

class GetLoadoutUseCase(
    private val loadoutRepository: LoadoutRepository,
) {
    operator fun invoke(name: String): Result<Loadout, LoadoutError> =
        validateLoadoutName(name).flatMap { validName ->
            loadoutRepository
                .findByName(validName)
                .flatMap { loadout ->
                    loadout
                        ?.let { Result.Success(it) }
                        ?: Result.Error(LoadoutError.LoadoutNotFound(validName))
                }
        }
}
