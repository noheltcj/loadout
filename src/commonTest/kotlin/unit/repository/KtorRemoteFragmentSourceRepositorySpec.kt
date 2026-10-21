@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.repository

import data.repository.KtorRemoteFragmentSourceRepository
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.TrustedDocumentUrl
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecWhenContainerScope
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CountedByteReadChannel
import io.ktor.utils.io.counted
import kotlin.coroutines.cancellation.CancellationException

private const val RAW_HOST = "raw.githubusercontent.com"
private const val RAW_DOCUMENT_URL =
    "https://raw.githubusercontent.com/noheltcj/loadout/refs/heads/main/fragments/universal/" +
        "uses-unambiguous-modeling.md"

private typealias ServerBehavior = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

private data class DocumentResponse(
    val document: String,
    val status: HttpStatusCode = HttpStatusCode.OK,
    val headers: Headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=utf-8"),
) {
    fun withHeader(
        name: String,
        value: String?,
    ): DocumentResponse =
        copy(
            headers = HeadersBuilder().apply {
                appendAll(headers)
                remove(name)
                if (value != null) append(name, value)
            }.build()
        )

    fun serve(body: ByteReadChannel = ByteReadChannel(document.encodeToByteArray())): ServerBehavior =
        {
            respond(content = body, status = status, headers = headers)
        }
}

private class ObservedResponseBody(
    private val channel: CountedByteReadChannel,
) : ByteReadChannel by channel {
    constructor(bytes: ByteArray) : this(ByteReadChannel(bytes).counted())

    private var bytesReadBeforeCancellation: Long? = null

    val bytesRead: Long get() = bytesReadBeforeCancellation ?: channel.totalBytesRead

    override fun cancel(cause: Throwable?) {
        // CountedByteReadChannel clears unread buffered bytes on cancellation; snapshot actual consumption first.
        if (bytesReadBeforeCancellation == null) bytesReadBeforeCancellation = channel.totalBytesRead
        channel.cancel(cause)
    }
}

private data class DocumentFetch(
    val result: Result<ByteArray, LoadoutError>,
    val requests: List<HttpRequestData>,
    val bodyBytesRead: Long?,
) {
    fun documentBytes(): ByteArray = (result as Result.Success).value

    fun error(): LoadoutError = (result as Result.Error).error

    fun sourceRejection(): LoadoutError.InvalidFragmentSource = error() as LoadoutError.InvalidFragmentSource
}

private suspend fun fetchDocument(
    serverBehavior: ServerBehavior,
    maximumBytes: Int = 200_000,
    url: TrustedDocumentUrl = TrustedDocumentUrl(RAW_DOCUMENT_URL, RAW_HOST),
    observedBody: ObservedResponseBody? = null,
): DocumentFetch {
    val requests = mutableListOf<HttpRequestData>()
    val repository = KtorRemoteFragmentSourceRepository {
        MockEngine { request ->
            requests += request
            serverBehavior(request)
        }
    }

    return try {
        DocumentFetch(
            result = repository.fetchDocument(url, maximumBytes),
            requests = requests.toList(),
            bodyBytesRead = observedBody?.bytesRead,
        )
    } finally {
        repository.close()
    }
}

private suspend fun BehaviorSpecWhenContainerScope.thenDocumentIsRejected(
    fetch: DocumentFetch,
    sourceUrl: String,
    reason: String,
) {
    Then("it rejects the invalid document source") {
        fetch.error().shouldBeInstanceOf<LoadoutError.InvalidFragmentSource>()
    }
    Then("it identifies the requested source URL") {
        fetch.sourceRejection().source shouldBe sourceUrl
    }
    Then("it explains why the request or response was rejected") {
        fetch.sourceRejection().reason.shouldContain(reason)
    }
}

private suspend fun BehaviorSpecWhenContainerScope.thenCopyLimitIsReported(
    fetch: DocumentFetch,
    maximumBytes: Int,
) {
    Then("it rejects the oversized document") {
        fetch.error().shouldBeInstanceOf<LoadoutError.FragmentCopyTooLarge>()
    }
    Then("it reports the configured copy limit") {
        (fetch.error() as LoadoutError.FragmentCopyTooLarge).maximumBytes shouldBe maximumBytes
    }
}

class KtorRemoteFragmentSourceRepositorySpec : BehaviorSpec({
    Given("the server serves a plain UTF-8 Markdown document") {
        val response = DocumentResponse("## Remote guidance — café\n")
        val sourceUrl = TrustedDocumentUrl(RAW_DOCUMENT_URL, RAW_HOST)

        When("the document is fetched") {
            val fetch = fetchDocument(response.serve(), url = sourceUrl)

            Then("it preserves all document bytes including non-ASCII text") {
                fetch.documentBytes().toList().shouldContainExactly(response.document.encodeToByteArray().toList())
            }
            Then("it issues exactly one request") {
                fetch.requests.size shouldBe 1
            }
            Then("it requests the authorized URL") {
                fetch.requests.single().url.toString() shouldBe sourceUrl.value
            }
            Then("it uses HTTP GET") {
                fetch.requests.single().method shouldBe HttpMethod.Get
            }
            Then("it advertises support for Markdown responses") {
                fetch.requests.single().headers[HttpHeaders.Accept].orEmpty().shouldContain("text/markdown")
            }
            Then("it advertises support for plain-text responses") {
                fetch.requests.single().headers[HttpHeaders.Accept].orEmpty().shouldContain("text/plain")
            }
            Then("it requests an uncompressed response") {
                fetch.requests.single().headers[HttpHeaders.AcceptEncoding] shouldBe "identity"
            }
        }

        mapOf(
            "the content type is uppercase Markdown" to
                response.withHeader(HttpHeaders.ContentType, "TEXT/MARKDOWN"),
            "the content type is Markdown with a charset parameter" to
                response.withHeader(HttpHeaders.ContentType, "text/markdown; charset=utf-8"),
            "the content encoding explicitly uses mixed-case identity" to
                response.withHeader(HttpHeaders.ContentEncoding, "IDENTITY"),
        ).forEach { (description, variedResponse) ->
            Given(description) {
                When("the document is fetched") {
                    val fetch = fetchDocument(variedResponse.serve(), url = sourceUrl)

                    Then("it returns the original document bytes") {
                        fetch.documentBytes().toList()
                            .shouldContainExactly(variedResponse.document.encodeToByteArray().toList())
                    }
                }
            }
        }

        mapOf(
            "the URL uses uppercase host spelling" to
                sourceUrl.copy(value = sourceUrl.value.replace(RAW_HOST, "RAW.GITHUBUSERCONTENT.COM")),
            "the trusted hostname uses uppercase spelling" to sourceUrl.copy(host = RAW_HOST.uppercase()),
        ).forEach { (description, variedUrl) ->
            Given(description) {
                When("the document is fetched") {
                    val fetch = fetchDocument(response.serve(), url = variedUrl)

                    Then("it matches the trusted host case-insensitively") {
                        fetch.documentBytes().toList().shouldContainExactly(
                            response.document.encodeToByteArray().toList()
                        )
                    }
                }
            }
        }

        mapOf(
            "the server responds with HTML" to response.withHeader(HttpHeaders.ContentType, "text/html"),
            "the server omits the content type" to response.withHeader(HttpHeaders.ContentType, null),
            "the server responds with binary content" to
                response.withHeader(HttpHeaders.ContentType, "application/octet-stream"),
        ).forEach { (description, invalidResponse) ->
            Given(description) {
                When("the document is fetched") {
                    val fetch = fetchDocument(invalidResponse.serve(), url = sourceUrl)

                    thenDocumentIsRejected(fetch, sourceUrl.value, "Expected a plain Markdown document")
                }
            }
        }

        Given("the server responds with a compressed body") {
            val compressedResponse = response.withHeader(HttpHeaders.ContentEncoding, "gzip")

            When("the document is fetched") {
                val fetch = fetchDocument(compressedResponse.serve(), url = sourceUrl)

                thenDocumentIsRejected(fetch, sourceUrl.value, "Encoded responses are not supported")
            }
        }

        Given("the server responds with an HTTP error") {
            val errorResponse = response.copy(status = HttpStatusCode.InternalServerError)

            When("the document is fetched") {
                val fetch = fetchDocument(errorResponse.serve(), url = sourceUrl)

                thenDocumentIsRejected(fetch, sourceUrl.value, "HTTP 500")
            }
        }

        Given("the server redirects to a different host") {
            val redirectResponse = response.copy(status = HttpStatusCode.Found)
                .withHeader(HttpHeaders.Location, "https://example.com/other.md")

            When("the document is fetched") {
                val fetch = fetchDocument(redirectResponse.serve(), url = sourceUrl)

                thenDocumentIsRejected(fetch, sourceUrl.value, "HTTP 302")

                Then("it issues no request to the redirect target") {
                    fetch.requests.map { it.url.toString() }.shouldContainExactly(sourceUrl.value)
                }
            }
        }

        mapOf(
            "the URL targets a different host" to sourceUrl.copy(value = "https://example.com/guidance.md"),
            "the URL uses HTTP" to sourceUrl.copy(value = sourceUrl.value.replace("https://", "http://")),
            "the URL uses a nonstandard HTTPS port" to
                sourceUrl.copy(value = sourceUrl.value.replace(RAW_HOST, "$RAW_HOST:8443")),
        ).forEach { (description, invalidUrl) ->
            Given(description) {
                When("the document is fetched") {
                    val fetch = fetchDocument(response.serve(), url = invalidUrl)

                    thenDocumentIsRejected(fetch, invalidUrl.value, "does not target trusted host '$RAW_HOST'")

                    Then("it issues no network request") {
                        fetch.requests.size shouldBe 0
                    }
                }
            }
        }

        Given("the network request fails") {
            val requestFailure = IllegalStateException("connection failed")
            val failingServer: ServerBehavior = { throw requestFailure }

            When("the document is fetched") {
                val fetch = fetchDocument(failingServer, url = sourceUrl)

                thenDocumentIsRejected(fetch, sourceUrl.value, "Request failed: connection failed")

                Then("it preserves the failure cause") {
                    fetch.sourceRejection().cause shouldBe requestFailure
                }
            }
        }

        Given("the network request times out") {
            val requestFailure = HttpRequestTimeoutException(sourceUrl.value, 10_000)
            val failingServer: ServerBehavior = { throw requestFailure }

            When("the document is fetched") {
                val fetch = fetchDocument(failingServer, url = sourceUrl)

                thenDocumentIsRejected(fetch, sourceUrl.value, "Request timed out")

                Then("it preserves the timeout cause") {
                    fetch.sourceRejection().cause shouldBe requestFailure
                }
            }
        }

        Given("the network request is cancelled") {
            val cancellation = CancellationException("fetch cancelled")
            val cancellingServer: ServerBehavior = { throw cancellation }

            When("the document is fetched") {
                val execution = runCatching { fetchDocument(cancellingServer, url = sourceUrl) }

                Then("it propagates cancellation instead of returning a domain error") {
                    execution.exceptionOrNull().shouldBeInstanceOf<CancellationException>()
                }
                Then("it preserves the cancellation reason") {
                    execution.exceptionOrNull()?.message shouldBe "fetch cancelled"
                }
            }
        }
    }

    Given("the maximum document size is 16 bytes") {
        val maximumBytes = 16
        val response = DocumentResponse("x".repeat(maximumBytes))

        When("a document exactly at the limit is fetched") {
            val fetch = fetchDocument(response.serve(), maximumBytes)

            Then("it returns all document bytes without truncation") {
                fetch.documentBytes().toList().shouldContainExactly(response.document.encodeToByteArray().toList())
            }
        }

        Given("the document is empty") {
            val emptyResponse = response.copy(document = "")

            When("the document is fetched") {
                val fetch = fetchDocument(emptyResponse.serve(), maximumBytes)

                Then("it returns an empty body for the caller to validate") {
                    fetch.documentBytes().size shouldBe 0
                }
            }
        }

        Given("the server declares a Content-Length above the limit") {
            val oversizedResponse = response.copy(document = "x".repeat(64))
                .withHeader(HttpHeaders.ContentLength, "64")
            val body = ObservedResponseBody(oversizedResponse.document.encodeToByteArray())

            When("the document is fetched") {
                val fetch = fetchDocument(oversizedResponse.serve(body), maximumBytes, observedBody = body)

                thenCopyLimitIsReported(fetch, maximumBytes)

                Then("it rejects the advertised size without reading the body") {
                    fetch.bodyBytesRead shouldBe 0L
                }
            }
        }

        Given("the server streams a body above the limit without declaring its length") {
            val oversizedResponse = response.copy(document = "x".repeat(4096))
            val body = ObservedResponseBody(oversizedResponse.document.encodeToByteArray())

            When("the document is fetched") {
                val fetch = fetchDocument(oversizedResponse.serve(body), maximumBytes, observedBody = body)

                thenCopyLimitIsReported(fetch, maximumBytes)

                Then("it stops reading as soon as the body exceeds the limit") {
                    (fetch.bodyBytesRead ?: error("Missing body observation"))
                        .shouldBeLessThanOrEqual(maximumBytes.toLong() + 1)
                }
            }

            Given("the server advertises a smaller Content-Length than its body") {
                val misleadingResponse = oversizedResponse.withHeader(HttpHeaders.ContentLength, "8")

                When("the document is fetched") {
                    val fetch = fetchDocument(misleadingResponse.serve(), maximumBytes)

                    thenCopyLimitIsReported(fetch, maximumBytes)
                }
            }
        }
    }
})
