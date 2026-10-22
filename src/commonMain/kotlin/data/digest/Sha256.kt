package data.digest

import org.kotlincrypto.hash.sha2.SHA256

fun sha256Hex(bytes: ByteArray): String = SHA256().digest(bytes).toHexString()
