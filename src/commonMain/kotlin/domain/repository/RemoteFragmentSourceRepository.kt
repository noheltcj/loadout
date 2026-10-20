package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.TrustedDocumentUrl

interface RemoteFragmentSourceRepository {
    /** Implementations must stop reading once the body exceeds [maximumBytes]. */
    suspend fun fetchDocument(
        url: TrustedDocumentUrl,
        maximumBytes: Int,
    ): Result<ByteArray, LoadoutError>
}
