package com.valoser.futacha.shared.ui.futaber

private const val FUTABER_FULL_KANA =
    "。「」、・ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン゛゜"
private const val FUTABER_HALF_KANA =
    "｡｢｣､･ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝﾞﾟ"

/** The voiced katakana, written in half-width as their plain letter and a separate ﾞ / ﾟ. */
private val FUTABER_VOICED_BASE = mapOf(
    'ヴ' to 'ウ', 'ガ' to 'カ', 'ギ' to 'キ', 'グ' to 'ク', 'ゲ' to 'ケ', 'ゴ' to 'コ',
    'ザ' to 'サ', 'ジ' to 'シ', 'ズ' to 'ス', 'ゼ' to 'セ', 'ゾ' to 'ソ',
    'ダ' to 'タ', 'ヂ' to 'チ', 'ヅ' to 'ツ', 'デ' to 'テ', 'ド' to 'ト',
    'バ' to 'ハ', 'ビ' to 'ヒ', 'ブ' to 'フ', 'ベ' to 'ヘ', 'ボ' to 'ホ',
    'ヷ' to 'ワ', 'ヺ' to 'ヲ'
)
private val FUTABER_SEMI_VOICED_BASE = mapOf(
    'パ' to 'ハ', 'ピ' to 'ヒ', 'プ' to 'フ', 'ペ' to 'ヘ', 'ポ' to 'ホ'
)

/**
 * The writing screen's "カナ" (as in the original): hiragana and katakana become half-width katakana, a voiced letter
 * becoming its letter and ﾞ / ﾟ ("すけべしまる" → "ｽｹﾍﾞｼﾏﾙ"), with the Japanese punctuation that has a half-width form.
 * Everything else (kanji, ASCII, a kana with no half-width form such as ヶ) stays as it is.
 */
internal fun futaberToHalfWidthKana(text: String): String {
    val out = StringBuilder(text.length + 8)
    for (raw in text) {
        // Hiragana first become their katakana (ぁ..ゖ, and ゝゞ are left alone).
        val c = if (raw in 'ぁ'..'ゖ') raw + 0x60 else raw
        val voiced = FUTABER_VOICED_BASE[c]
        val semiVoiced = FUTABER_SEMI_VOICED_BASE[c]
        when {
            voiced != null -> out.append(FUTABER_HALF_KANA[FUTABER_FULL_KANA.indexOf(voiced)]).append('ﾞ')
            semiVoiced != null -> out.append(FUTABER_HALF_KANA[FUTABER_FULL_KANA.indexOf(semiVoiced)]).append('ﾟ')
            // The combining marks are written as the spacing ones.
            c == '゙' -> out.append('ﾞ')
            c == '゚' -> out.append('ﾟ')
            else -> {
                val index = FUTABER_FULL_KANA.indexOf(c)
                out.append(if (index >= 0) FUTABER_HALF_KANA[index] else raw)
            }
        }
    }
    return out.toString()
}
