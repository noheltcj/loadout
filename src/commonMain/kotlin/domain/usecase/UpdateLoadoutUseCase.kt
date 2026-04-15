package domain.usecase

import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.LoadoutRepository

class UpdateLoadoutUseCase(
    private val loadoutRepository: LoadoutRepository,
) {
    operator fun invoke(loadout: Loadout): Result<Loadout, LoadoutError> {
        if (!loadoutRepository.exists(loadout.name)) {
            return Result.Error(LoadoutError.LoadoutNotFound(loadout.name))
        }

        val validationErrors = loadout.validate()
        if (validationErrors.isNotEmpty()) {
            return Result.Error(LoadoutError.ValidationError("loadout", validationErrors.first()))
        }

        return loadoutRepository.save(loadout).map { loadout }
    }
}
