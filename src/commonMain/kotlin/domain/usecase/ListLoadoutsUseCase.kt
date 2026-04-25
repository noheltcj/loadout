package domain.usecase

import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.LoadoutRepository

class ListLoadoutsUseCase(
    private val loadoutRepository: LoadoutRepository,
) {
    operator fun invoke(): Result<List<Loadout>, LoadoutError> = loadoutRepository.findAll()
}
