package data.digest

import org.kotlincrypto.hash.sha2.SHA256

/** The SHA-256 digest of [bytes], as lowercase hexadecimal. */
fun sha256Hex(bytes: ByteArray): String = SHA256().digest(bytes).toHexString()
