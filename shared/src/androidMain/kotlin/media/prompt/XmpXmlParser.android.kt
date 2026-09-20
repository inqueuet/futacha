@file:OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.media.prompt

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.SAXParserFactory

internal actual fun parseXmpDocument(bytes: ByteArray, sink: XmpEventSink) {
    val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
    // DTDs are rejected by the common preflight too. Android does not expose
    // every SAX feature, so optional hardening must not disable valid packets.
    for ((feature, value) in listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false
    )) runCatching { factory.setFeature(feature, value) }
    val parser = factory.newSAXParser().xmlReader
    var callbackFailure: Throwable? = null
    fun event(action: () -> Unit) {
        try { action() } catch (failure: Throwable) {
            callbackFailure = failure
            throw SAXException("Metadata parsing stopped")
        }
    }
    val handler = object : DefaultHandler() {
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) = event {
            sink.start(XmpName(uri, localName), (0 until attributes.length).map { index ->
                XmpAttribute(XmpName(attributes.getURI(index), attributes.getLocalName(index)), attributes.getValue(index))
            })
        }
        override fun characters(ch: CharArray, start: Int, length: Int) = event { sink.text(ch.concatToString(start, start + length)) }
        override fun endElement(uri: String, localName: String, qName: String) = event { sink.end() }
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw InvalidMetadata()
        override fun error(e: SAXParseException): Unit = throw InvalidMetadata()
        override fun fatalError(e: SAXParseException): Unit = throw InvalidMetadata()
    }
    parser.contentHandler = handler
    parser.errorHandler = handler
    parser.entityResolver = handler
    try { parser.parse(InputSource(bytes.inputStream())) }
    catch (_: SAXException) { throw callbackFailure ?: InvalidMetadata() }
}
