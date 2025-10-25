package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.Loadout
import domain.entity.packaging.Result

interface LoadoutRepository {
    fun findAll(): Result<List<Loadout>, LoadoutError>
    fun findByName(name: String): Result<Loadout?, LoadoutError>
    fun save(loadout: Loadout): Result<Unit, LoadoutError>
    fun delete(name: String): Result<Unit, LoadoutError>
    fun exists(name: String): Boolean
}