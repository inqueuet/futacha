package com.valoser.futacha.shared.ui.ftutter

/**
 * 原作のバックアップ（`.ftt`）を読むための最小限の AES（FIPS-197）。CTR は暗号化の関数だけで復号できるので、ブロックの暗号化と
 * CTR・PKCS#7 だけを持つ。原作は Dart の encrypt パッケージの既定（AESMode.sic＝CTR、128ビットのカウンタをビッグエンディアンで
 * 増やす）と PKCS#7 を使う。
 */
internal class FtutterAes(key: ByteArray) {
    private val rounds: Int
    private val roundKeys: IntArray

    init {
        require(key.size == 16 || key.size == 24 || key.size == 32) { "AES の鍵の長さが正しくありません" }
        val nk = key.size / 4
        rounds = nk + 6
        val total = 4 * (rounds + 1)
        roundKeys = IntArray(total)
        for (i in 0 until nk) {
            roundKeys[i] = ((key[4 * i].toInt() and 0xFF) shl 24) or ((key[4 * i + 1].toInt() and 0xFF) shl 16) or
                ((key[4 * i + 2].toInt() and 0xFF) shl 8) or (key[4 * i + 3].toInt() and 0xFF)
        }
        var rcon = 1
        for (i in nk until total) {
            var temp = roundKeys[i - 1]
            if (i % nk == 0) {
                temp = subWord(rotWord(temp)) xor (rcon shl 24)
                rcon = xtime(rcon)
            } else if (nk > 6 && i % nk == 4) {
                temp = subWord(temp)
            }
            roundKeys[i] = roundKeys[i - nk] xor temp
        }
    }

    /** 16バイトを1ブロック暗号化する。 */
    fun encryptBlock(input: ByteArray): ByteArray {
        require(input.size == 16)
        val s = IntArray(16) { input[it].toInt() and 0xFF }
        addRoundKey(s, 0)
        for (round in 1 until rounds) {
            subBytes(s); shiftRows(s); mixColumns(s); addRoundKey(s, round)
        }
        subBytes(s); shiftRows(s); addRoundKey(s, rounds)
        return ByteArray(16) { s[it].toByte() }
    }

    /** CTR：[iv] を128ビットの数としてブロックごとに1ずつ増やし、その暗号化と排他的論理和をとる（暗号化も復号も同じ）。 */
    fun ctr(data: ByteArray, iv: ByteArray): ByteArray {
        require(iv.size == 16)
        val counter = iv.copyOf()
        val out = ByteArray(data.size)
        var offset = 0
        while (offset < data.size) {
            val stream = encryptBlock(counter)
            val n = minOf(16, data.size - offset)
            for (i in 0 until n) out[offset + i] = (data[offset + i].toInt() xor stream[i].toInt()).toByte()
            offset += n
            for (i in 15 downTo 0) {
                val v = (counter[i].toInt() and 0xFF) + 1
                counter[i] = v.toByte()
                if (v <= 0xFF) break
            }
        }
        return out
    }

    private fun addRoundKey(s: IntArray, round: Int) {
        for (c in 0 until 4) {
            val k = roundKeys[round * 4 + c]
            s[c * 4] = s[c * 4] xor ((k ushr 24) and 0xFF)
            s[c * 4 + 1] = s[c * 4 + 1] xor ((k ushr 16) and 0xFF)
            s[c * 4 + 2] = s[c * 4 + 2] xor ((k ushr 8) and 0xFF)
            s[c * 4 + 3] = s[c * 4 + 3] xor (k and 0xFF)
        }
    }

    private fun subBytes(s: IntArray) { for (i in 0 until 16) s[i] = SBOX[s[i]] }

    private fun shiftRows(s: IntArray) {
        // 状態は列ごと（s[c*4 + r]）。r 行目を左へ r つずらす。
        for (r in 1 until 4) {
            val row = IntArray(4) { c -> s[((c + r) % 4) * 4 + r] }
            for (c in 0 until 4) s[c * 4 + r] = row[c]
        }
    }

    private fun mixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = s[c * 4]; val a1 = s[c * 4 + 1]; val a2 = s[c * 4 + 2]; val a3 = s[c * 4 + 3]
            s[c * 4] = xtime(a0) xor (xtime(a1) xor a1) xor a2 xor a3
            s[c * 4 + 1] = a0 xor xtime(a1) xor (xtime(a2) xor a2) xor a3
            s[c * 4 + 2] = a0 xor a1 xor xtime(a2) xor (xtime(a3) xor a3)
            s[c * 4 + 3] = (xtime(a0) xor a0) xor a1 xor a2 xor xtime(a3)
        }
    }

    private companion object {
        fun xtime(x: Int): Int = ((x shl 1) xor (if (x and 0x80 != 0) 0x1B else 0)) and 0xFF
        fun rotWord(w: Int): Int = (w shl 8) or (w ushr 24)
        fun subWord(w: Int): Int =
            (SBOX[(w ushr 24) and 0xFF] shl 24) or (SBOX[(w ushr 16) and 0xFF] shl 16) or
                (SBOX[(w ushr 8) and 0xFF] shl 8) or SBOX[w and 0xFF]

        val SBOX: IntArray = run {
            // 乗法逆元とアフィン変換から作る（表を手で書き写さない）。
            val box = IntArray(256)
            var p = 1
            var q = 1
            do {
                p = p xor ((p shl 1) and 0xFF) xor (if (p and 0x80 != 0) 0x1B else 0)
                q = q xor (q shl 1)
                q = q xor (q shl 2)
                q = q xor (q shl 4)
                q = q and 0xFF
                if (q and 0x80 != 0) q = q xor 0x09
                val x = q xor rotl8(q, 1) xor rotl8(q, 2) xor rotl8(q, 3) xor rotl8(q, 4)
                box[p] = (x xor 0x63) and 0xFF
            } while (p != 1)
            box[0] = 0x63
            box
        }

        fun rotl8(x: Int, shift: Int): Int = ((x shl shift) or (x ushr (8 - shift))) and 0xFF
    }
}

/** PKCS#7 の詰め物を外す。正しくなければ null（鍵が違う・壊れたファイル）。 */
internal fun ftutterStripPkcs7(data: ByteArray): ByteArray? {
    if (data.isEmpty() || data.size % 16 != 0) return null
    val pad = data.last().toInt() and 0xFF
    if (pad !in 1..16) return null
    for (i in data.size - pad until data.size) if ((data[i].toInt() and 0xFF) != pad) return null
    return data.copyOf(data.size - pad)
}
