@file:OptIn(kotlin.ExperimentalMultiplatform::class, kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@file:Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")

package com.valoser.futacha.shared.media.prompt

import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.darwin.NSObject
import platform.posix.memcpy

internal actual fun parseXmpDocument(bytes: ByteArray, sink: XmpEventSink) {
    if (bytes.isEmpty()) throw InvalidMetadata()
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    val delegate = XmpXmlDelegate(sink)
    val parser = NSXMLParser(data).apply {
        this.delegate = delegate
        shouldProcessNamespaces = true
        shouldReportNamespacePrefixes = true
        shouldResolveExternalEntities = false
        externalEntityResolvingPolicy = NSXMLParserResolveExternalEntitiesNever
    }
    try {
        val success = parser.parse()
        delegate.failure?.let { throw it }
        if (!success) throw InvalidMetadata()
    } finally { parser.delegate = null }
}

private class XmpXmlDelegate(private val sink: XmpEventSink) : NSObject(), NSXMLParserDelegateProtocol {
    var failure: Throwable? = null
        private set
    private val prefixes = mutableMapOf("xml" to mutableListOf(XML_NS))

    // Kotlin exceptions must not cross an Objective-C callback boundary. Abort
    // and rethrow on the Kotlin caller, including the original cancellation.
    private inline fun event(parser: NSXMLParser, action: () -> Unit) {
        if (failure != null) return
        try { action() } catch (error: Throwable) { failure = error; parser.abortParsing() }
    }

    @ObjCSignatureOverride
    override fun parser(parser: NSXMLParser, didStartMappingPrefix: String, toURI: String) = event(parser) {
        if (prefixes.values.sumOf { it.size } >= 256) throw MetadataBudgetExceeded()
        prefixes.getOrPut(didStartMappingPrefix) { mutableListOf() }.add(toURI)
    }

    @ObjCSignatureOverride
    override fun parser(parser: NSXMLParser, didEndMappingPrefix: String) = event(parser) {
        val values = prefixes[didEndMappingPrefix] ?: throw InvalidMetadata()
        values.removeAt(values.lastIndex)
        if (values.isEmpty()) prefixes.remove(didEndMappingPrefix)
    }

    @ObjCSignatureOverride
    override fun parser(parser: NSXMLParser, didStartElement: String, namespaceURI: String?, qualifiedName: String?, attributes: Map<Any?, *>) = event(parser) {
        val resolved = attributes.map { (rawName, rawValue) ->
            val name = rawName as? String ?: throw InvalidMetadata()
            val value = rawValue as? String ?: throw InvalidMetadata()
            val namespace = if (':' in name) prefixes[name.substringBefore(':')]?.lastOrNull() ?: throw InvalidMetadata() else ""
            XmpAttribute(XmpName(namespace, name.substringAfter(':')), value)
        }
        sink.start(XmpName(namespaceURI.orEmpty(), didStartElement), resolved)
    }

    @ObjCSignatureOverride
    override fun parser(parser: NSXMLParser, didEndElement: String, namespaceURI: String?, qualifiedName: String?) = event(parser) { sink.end() }
    @ObjCSignatureOverride
    override fun parser(parser: NSXMLParser, foundCharacters: String) = event(parser) { sink.text(foundCharacters) }
    @ObjCSignatureOverride
    override fun parser(parser: NSXMLParser, foundIgnorableWhitespace: String) = event(parser) { sink.text(foundIgnorableWhitespace) }
    @ObjCSignatureOverride
    override fun parser(parser: NSXMLParser, foundCDATA: NSData) = event(parser) {
        if (foundCDATA.length > MAX_METADATA_BYTES.toULong()) throw MetadataBudgetExceeded()
        val content = ByteArray(foundCDATA.length.toInt())
        if (content.isNotEmpty()) content.usePinned { memcpy(it.addressOf(0), foundCDATA.bytes, foundCDATA.length) }
        sink.text(content.decodeToString(throwOnInvalidSequence = true))
    }
    @ObjCSignatureOverride
    override fun parser(parser: NSXMLParser, resolveExternalEntityName: String, systemID: String?): NSData? {
        event(parser) { throw InvalidMetadata() }
        return null
    }
}
