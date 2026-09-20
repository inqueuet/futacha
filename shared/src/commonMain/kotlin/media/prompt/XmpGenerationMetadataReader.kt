@file:OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.media.prompt

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class XmpName(val namespace: String, val local: String)
internal data class XmpAttribute(val name: XmpName, val value: String)

/** Platform XML parsers only tokenize. Namespaces, limits and generation semantics are shared. */
internal expect fun parseXmpDocument(bytes: ByteArray, sink: XmpEventSink)

internal class XmpNode(val name: XmpName, val attributes: List<XmpAttribute>) {
    val children = mutableListOf<XmpNode>()
    val text = StringBuilder()
}

internal class XmpEventSink(private val budget: MetadataReadBudget, private val checkActive: () -> Unit) {
    private val stack = mutableListOf<XmpNode>()
    private var nodes = 0
    var root: XmpNode? = null
        private set

    fun start(name: XmpName, attributes: List<XmpAttribute>) {
        checkActive()
        if (++nodes > 1024 || stack.size >= 32 || attributes.size > 128) throw MetadataBudgetExceeded()
        budget.consume(64L + (name.namespace.length + name.local.length) * 2L)
        attributes.forEach { budget.consume(32L + (it.name.namespace.length + it.name.local.length + it.value.length) * 2L) }
        val node = XmpNode(name, attributes)
        if (stack.isEmpty()) {
            if (root != null) throw InvalidMetadata()
            root = node
        } else stack.last().children += node
        stack += node
    }

    fun text(value: String) {
        checkActive()
        if (stack.isEmpty()) {
            if (value.isNotBlank()) throw InvalidMetadata()
        } else {
            budget.consume(value.length * 2L)
            stack.last().text.append(value)
        }
    }

    fun end() {
        checkActive()
        if (stack.isEmpty()) throw InvalidMetadata()
        stack.removeAt(stack.lastIndex)
    }

    fun finish(): XmpNode {
        checkActive()
        if (stack.isNotEmpty()) throw InvalidMetadata()
        return root ?: throw InvalidMetadata()
    }
}

internal suspend fun readXmpGenerationMetadata(
    bytes: ByteArray, source: String, collector: GenerationFieldCollector, budget: MetadataReadBudget
) {
    // Preflight before invoking any parser. Covers UTF-8 and UTF-16 packets; no
    // external entities, DTDs, network resources or embedded schemas are loaded.
    val safetyText = when {
        bytes.size >= 2 && metadataUint(bytes, 0, 2) in setOf(0xfffeL, 0xfeffL, 0x003cL, 0x3c00L) ->
            metadataUtf16(bytes, little = bytes[0] != 0.toByte())
        else -> bytes.decodeToString(throwOnInvalidSequence = true)
    }
    if ('\u0000' in safetyText || Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(safetyText)) throw InvalidMetadata()
    val context = currentCoroutineContext()
    val sink = XmpEventSink(budget) { context.ensureActive() }
    parseXmpDocument(bytes, sink)
    val root = sink.finish()

    fun property(name: XmpName, value: String) {
        if (name.namespace == RDF_NS || name.namespace == XML_NS || name.namespace.isEmpty()) return
        if (name.namespace == XMP_NOTE_NS && name.local == "HasExtendedXMP") {
            collector.limitations += MetadataLimitation.EXTENDED_XMP
            return
        }
        if (name.namespace in IPTC_NAMESPACES && name.local == "DigitalSourceType") {
            collector.declaration(value, source)
        }
        when (name.local.lowercase()) {
            "parameters", "prompt" -> collector.text(name.local.lowercase(), value, "$source ${name.local}")
            "description", "usercomment" -> collector.text("XMP ${name.local}", value, "$source ${name.local}")
        }
    }

    fun visit(node: XmpNode) {
        context.ensureActive()
        node.attributes.forEach { property(it.name, it.value) }
        // rdf:Description is a structural node, not a photo's description.
        if (node.name.namespace != RDF_NS && node.name.local.lowercase() in setOf(
                "parameters", "prompt", "description", "usercomment", "digitalsourcetype", "hasextendedxmp")) {
            val resource = node.attributes.singleOrNull { it.name == XmpName(RDF_NS, "resource") }?.value
            when {
                resource != null && node.children.isEmpty() && node.text.isBlank() -> property(node.name, resource)
                node.children.isEmpty() -> property(node.name, node.text.toString())
                node.children.size == 1 && node.text.isBlank() && node.children.single().name == XmpName(RDF_NS, "Alt") -> {
                    val alt = node.children.single()
                    if (alt.text.isBlank() && alt.children.all { it.name == XmpName(RDF_NS, "li") && it.children.isEmpty() }) {
                        // Keep language alternatives separate; concatenation fabricates a prompt.
                        alt.children.forEach { property(node.name, it.text.toString()) }
                    } else collector.limitations += MetadataLimitation.COMPLEX_XMP
                }
                else -> collector.limitations += MetadataLimitation.COMPLEX_XMP
            }
        }
        node.children.forEach(::visit)
    }
    visit(root)
}

internal const val RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
internal const val XML_NS = "http://www.w3.org/XML/1998/namespace"
private const val XMP_NOTE_NS = "http://ns.adobe.com/xmp/note/"
private val IPTC_NAMESPACES = setOf("http://iptc.org/std/Iptc4xmpExt/2008-02-29/", "http://iptc.org/std/Iptc4xmpExt/2024-06-19/")
