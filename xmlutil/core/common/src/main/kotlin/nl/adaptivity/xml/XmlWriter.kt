/*
 * Copyright (c) 2018.
 *
 * This file is part of ProcessManager.
 *
 * ProcessManager is free software: you can redistribute it and/or modify it under the terms of version 3 of the
 * GNU Lesser General Public License as published by the Free Software Foundation.
 *
 * ProcessManager is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with ProcessManager.  If not,
 * see <http://www.gnu.org/licenses/>.
 */
@file:JvmName("XmlWriterUtilCore")

package nl.adaptivity.xml

import nl.adaptivity.util.multiplatform.*
import nl.adaptivity.util.xml.XmlDelegatingWriter
import nl.adaptivity.xml.XMLConstants.DEFAULT_NS_PREFIX
import nl.adaptivity.xml.XMLConstants.NULL_NS_URI

/**
 * Created by pdvrieze on 15/11/15.
 */
interface XmlWriter : Closeable {

    val depth: Int

    @Deprecated("Use the version that takes strings",
                ReplaceWith("setPrefix(prefix.toString(), namespaceUri.toString())"))
    fun setPrefix(prefix:CharSequence, namespaceUri: CharSequence) = setPrefix(prefix.toString(), namespaceUri.toString())

    /**
     * Bind the prefix to the given uri for this element.
     */
    fun setPrefix(prefix: String, namespaceUri: String)

    @Deprecated("Use the version that takes strings",
                ReplaceWith("namespaceAttr(namespacePrefix.toString(), namespaceUri.toString())"))
    fun namespaceAttr(namespacePrefix: CharSequence, namespaceUri: CharSequence) = namespaceAttr(namespacePrefix.toString(), namespaceUri.toString())

    fun namespaceAttr(namespacePrefix: String, namespaceUri: String)

    override fun close()

    /**
     * Flush all state to the underlying buffer
     */
    fun flush()

    /**
     * Write a start tag.
     * @param namespace The namespace to use for the tag.
     *
     * @param localName The local name for the tag.
     *
     * @param prefix The prefix to use, or `null` for the namespace to be assigned automatically
     */
    fun startTag(namespace: String?, localName: String, prefix: String?)

    /**
     * Write a comment.
     * @param text The comment text
     */
    fun comment(text: String)

    /**
     * Write text.
     * @param text The text content.
     */
    fun text(text: String)

    /**
     * Write a CDATA section
     * @param text The text of the section.
     */
    fun cdsect(text: String)

    fun entityRef(text: String)

    fun processingInstruction(text: String)

    fun ignorableWhitespace(text: String)

    fun attribute(namespace: String?, name: String, prefix: String?, value: String)

    fun docdecl(text: String)

    fun startDocument(version: String?, encoding: String?, standalone: Boolean?)

    fun endDocument()

    fun endTag(namespace: String?, localName: String, prefix: String?)

    val namespaceContext: NamespaceContext

    /**
     * Get the namespace uri the prefix is currently bound to
     */
    fun getNamespaceUri(prefix: String): String?

    /**
     * Get a prefix the uri is currently bound to
     */
    fun getPrefix(namespaceUri: String?): String?
}


fun XmlWriter.addUndeclaredNamespaces(reader: XmlReader, missingNamespaces: MutableMap<String, String>) {
    undeclaredPrefixes(reader, missingNamespaces)
}

private fun XmlWriter.undeclaredPrefixes(reader: XmlReader, missingNamespaces: MutableMap<String, String>) {
    assert(reader.eventType === EventType.START_ELEMENT)
    val prefix = reader.prefix
    if (!missingNamespaces.containsKey(prefix)) {
        val uri = reader.namespaceUri
        if (getNamespaceUri(prefix) == uri && reader.isPrefixDeclaredInElement(prefix)) {
            return
        } else if (uri.isNotEmpty()) {
            if (getNamespaceUri(prefix) != uri) {
                missingNamespaces[prefix] = uri
            }
        }
    }
}


fun XmlWriter.writeChild(child: XmlSerializable?) {
    child?.serialize(this)
}

fun XmlWriter.writeChildren(children: Iterable<XmlSerializable>?) {
    children?.forEach { writeChild(it) }
}

fun XmlWriter.serialize(reader: XmlReader) {
    while (reader.hasNext()) {
        val eventType = reader.next()
        when (eventType) {
            EventType.START_DOCUMENT,
            EventType.PROCESSING_INSTRUCTION,
            EventType.DOCDECL,
            EventType.END_DOCUMENT -> {
                if (depth <= 0) {
                    writeCurrentEvent(reader)
                }
            }
            else                   -> writeCurrentEvent(reader)
        }
    }
}

fun XmlWriter.writeCurrentEvent(reader: XmlReader) {
    when (reader.eventType) {
        EventType.START_DOCUMENT         -> startDocument(null, reader.encoding, reader.standalone)
        EventType.START_ELEMENT          -> {
            startTag(reader.namespaceUri, reader.localName, reader.prefix)
            run {
                for (i in reader.namespaceIndices) {
                    namespaceAttr(reader.getNamespacePrefix(i), reader.getNamespaceUri(i))
                }
            }
            run {
                for (i in reader.attributeIndices) {
                    attribute(reader.getAttributeNamespace(i), reader.getAttributeLocalName(i),
                              null, reader.getAttributeValue(i))
                }
            }
        }
        EventType.END_ELEMENT            -> endTag(reader.namespaceUri, reader.localName,
                                                   reader.prefix)
        EventType.COMMENT                -> comment(reader.text)
        EventType.TEXT                   -> text(reader.text)
        EventType.ATTRIBUTE              -> attribute(reader.namespaceUri, reader.localName,
                                                      reader.prefix, reader.text)
        EventType.CDSECT                 -> cdsect(reader.text)
        EventType.DOCDECL                -> docdecl(reader.text)
        EventType.END_DOCUMENT           -> endDocument()
        EventType.ENTITY_REF             -> entityRef(reader.text)
        EventType.IGNORABLE_WHITESPACE   -> ignorableWhitespace(reader.text)
        EventType.PROCESSING_INSTRUCTION -> processingInstruction(reader.text)
    }
}


/**
 * Enhanced function for writing start tags, that will attempt to reuse prefixes.
 */
fun XmlWriter.smartStartTag(qName: QName) {
    smartStartTag(qName.getNamespaceURI(), qName.getLocalPart(), qName.getPrefix())
}

/**
 * Enhanced function for writing start tags, that will attempt to reuse prefixes.
 */
inline fun XmlWriter.smartStartTag(qName: QName, body: XmlWriter.() -> Unit) {
    smartStartTag(qName.getNamespaceURI(), qName.getLocalPart(), qName.getPrefix(), body)
}

@Deprecated("Use strings", ReplaceWith("smartStartTag(nsUri?.toString(), localName.toString(), prefix?.toString())"))
@JvmOverloads
fun XmlWriter.smartStartTag(nsUri: CharSequence?, localName: CharSequence, prefix: CharSequence? = null)=
    smartStartTag(nsUri?.toString(), localName.toString(), prefix?.toString())

/**
 * Enhanced function for writing start tags, that will attempt to reuse prefixes.
 */
@JvmOverloads
fun XmlWriter.smartStartTag(nsUri: String?, localName: String, prefix: String? = null) {
    if (nsUri == null) {
        val namespace = namespaceContext.getNamespaceURI(prefix ?: DEFAULT_NS_PREFIX) ?: NULL_NS_URI
        startTag(namespace, localName, prefix)
    } else {
        var writeNs = false

        val usedPrefix = getPrefix(nsUri) ?: run {
            if (nsUri != NULL_NS_URI) { writeNs = true }; prefix ?: DEFAULT_NS_PREFIX
        }

        startTag(nsUri, localName, usedPrefix)

        if (writeNs) this.namespaceAttr(usedPrefix, nsUri)
    }
}

@Deprecated("Use strings",
            ReplaceWith("smartStartTag(nsUri?.toString(), localName.toString(), prefix?.toString(), body)"))
inline fun XmlWriter.smartStartTag(nsUri: CharSequence?,
                                   localName: CharSequence,
                                   prefix: CharSequence? = null,
                                   body: XmlWriter.() -> Unit) =
    smartStartTag(nsUri?.toString(), localName.toString(), prefix?.toString(), body)

@JvmOverloads
inline fun XmlWriter.smartStartTag(nsUri: String?,
                                   localName: String,
                                   prefix: String? = null,
                                   body: XmlWriter.() -> Unit) {
    smartStartTag(nsUri, localName, prefix)
    body()
    endTag(nsUri, localName, prefix)
}


inline fun <T> XmlWriter.writeListIfNotEmpty(iterable: Iterable<T>,
                                             nsUri: String?,
                                             localName: String,
                                             prefix: String? = null,
                                             body: XmlWriter.(T) -> Unit) {
    val it = iterable.iterator()
    if (it.hasNext()) {
        smartStartTag(nsUri, localName, prefix)
        while (it.hasNext()) {
            body(it.next())
        }
        endTag(nsUri, localName, prefix)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T : XmlSerializable> XmlWriter.serializeAll(iterable: Iterable<T>) = iterable.forEach { it.serialize(this) }

@Suppress("NOTHING_TO_INLINE")
inline fun <T : XmlSerializable> XmlWriter.serializeAll(sequence: Sequence<T>) = sequence.forEach { it.serialize(this) }


inline fun XmlWriter.startTag(nsUri: String?,
                              localName: String,
                              prefix: String? = null,
                              body: XmlWriter.() -> Unit) {
    startTag(nsUri, localName, prefix)
    body()
    endTag(nsUri, localName, prefix)
}

fun XmlWriter.writeSimpleElement(qName: QName, value: String?) {
    writeSimpleElement(qName.getNamespaceURI(), qName.getLocalPart(), qName.getPrefix(), value)
}

fun XmlWriter.writeSimpleElement(nsUri: String?,
                                 localName: String,
                                 prefix: String?,
                                 value: String?) {
    smartStartTag(nsUri, localName, prefix)
    if (!value.isNullOrEmpty()) {
        text(value.toString())
    }
    endTag(nsUri, localName, prefix)
}

fun XmlWriter.writeAttribute(name: String, value: String?) {
    value?.let { attribute(null, name, null, it) }
}

fun XmlWriter.writeAttribute(name: String, value: Any?) {
    value?.let { attribute(null, name, null, it.toString()) }
}

fun XmlWriter.writeAttribute(name: QName, value: String?) {
    value?.let { attribute(name.getNamespaceURI(), name.getLocalPart(), name.getPrefix(), value) }
}

fun XmlWriter.writeAttribute(name: String, value: Double) {
    if (!value.isNaN()) {
        attribute(null, name, null, value.toString())
    }
}

fun XmlWriter.writeAttribute(name: String, value: Long) {
    attribute(null, name, null, value.toString())
}

fun XmlWriter.writeAttribute(name: String, value: QName?) {
    if (value != null) {
        var prefix: String?
        if (value.getNamespaceURI().isNotEmpty()) {
            if (value.getNamespaceURI() == namespaceContext.getNamespaceURI(value.getPrefix())) {
                prefix = value.getPrefix()
            } else {
                prefix = namespaceContext.getPrefix(value.getNamespaceURI())
                if (prefix == null) {
                    prefix = value.getPrefix()
                    namespaceAttr(prefix, value.getNamespaceURI())
                }
            }
        } else { // namespace not specified
            prefix = value.getPrefix()
            if (prefix.let { namespaceContext.getNamespaceURI(it) } == null) throw IllegalArgumentException(
                "Cannot determine namespace of qname")
        }
        attribute(null, name, null, prefix + ':' + value.getLocalPart())
    }
}

fun XmlWriter.endTag(predelemname: QName) {
    this.endTag(predelemname.getNamespaceURI(), predelemname.getLocalPart(), predelemname.getPrefix())
}

fun XmlWriter.filterSubstream(): XmlWriter {
    return SubstreamFilterWriter(this)
}


private fun undeclaredPrefixes(reader: XmlReader,
                               reference: XmlWriter,
                               missingNamespaces: MutableMap<String, String>) {
    assert(reader.eventType === EventType.START_ELEMENT)
    val prefix = reader.prefix
    if (!missingNamespaces.containsKey(prefix)) {
        val uri = reader.namespaceUri
        if (uri == reference.getNamespaceUri(prefix)
            && reader.isPrefixDeclaredInElement(prefix)) {
            return
        } else if (uri.isNotEmpty()) {
            if (uri != reference.getNamespaceUri(prefix)) {
                missingNamespaces[prefix] = uri
            }
        }
    }
}

fun XmlWriter.writeElementContent(missingNamespaces: MutableMap<String, String>?, reader: XmlReader) {
    reader.forEach { type ->
        reader.writeCurrent(this)

        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (type) {
            EventType.START_ELEMENT -> {
                if (missingNamespaces != null) addUndeclaredNamespaces(reader, missingNamespaces)

                writeElementContent(missingNamespaces, reader)
            }
            EventType.END_ELEMENT   -> return
        }
    }
}


private class SubstreamFilterWriter(delegate: XmlWriter) : XmlDelegatingWriter(delegate) {

    override fun processingInstruction(text: String) { /* ignore */ }

    override fun endDocument() { /* ignore */ }

    override fun docdecl(text: String) { /* ignore */ }

    override fun startDocument(version: String?, encoding: String?, standalone: Boolean?) { /* ignore */ }
}
