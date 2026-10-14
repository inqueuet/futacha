package com.valoser.futacha.shared.ui.ftutter

/**
 * 原作（ふたったー 3.1.1）がバックアップ（`.ftt`）の暗号化に使う鍵と IV。原作のアプリに埋め込まれている固定の値で、利用者ごとの
 * 秘密ではない（同じ値で誰のバックアップも作られる）。取り込みのためだけに使う。値は調査の文書には書かない。
 */
internal object FtutterBackupCipher {
    private const val KEY_HEX = "696e206d79206f70206564676520697320746865206265737420616e6f6e2e2e"
    private const val IV_HEX = "73686f756c64206265203136206c656e"

    val key: ByteArray get() = KEY_HEX.hexToBytes()
    val iv: ByteArray get() = IV_HEX.hexToBytes()

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
