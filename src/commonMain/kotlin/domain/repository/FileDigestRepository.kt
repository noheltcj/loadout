package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface FileDigestRepository {
    /**
     * A cryptographic digest of the exact bytes in [path], following symbolic links. Two files share a digest only
     * when they hold the same bytes.
     */
    fun digestFile(path: String): Result<String, LoadoutError>
}
