package data.repository

import data.digest.sha256Hex
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileDigestRepository
import domain.repository.FileQueryRepository

class Sha256FileDigestRepository(
    private val fileQueryRepository: FileQueryRepository,
) : FileDigestRepository {
    override fun digestFile(path: String): Result<String, LoadoutError> =
        fileQueryRepository.readFileBytes(path).map(::sha256Hex)
}
