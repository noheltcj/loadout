@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.policy

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.AuthorizedFragmentSource
import domain.policy.FragmentSource
import domain.policy.authorizeFragmentSource
import domain.policy.resolveFragmentSource
import domain.policy.validateFragmentDocumentSize
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecWhenContainerScope
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

private const val RAW_HOST = "raw.githubusercontent.com"
private const val RAW_DOCUMENT_URL =
    "https://raw.githubusercontent.com/noheltcj/loadout/refs/heads/main/fragments/universal/" +
        "uses-unambiguous-modeling.md"
private const val GITHUB_BLOB_PAGE_URL =
    "https://github.com/noheltcj/loadout/blob/main/fragments/universal/uses-unambiguous-modeling.md"

private fun <T> Result<T, LoadoutError>.accepted(): T = (this as Result.Success).value

private fun <T> Result<T, LoadoutError>.error(): LoadoutError = (this as Result.Error).error

private suspend fun BehaviorSpecWhenContainerScope.thenRemoteDocumentIsResolved(
    resolution: Result<FragmentSource, LoadoutError>,
    sourceUrl: String,
) {
    Then("it identifies a remote document") {
        resolution.accepted().shouldBeInstanceOf<FragmentSource.RemoteDocument>()
    }
    Then("it preserves the supplied document URL") {
        resolution.accepted().location shouldBe sourceUrl
    }
    Then("it records the normalized source host") {
        (resolution.accepted() as FragmentSource.RemoteDocument).host shouldBe RAW_HOST
    }
}

private suspend fun BehaviorSpecWhenContainerScope.thenSourceIsRejected(
    resolution: Result<FragmentSource, LoadoutError>,
    source: String,
    reason: String,
) {
    Then("it rejects the invalid fragment source") {
        resolution.error().shouldBeInstanceOf<LoadoutError.InvalidFragmentSource>()
    }
    Then("it identifies the rejected source") {
        (resolution.error() as LoadoutError.InvalidFragmentSource).source shouldBe source
    }
    Then("it explains why the source is invalid") {
        (resolution.error() as LoadoutError.InvalidFragmentSource).reason.shouldContain(reason)
    }
}

private suspend fun BehaviorSpecWhenContainerScope.thenRemoteDocumentIsAuthorized(
    authorization: Result<AuthorizedFragmentSource, LoadoutError>,
    source: FragmentSource.RemoteDocument,
) {
    Then("it authorizes a remote document") {
        authorization.accepted().shouldBeInstanceOf<AuthorizedFragmentSource.RemoteDocument>()
    }
    Then("it preserves the document URL in the trusted URL") {
        (authorization.accepted() as AuthorizedFragmentSource.RemoteDocument).url.value shouldBe source.location
    }
    Then("it associates the trusted URL with the source host") {
        (authorization.accepted() as AuthorizedFragmentSource.RemoteDocument).url.host shouldBe source.host
    }
    Then("it exposes the original source location") {
        authorization.accepted().location shouldBe source.location
    }
}

class FragmentSourceSpec : BehaviorSpec({
    Given("a local Markdown source path") {
        val sourcePath = "fragments/guidance.md"

        When("the fragment source is resolved") {
            val resolution = resolveFragmentSource(sourcePath)

            Then("it identifies a local Markdown file") {
                resolution.accepted().shouldBeInstanceOf<FragmentSource.LocalMarkdownFile>()
            }
            Then("it preserves the source path") {
                resolution.accepted().location shouldBe sourcePath
            }
        }

        Given("the Markdown extension is changed to uppercase") {
            val uppercasePath = sourcePath.removeSuffix(".md") + ".MD"

            When("the fragment source is resolved") {
                val resolution = resolveFragmentSource(uppercasePath)

                Then("it accepts the uppercase Markdown extension") {
                    resolution.accepted().shouldBeInstanceOf<FragmentSource.LocalMarkdownFile>()
                }
                Then("it preserves the original path casing") {
                    resolution.accepted().location shouldBe uppercasePath
                }
            }
        }

        Given("the local source has been resolved and no remote hosts are trusted") {
            val localSource = resolveFragmentSource(sourcePath).accepted()
            val trustedHosts = emptyList<String>()

            When("the local fragment source is authorized") {
                val authorization = authorizeFragmentSource(localSource, trustedHosts)

                Then("it authorizes the local file without requiring a trusted host") {
                    authorization.accepted().shouldBeInstanceOf<AuthorizedFragmentSource.LocalMarkdownFile>()
                }
                Then("it preserves the local source path") {
                    authorization.accepted().location shouldBe sourcePath
                }
            }
        }
    }

    Given("a local source path without a Markdown extension") {
        val sourcePath = "notes.txt"

        When("the fragment source is resolved") {
            val resolution = resolveFragmentSource(sourcePath)

            thenSourceIsRejected(resolution, sourcePath, "Local source must be a Markdown file")
        }
    }

    Given("a remote Markdown document URL") {
        val documentUrl = RAW_DOCUMENT_URL

        When("the fragment source is resolved") {
            val resolution = resolveFragmentSource(documentUrl)

            thenRemoteDocumentIsResolved(resolution, documentUrl)
        }

        val acceptedUrlVariations = mapOf(
            "the host is changed to mixed case" to documentUrl.replace(RAW_HOST, "RAW.GitHubUserContent.com"),
            "the HTTPS scheme is changed to uppercase" to documentUrl.replace("https://", "HTTPS://"),
            "the standard HTTPS port is explicit" to documentUrl.replace(RAW_HOST, "$RAW_HOST:443"),
            "the Markdown extension is changed to uppercase" to documentUrl.removeSuffix(".md") + ".MD",
            "a query and fragment are appended" to "$documentUrl?download=1#guidance",
        )
        acceptedUrlVariations.forEach { (description, sourceUrl) ->
            Given(description) {
                When("the fragment source is resolved") {
                    val resolution = resolveFragmentSource(sourceUrl)

                    thenRemoteDocumentIsResolved(resolution, sourceUrl)
                }
            }
        }

        val rejectedUrlVariations = listOf(
            Triple(
                "the scheme is changed to HTTP",
                documentUrl.replace("https://", "http://"),
                "Only local paths and HTTPS URLs are supported",
            ),
            Triple(
                "the port is changed to 8443",
                documentUrl.replace(RAW_HOST, "$RAW_HOST:8443"),
                "Only HTTPS port 443 is supported",
            ),
            Triple(
                "credentials are embedded in the URL",
                documentUrl.replace("https://", "https://user:secret@"),
                "URL credentials are not supported",
            ),
            Triple(
                "the URL port is not numeric",
                documentUrl.replace(RAW_HOST, "$RAW_HOST:invalid"),
                "URL is malformed",
            ),
            Triple(
                "the Markdown extension is removed",
                documentUrl.removeSuffix(".md"),
                "URL path must identify a Markdown file",
            ),
        )
        rejectedUrlVariations.forEach { (description, sourceUrl, reason) ->
            Given(description) {
                When("the fragment source is resolved") {
                    val resolution = resolveFragmentSource(sourceUrl)

                    thenSourceIsRejected(resolution, sourceUrl, reason)
                }
            }
        }

        Given("the remote source has been resolved") {
            val remoteSource = resolveFragmentSource(documentUrl).accepted() as FragmentSource.RemoteDocument

            Given("the source host is on the allowlist") {
                val trustedHosts = listOf(remoteSource.host)

                When("the fragment source is authorized") {
                    val authorization = authorizeFragmentSource(remoteSource, trustedHosts)

                    thenRemoteDocumentIsAuthorized(authorization, remoteSource)
                }

                Given("the allowlisted host is changed to mixed case") {
                    val mixedCaseTrustedHosts = trustedHosts.map { it.replace("raw.", "RAW.") }

                    When("the fragment source is authorized") {
                        val authorization = authorizeFragmentSource(remoteSource, mixedCaseTrustedHosts)

                        thenRemoteDocumentIsAuthorized(authorization, remoteSource)
                    }
                }
            }

            mapOf(
                "only another host is trusted" to listOf("example.com"),
                "only the parent domain of the source host is trusted" to listOf("githubusercontent.com"),
                "the host allowlist is empty" to emptyList(),
            ).forEach { (description, trustedHosts) ->
                Given(description) {
                    When("the fragment source is authorized") {
                        val authorization = authorizeFragmentSource(remoteSource, trustedHosts)

                        Then("it rejects an untrusted source host") {
                            authorization.error().shouldBeInstanceOf<LoadoutError.UntrustedFragmentSourceHost>()
                        }
                        Then("it identifies the host that needs authorization") {
                            (authorization.error() as LoadoutError.UntrustedFragmentSourceHost).host shouldBe RAW_HOST
                        }
                    }
                }
            }
        }
    }

    Given("a malformed HTTPS source URL") {
        val sourceUrl = "https://"

        When("the fragment source is resolved") {
            val resolution = resolveFragmentSource(sourceUrl)

            thenSourceIsRejected(resolution, sourceUrl, "URL is malformed")
        }
    }

    Given("a GitHub blob page URL") {
        val sourceUrl = GITHUB_BLOB_PAGE_URL

        When("the fragment source is resolved") {
            val resolution = resolveFragmentSource(sourceUrl)

            thenSourceIsRejected(resolution, sourceUrl, "GitHub page URLs return HTML")

            Then("it suggests the corresponding raw document URL") {
                (resolution.error() as LoadoutError.InvalidFragmentSource).reason.shouldContain(RAW_DOCUMENT_URL)
            }
        }
    }

    Given("the maximum fragment document size is 10 bytes") {
        val maximumBytes = 10

        mapOf(
            "the document is empty" to byteArrayOf(),
            "the document is below the limit" to byteArrayOf(1, 2, 3),
            "the document is exactly at the limit" to ByteArray(maximumBytes) { it.toByte() },
        ).forEach { (description, document) ->
            Given(description) {
                When("the document size is validated") {
                    val validation = validateFragmentDocumentSize(document, maximumBytes)

                    Then("it preserves all document bytes") {
                        validation.accepted().toList().shouldContainExactly(document.toList())
                    }
                }
            }
        }

        Given("the document is one byte above the limit") {
            val document = ByteArray(maximumBytes + 1)

            When("the document size is validated") {
                val validation = validateFragmentDocumentSize(document, maximumBytes)

                Then("it rejects an oversized document") {
                    validation.error().shouldBeInstanceOf<LoadoutError.FragmentCopyTooLarge>()
                }
                Then("it reports the configured byte limit") {
                    (validation.error() as LoadoutError.FragmentCopyTooLarge).maximumBytes shouldBe maximumBytes
                }
            }
        }
    }
})
