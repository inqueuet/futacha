package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FtutterAesTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun aes256MatchesFips197() {
        // FIPS-197 付録 C.3
        val aes = FtutterAes(hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"))
        assertContentEquals(hex("8ea2b7ca516745bfeafc49904b496089"), aes.encryptBlock(hex("00112233445566778899aabbccddeeff")))
    }

    @Test
    fun aes128MatchesFips197() {
        // FIPS-197 付録 C.1（鍵の展開を別の長さでも確かめる）
        val aes = FtutterAes(hex("000102030405060708090a0b0c0d0e0f"))
        assertContentEquals(hex("69c4e0d86a7b0430d8cdb78070b4c55a"), aes.encryptBlock(hex("00112233445566778899aabbccddeeff")))
    }

    @Test
    fun ctrMatchesNistSp80038a() {
        // NIST SP 800-38A F.5.5（CTR-AES256.Encrypt）。カウンタの繰り上がりも含めて確かめる。
        val aes = FtutterAes(hex("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4"))
        val iv = hex("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff")
        val plain = hex(
            "6bc1bee22e409f96e93d7e117393172a" + "ae2d8a571e03ac9c9eb76fac45af8e51" +
                "30c81c46a35ce411e5fbc1191a0a52ef" + "f69f2445df4f9b17ad2b417be66c3710"
        )
        val cipher = hex(
            "601ec313775789a5b7a7f504bbf3d228" + "f443e3ca4d62b59aca84e990cacaf5c5" +
                "2b0930daa23de94ce87017ba2d84988d" + "dfc9c58db67aada613c2dd08457941a6"
        )
        assertContentEquals(cipher, aes.ctr(plain, iv))
        assertContentEquals(plain, aes.ctr(cipher, iv))
        // 端数のあるデータ（ブロックの途中で終わる）も同じ流れで扱う。
        assertContentEquals(cipher.copyOf(20), aes.ctr(plain.copyOf(20), iv))
    }

    @Test
    fun pkcs7IsStrippedOnlyWhenValid() {
        val padded = "abc".encodeToByteArray() + ByteArray(13) { 13 }
        assertEquals("abc", ftutterStripPkcs7(padded)?.decodeToString())
        // 最後の値は13なのに、詰め物の途中に違う値がある。
        assertNull(ftutterStripPkcs7("abc".encodeToByteArray() + ByteArray(13) { if (it == 0) 7 else 13 }))
        assertNull(ftutterStripPkcs7(ByteArray(16) { 0 }))
        assertNull(ftutterStripPkcs7(ByteArray(15)))
    }
}
