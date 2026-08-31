package com.valoser.futacha.shared.compat

private const val HALF_WIDTH_KANA =
    "｡｢｣､･ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝﾞﾟ"
private const val FULL_WIDTH_KANA =
    "。「」、・ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン゛゜"

private val COMPAT_DAKUTEN_COMBINATIONS = mapOf(
    'ウ' to 'ヴ', 'カ' to 'ガ', 'キ' to 'ギ', 'ク' to 'グ', 'ケ' to 'ゲ', 'コ' to 'ゴ',
    'サ' to 'ザ', 'シ' to 'ジ', 'ス' to 'ズ', 'セ' to 'ゼ', 'ソ' to 'ゾ',
    'タ' to 'ダ', 'チ' to 'ヂ', 'ツ' to 'ヅ', 'テ' to 'デ', 'ト' to 'ド',
    'ハ' to 'バ', 'ヒ' to 'ビ', 'フ' to 'ブ', 'ヘ' to 'ベ', 'ホ' to 'ボ',
    'ワ' to 'ヷ', 'ヰ' to 'ヸ', 'ヱ' to 'ヹ', 'ヲ' to 'ヺ'
)
private val COMPAT_HANDAKUTEN_COMBINATIONS = mapOf(
    'ハ' to 'パ', 'ヒ' to 'ピ', 'フ' to 'プ', 'ヘ' to 'ペ', 'ホ' to 'ポ'
)

/**
 * Reference matching folds case, full-width ASCII and half-width katakana.
 * Keep it in common code so Android and iOS apply identical NG/watch/search
 * behavior without depending on java.text.Normalizer.
 */
fun normalizeCompatSearchText(raw: String): String {
    val normalized = StringBuilder(raw.length)
    var index = 0
    while (index < raw.length) {
        val source = raw[index]
        val mapped = when {
            source == '\u3000' -> ' '
            source.code in 0xFF01..0xFF5E -> (source.code - 0xFEE0).toChar()
            else -> HALF_WIDTH_KANA.indexOf(source)
                .takeIf { it >= 0 }
                ?.let(FULL_WIDTH_KANA::get)
                ?: source
        }
        val next = raw.getOrNull(index + 1)
        val combined = when (next) {
            'ﾞ' -> COMPAT_DAKUTEN_COMBINATIONS[mapped]
            'ﾟ' -> COMPAT_HANDAKUTEN_COMBINATIONS[mapped]
            else -> null
        }
        normalized.append(combined ?: mapped)
        index += if (combined != null) 2 else 1
    }
    return normalized.toString().trim().lowercase()
}
