package domain.policy

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

private const val HTTPS_SCHEME = "https"
private const val HTTPS_PORT = 443
private const val GITHUB_HOST = "github.com"
private const val URI_SCHEME_DELIMITER = "://"

private val githubBlobPath =
    Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+\\.md)(?:[?#].*)?$", RegexOption.IGNORE_CASE)

sealed interface FragmentSource {
    val location: String

    @ConsistentCopyVisibility
    data class LocalMarkdownFile internal constructor(override val location: String) : FragmentSource

    @ConsistentCopyVisibility
    data class RemoteDocument internal constructor(
        override val location: String,
        val host: String,
    ) : FragmentSource
}

sealed interface AuthorizedFragmentSource {
    val location: String

    @ConsistentCopyVisibility
    data class LocalMarkdownFile internal constructor(override val location: String) : AuthorizedFragmentSource

    @ConsistentCopyVisibility
    data class RemoteDocument internal constructor(val url: TrustedDocumentUrl) : AuthorizedFragmentSource {
        override val location: String = url.value
    }
}

@ConsistentCopyVisibility
data class TrustedDocumentUrl internal constructor(
    val value: String,
    val host: String,
)

fun resolveFragmentSource(source: String): Result<FragmentSource, LoadoutError> =
    when {
        source.contains(URI_SCHEME_DELIMITER) -> resolveRemoteDocument(source)
        !source.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) ->
            Result.Error(LoadoutError.InvalidFragmentSource(source, "Local source must be a Markdown file"))
        else -> Result.Success(FragmentSource.LocalMarkdownFile(source))
    }

fun authorizeFragmentSource(
    source: FragmentSource,
    trustedHosts: List<String>,
): Result<AuthorizedFragmentSource, LoadoutError> =
    when (source) {
        is FragmentSource.LocalMarkdownFile ->
            Result.Success(AuthorizedFragmentSource.LocalMarkdownFile(source.location))
        is FragmentSource.RemoteDocument ->
            if (trustedHosts.any { it.equals(source.host, ignoreCase = true) }) {
                Result.Success(
                    AuthorizedFragmentSource.RemoteDocument(
                        TrustedDocumentUrl(value = source.location, host = source.host)
                    )
                )
            } else {
                Result.Error(LoadoutError.UntrustedFragmentSourceHost(source.host))
            }
    }

fun validateFragmentDocumentSize(
    bytes: ByteArray,
    maximumBytes: Int,
): Result<ByteArray, LoadoutError> =
    if (bytes.size <= maximumBytes) {
        Result.Success(bytes)
    } else {
        Result.Error(LoadoutError.FragmentCopyTooLarge(maximumBytes))
    }

private fun resolveRemoteDocument(source: String): Result<FragmentSource, LoadoutError> {
    val url = parseAbsoluteUrl(source) ?: return invalid(source, "URL is malformed")

    return when {
        url.scheme != HTTPS_SCHEME -> invalid(source, "Only local paths and HTTPS URLs are supported")
        url.port != HTTPS_PORT -> invalid(source, "Only HTTPS port 443 is supported")
        url.hasCredentials -> invalid(source, "URL credentials are not supported")
        url.host.equals(GITHUB_HOST, ignoreCase = true) -> Result.Error(githubPageError(source))
        !url.path.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) ->
            invalid(source, "URL path must identify a Markdown file")
        else -> Result.Success(FragmentSource.RemoteDocument(location = source, host = url.host.lowercase()))
    }
}

private fun invalid(
    source: String,
    reason: String,
): Result<FragmentSource, LoadoutError> = Result.Error(LoadoutError.InvalidFragmentSource(source, reason))

private fun githubPageError(source: String): LoadoutError.InvalidFragmentSource {
    val suggestion =
        githubBlobPath.matchEntire(source)?.destructured?.let { (owner, repository, ref, path) ->
            " Try the raw URL: https://raw.githubusercontent.com/$owner/$repository/refs/heads/$ref/$path"
        }.orEmpty()

    return LoadoutError.InvalidFragmentSource(
        source,
        "GitHub page URLs return HTML; provide the raw file URL instead.$suggestion",
    )
}

private data class AbsoluteUrl(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String,
    val hasCredentials: Boolean,
)

private fun parseAbsoluteUrl(source: String): AbsoluteUrl? {
    val schemeEnd = source.indexOf(URI_SCHEME_DELIMITER)
    if (schemeEnd <= 0) return null

    val scheme = source.substring(0, schemeEnd).lowercase()
    val remainder = source.substring(schemeEnd + URI_SCHEME_DELIMITER.length)
    val authorityEnd = remainder.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (authorityEnd < 0) remainder else remainder.substring(0, authorityEnd)
    val path =
        if (authorityEnd < 0) "" else remainder.substring(authorityEnd).substringBefore('?').substringBefore('#')

    val credentialsEnd = authority.lastIndexOf('@')
    val hostAndPort = if (credentialsEnd >= 0) authority.substring(credentialsEnd + 1) else authority
    if (hostAndPort.isBlank() || hostAndPort.startsWith('[')) return null

    val portSeparator = hostAndPort.lastIndexOf(':')
    val host = if (portSeparator >= 0) hostAndPort.substring(0, portSeparator) else hostAndPort
    val port =
        if (portSeparator >= 0) {
            hostAndPort.substring(portSeparator + 1).toIntOrNull() ?: return null
        } else {
            defaultPortFor(scheme) ?: return null
        }
    if (host.isBlank()) return null

    return AbsoluteUrl(
        scheme = scheme,
        host = host,
        port = port,
        path = path,
        hasCredentials = credentialsEnd >= 0,
    )
}

private fun defaultPortFor(scheme: String): Int? =
    when (scheme) {
        HTTPS_SCHEME -> HTTPS_PORT
        "http" -> 80
        else -> null
    }
