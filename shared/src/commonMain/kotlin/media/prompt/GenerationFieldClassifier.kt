package com.valoser.futacha.shared.media.prompt

import kotlinx.serialization.json.*

internal data class GenerationTextField(val key: String, val value: String, val source: String)

/** Recognize schemas, not a generic comment or the mere presence of a 'prompt' key. */
internal fun classifyGenerationFields(fields: List<GenerationTextField>): List<GenerationCandidate> = buildList {
    val novelAi = fields.any { it.key == "Software" && it.value.equals("NovelAI", ignoreCase = true) }
    fields.forEachIndexed { index, (key, value, source) ->
        if (size >= 16) return@buildList
        when {
            key == "parameters" -> add(parseA1111(value, "$source / parameters #${index + 1}"))
            novelAi && key == "Comment" -> {
                val json = boundedJsonObject(value) ?: return@forEachIndexed
                val prompt = json.text("prompt") ?: return@forEachIndexed
                if (json["steps"] !is JsonPrimitive || json.text("sampler") == null) return@forEachIndexed
                add(GenerationCandidate("$source / NovelAI #${index + 1}", prompt, json.text("uc"),
                    JsonObject(json.filterKeys { it != "prompt" && it != "uc" }).toString(), value, true))
            }
            key == "prompt" -> {
                val graph = boundedJsonObject(value) ?: return@forEachIndexed
                if (graph.size > 512) return@forEachIndexed
                graph.forEach { (id, nodeValue) ->
                    if (size >= 16) return@forEach
                    val node = nodeValue as? JsonObject ?: return@forEach
                    if (node.text("class_type") !in setOf("KSampler", "KSamplerAdvanced")) return@forEach
                    val inputs = node["inputs"] as? JsonObject ?: return@forEach
                    if (inputs["positive"] !is JsonArray || inputs["negative"] !is JsonArray) return@forEach
                    fun textAt(link: JsonElement?): String? {
                        val reference = link as? JsonArray ?: return null
                        val textNode = graph[(reference.firstOrNull() as? JsonPrimitive)?.content] as? JsonObject ?: return null
                        if (textNode.text("class_type") != "CLIPTextEncode") return null
                        return (textNode["inputs"] as? JsonObject)?.text("text")
                    }
                    add(GenerationCandidate("$source / ComfyUI / $id", textAt(inputs["positive"]), textAt(inputs["negative"]),
                        JsonObject(inputs.filterValues { it is JsonPrimitive }).toString(), value, true))
                }
            }
            key == "Comment" || key == "Description" -> {
                val candidate = parseA1111(value, source)
                if (candidate.isAi) add(candidate)
            }
        }
    }
}

internal fun parseA1111(value: String, source: String): GenerationCandidate {
    val settingsMatch = Regex("(?m)^Steps: [0-9]+, [^\\r\\n]*").findAll(value).lastOrNull()
    if (settingsMatch == null || !settingsMatch.value.contains("Sampler:") || !settingsMatch.value.contains("Seed:") ||
        value.substring(settingsMatch.range.last + 1).isNotBlank()) {
        return GenerationCandidate(source, null, raw = value, isAi = false)
    }
    fun withoutSeparator(end: Int): Int = when {
        end >= 2 && value.substring(end - 2, end) == "\r\n" -> end - 2
        end >= 1 && value[end - 1] == '\n' -> end - 1
        else -> end
    }
    val bodyEnd = withoutSeparator(settingsMatch.range.first)
    val negatives = Regex("(?m)^Negative prompt: ?").findAll(value.substring(0, bodyEnd)).toList()
    if (negatives.size > 1) {
        // A quoted heading or duplicated generation block is ambiguous. Never let
        // an earlier negative section silently become the ordinary positive copy.
        return GenerationCandidate(source, null, settings = value.substring(settingsMatch.range.first), raw = value, isAi = true)
    }
    val negative = negatives.singleOrNull()
    return GenerationCandidate(
        source,
        value.substring(0, negative?.range?.first?.let(::withoutSeparator) ?: bodyEnd),
        negative?.let { value.substring(it.range.last + 1, bodyEnd) },
        value.substring(settingsMatch.range.first), value, true
    )
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Bound recursion before handing untrusted JSON to the platform-independent parser. */
private fun boundedJsonObject(text: String): JsonObject? {
    var depth = 0
    var quoted = false
    var escaped = false
    text.forEach { c ->
        if (quoted) {
            if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
        } else when (c) {
            '"' -> quoted = true
            '{', '[' -> { depth++; if (depth > 32) return null }
            '}', ']' -> { depth--; if (depth < 0) return null }
        }
    }
    if (depth != 0 || quoted) return null
    return try { Json.parseToJsonElement(text) as? JsonObject } catch (_: IllegalArgumentException) { null }
}
