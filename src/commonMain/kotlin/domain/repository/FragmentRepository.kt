package domain.repository

import domain.entity.packaging.Result
import domain.entity.Fragment
import domain.entity.error.LoadoutError

interface FragmentRepository {
    fun findByPath(path: String): Result<Fragment?, LoadoutError>
    fun findAll(): Result<List<Fragment>, LoadoutError>
    fun loadContent(path: String): Result<String, LoadoutError>
}