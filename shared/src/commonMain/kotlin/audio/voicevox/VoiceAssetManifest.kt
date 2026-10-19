package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.download.AssetDistribution

internal data class VoiceDictionaryFile(val name: String, val bytes: Long, val sha256: String)

internal object VoiceAssetManifest {
    const val PACK = "zundamon-0.16.4-dictionary-1.11"
    const val DOWNLOAD_BYTES = 81_861_222L
    const val INSTALLED_BYTES = 165_519_192L
    const val DICTIONARY_BYTES = 107_304_813L
    const val DICTIONARY_ROOT = "open_jtalk_dic_utf_8-1.11"
    const val STAGING_SPACE_BYTES = 256L * 1024 * 1024
    val model = AssetDistribution("https://github.com/VOICEVOX/voicevox_vvm/releases/download/0.16.4/0.vvm",
        58_214_379, "917e592cae92a02fb7668bd9e168df4e39c8ab2f5302c2be71906c70ec4e2df2")
    val dictionary = AssetDistribution("https://github.com/r9y9/open_jtalk/releases/download/v1.11.1/open_jtalk_dic_utf_8-1.11.tar.gz",
        23_646_843, "fe6ba0e43542cef98339abdffd903e062008ea170b04e7e2a35da805902f382a")
    val files = listOf(
        VoiceDictionaryFile("matrix.bin", 3_792_262, "62fd16b4f64c851d5dc352ef0d5740c5fc83ddc7c203b2b0b1fc5271969a14ce"),
        VoiceDictionaryFile("right-id.def", 77_672, "db1adac8a7f9e5854cd82ea044c85115249206c8181b9d88cf92ae2ee5e87b84"),
        VoiceDictionaryFile("pos-id.def", 1_923, "3460aa742053085af47cdfc889a1e0e6f557e89b406e501ba81c9ccc286de0c7"),
        VoiceDictionaryFile("COPYING", 5_865, "f4eca42ebd930e2c6e57fca58319d989bebcd1510cb7714b149c50f5425135ea"),
        VoiceDictionaryFile("rewrite.def", 7_457, "7f7c8dfbfe24092e8a149a9b6e0a3a7f1c2cf37d6c3dc29d1cccc6c004da9c1c"),
        VoiceDictionaryFile("left-id.def", 77_672, "db1adac8a7f9e5854cd82ea044c85115249206c8181b9d88cf92ae2ee5e87b84"),
        VoiceDictionaryFile("sys.dic", 103_073_776, "ca57d9029691a70a5dfb99afc2844180256161d7130da65b1a867510e129b9a6"),
        VoiceDictionaryFile("char.bin", 262_496, "888ee94c5a8a7a26d24ab3f1b7155441351954fd51ea06b4a2f78bd742492b2f"),
        VoiceDictionaryFile("unk.dic", 5_690, "ce97851ecda075914fa3ffe7294a1ab34ee4f6d56ba6bf9197d74143b5dffbfe")
    )
}
