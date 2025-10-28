package domain.repository

import domain.entity.Fragment
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface FragmentRepository {
    fun findByPath(path: String): Result<Fragment?, LoadoutError>

    fun findAll(): Result<List<Fragment>, LoadoutError>

    fun loadContent(path: String): Result<String, LoadoutError>
}
