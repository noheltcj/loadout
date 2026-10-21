package data.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.TrustedDocumentUrl
import domain.repository.RemoteFragmentSourceRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlin.coroutines.cancellation.CancellationException

private val MARKDOWN_CONTENT_TYPE = ContentType("text", "markdown")
private const val HTTPS_PORT = 443

class KtorRemoteFragmentSourceRepository(
    createEngine: () -> HttpClientEngine,
) : RemoteFragmentSourceRepository, AutoCloseable {
    private val engine = lazy(createEngine)
    private val httpClient =
        lazy {
            HttpClient(engine.value) {
                // The host allowlist only authorizes the requested URL, not redirect targets.
                followRedirects = false
                // Channel transformation copies the response ahead of the bounded reader.
                useDefaultTransformers = false
                install(HttpTimeout) {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = 10_000
                    socketTimeoutMillis = 5_000
                }
            }
        }

    override suspend fun fetchDocument(
        url: TrustedDocumentUrl,
        maximumBytes: Int,
    ): Result<ByteArray, LoadoutError> =
        try {
            val requestUrl = Url(url.value)
            if (!requestUrl.targets(url)) {
                Result.Error(
                    LoadoutError.InvalidFragmentSource(url.value, "URL does not target trusted host '${url.host}'")
                )
            } else {
                httpClient.value.prepareGet(requestUrl) {
                    headers.append(HttpHeaders.Accept, "text/markdown, text/plain;q=0.9")
                    headers.append(HttpHeaders.AcceptEncoding, "identity")
                }.execute { response ->
                    validateResponse(url.value, response, maximumBytes)
                }
            }
        } catch (error: HttpRequestTimeoutException) {
            Result.Error(LoadoutError.InvalidFragmentSource(url.value, "Request timed out", error))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.Error(LoadoutError.InvalidFragmentSource(url.value, "Request failed: ${error.message}", error))
        }

    override fun close() {
        if (httpClient.isInitialized()) httpClient.value.close()
        if (engine.isInitialized()) engine.value.close()
    }

    private fun Url.targets(trustedUrl: TrustedDocumentUrl): Boolean =
        protocol == URLProtocol.HTTPS &&
            port == HTTPS_PORT &&
            host.equals(trustedUrl.host, ignoreCase = true)

    private suspend fun validateResponse(
        source: String,
        response: HttpResponse,
        maximumBytes: Int,
    ): Result<ByteArray, LoadoutError> {
        if (response.status != HttpStatusCode.OK) {
            return Result.Error(
                LoadoutError.InvalidFragmentSource(source, "Server returned HTTP ${response.status.value}")
            )
        }

        val contentType = response.contentType()?.withoutParameters()
        if (contentType != ContentType.Text.Plain && contentType != MARKDOWN_CONTENT_TYPE) {
            val detail = contentType?.toString() ?: "missing content type"
            return Result.Error(
                LoadoutError.InvalidFragmentSource(source, "Expected a plain Markdown document, received $detail")
            )
        }

        val contentEncoding = response.headers[HttpHeaders.ContentEncoding]
        if (contentEncoding != null && !contentEncoding.equals("identity", ignoreCase = true)) {
            return Result.Error(
                LoadoutError.InvalidFragmentSource(source, "Encoded responses are not supported")
            )
        }

        // Content-Length is advisory; readBoundedBody enforces the limit.
        val advertisedLength = response.contentLength()
        if (advertisedLength != null && advertisedLength > maximumBytes) {
            return Result.Error(LoadoutError.FragmentCopyTooLarge(maximumBytes))
        }

        return readBoundedBody(response, maximumBytes)
    }

    private suspend fun readBoundedBody(
        response: HttpResponse,
        maximumBytes: Int,
    ): Result<ByteArray, LoadoutError> {
        val channel = response.bodyAsChannel()
        val document = ByteArray(maximumBytes + 1)
        var documentSize = 0

        while (documentSize < document.size) {
            val bytesRead = channel.readAvailable(document, documentSize, document.size - documentSize)
            if (bytesRead < 0) break
            documentSize += bytesRead
        }

        return if (documentSize > maximumBytes) {
            Result.Error(LoadoutError.FragmentCopyTooLarge(maximumBytes))
        } else {
            Result.Success(document.copyOf(documentSize))
        }
    }
}
