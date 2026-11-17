package com.valoser.futacha.shared.ai

val OPENAI_MODERATION_CATEGORIES: Map<String, String> = linkedMapOf(
    "harassment" to "嫌がらせ・攻撃的表現",
    "harassment/threatening" to "脅迫的な嫌がらせ",
    "hate" to "差別・憎悪表現",
    "hate/threatening" to "脅迫を伴う差別・憎悪表現",
    "sexual" to "性的表現",
    "sexual/minors" to "未成年に関する性的表現",
    "violence" to "暴力",
    "violence/graphic" to "生々しい暴力描写",
    "self-harm" to "自傷",
    "self-harm/intent" to "自傷の意図",
    "self-harm/instructions" to "自傷の方法",
    "illicit" to "違法行為の助言・手順",
    "illicit/violent" to "暴力を伴う違法行為の助言・手順"
)
val DEFAULT_OPENAI_MODERATION_CATEGORIES: Set<String> = setOf("harassment", "harassment/threatening", "hate", "hate/threatening")
