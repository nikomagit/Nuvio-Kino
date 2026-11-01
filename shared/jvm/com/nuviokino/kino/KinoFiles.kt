package com.nuviokino.kino

import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun String.unhex(): ByteArray {
    require(length % 2 == 0 && all { it in "0123456789abcdefABCDEF" }) { "Hexadecimal inválido" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
internal fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()

internal fun atomicWrite(file: File, data: ByteArray) {
    file.parentFile.mkdirs()
    val temp = File(file.parentFile, file.name + ".tmp")
    temp.outputStream().use { out -> out.write(data); out.fd.sync() }
    try {
        java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } catch(_: java.nio.file.AtomicMoveNotSupportedException) {
        java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun verifyPluginSignature(plugin: KinoPlugin, source: ByteArray, expectedKey: String? = null) {
    val signature = plugin.manifest.obj("signature")
    val key = signature.text("authorKey")
    if (expectedKey != null) require(key == expectedKey) { "La clave del autor cambió o falta la firma" }
    if (signature.isEmpty()) return
    require(key.matches(Regex("[0-9a-fA-F]{64}"))) { "Clave Ed25519 inválida" }
    val message = "kino-signed-entry:v1\n${plugin.address.lowercase()}\n${plugin.id}\n${plugin.version}\n${sha256(source)}"
    val verifier = org.bouncycastle.crypto.signers.Ed25519Signer()
    verifier.init(false, org.bouncycastle.crypto.params.Ed25519PublicKeyParameters(key.unhex(), 0))
    val bytes = message.toByteArray(Charsets.UTF_8)
    verifier.update(bytes, 0, bytes.size)
    require(verifier.verifySignature(signature.text("value").unhex())) { "La firma del plugin no es válida" }
}
