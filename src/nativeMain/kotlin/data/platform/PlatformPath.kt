package data.platform

internal expect fun platformIsSymbolicLink(path: String): Boolean

internal expect fun platformCanonicalPath(path: String): String?
