package com.example.redpaternal.utilidades

import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

class EncriptacionTplink {

    private val _iv: ByteArray
    private val _llave: ByteArray

    init {
        val random = SecureRandom()
        val ivBytes = ByteArray(8)
        val llaveBytes = ByteArray(8)
        random.nextBytes(ivBytes)
        random.nextBytes(llaveBytes)

        this._iv = bytesAHex(ivBytes).toByteArray(Charsets.UTF_8)
        this._llave = bytesAHex(llaveBytes).toByteArray(Charsets.UTF_8)
    }

    fun obtenerLlaveStr(): String = String(_llave, Charsets.UTF_8)
    fun obtenerIvStr(): String = String(_iv, Charsets.UTF_8)

    fun aesEncriptar(raw: String): String {
        try {
            val rawPad = _rellenar(raw)
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            val keySpec = SecretKeySpec(_llave, "AES")
            val ivSpec = IvParameterSpec(_iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encriptado = cipher.doFinal(rawPad.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encriptado, Base64.NO_WRAP)
        } catch (e: Exception) { return "" }
    }

    fun aesDesencriptar(enc: String): String {
        try {
            val encBytes = Base64.decode(enc, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            val keySpec = SecretKeySpec(_llave, "AES")
            val ivSpec = IvParameterSpec(_iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val desencriptado = cipher.doFinal(encBytes)
            val resultado = String(desencriptado, Charsets.UTF_8)
            return _quitarRelleno(resultado)
        } catch (e: Exception) { return "" }
    }

    fun obtenerFirma(seq: Long, dataLen: Int, nn: String, ee: String): String {
        val aesStr = _obtenerCadenaAes()

        val valSeq = seq + dataLen

        val s = "$aesStr&s=$valSeq"

        var firma = ""
        var pos = 0

        while (pos < s.length) {
            val fin = min(pos + 53, s.length)
            val fragmento = s.substring(pos, fin)
            firma += rsaEncriptar(fragmento, nn, ee)
            pos += 53
        }
        return firma
    }

    private fun _rellenar(s: String): String {
        val tamBloque = 16
        val relleno = tamBloque - (s.length % tamBloque)
        val charRelleno = relleno.toChar()
        return s + charRelleno.toString().repeat(relleno)
    }

    private fun _quitarRelleno(s: String): String {
        if (s.isEmpty()) return ""
        val ultimo = s.last().code
        return if (ultimo in 1..16) s.substring(0, s.length - ultimo) else s
    }

    private fun _obtenerCadenaAes(): String {
        return "k=${String(_llave)}&i=${String(_iv)}"
    }

    companion object {
        fun rsaEncriptar(data: String, nn: String, ee: String): String {
            try {
                val mod = BigInteger(nn, 16)
                val exp = BigInteger(ee, 16)
                val spec = RSAPublicKeySpec(mod, exp)
                val factory = KeyFactory.getInstance("RSA")
                val pubKey = factory.generatePublic(spec)

                val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(Cipher.ENCRYPT_MODE, pubKey)
                val encriptado = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
                return bytesAHex(encriptado)
            } catch (e: Exception) { return "" }
        }

        private fun bytesAHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}