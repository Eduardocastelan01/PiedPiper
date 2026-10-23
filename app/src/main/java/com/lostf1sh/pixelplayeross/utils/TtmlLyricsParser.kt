package com.lostf1sh.pixelplayeross.utils

import java.io.StringReader
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource

internal object TtmlLyricsParser {
    private const val MAX_TTML_PARAGRAPHS = 5_000
    private const val TTML_METADATA_NS = "http://www.w3.org/ns/ttml#metadata"
    private const val XML_NS = "http://www.w3.org/XML/1998/namespace"
    private const val GROUP_AGENT_VOICE = 1_000
    private val NUMBERED_AGENT_REGEX = Regex("^v(\\d{1,4})$", RegexOption.IGNORE_CASE)

    // A closing tag that the next syllable's opening tag repeats ("I<00:09.44> <00:09.44>bet").
    private val REDUNDANT_END_TAG_REGEX = Regex("<(\\d{2}:\\d{2}\\.\\d{2})>(\\s*)(?=<\\1>)")

    fun parseToEnhancedLrc(ttmlText: String): String? {
        return runCatching {
            val normalizedTtml = normalizeTtmlDocument(ttmlText)
            if (normalizedTtml.isBlank()) {
                return null
            }

            val builder = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
            }.newDocumentBuilder().apply {
                setEntityResolver(EntityResolver { _, _ -> InputSource(StringReader("")) })
            }

            val document = builder.parse(InputSource(StringReader(normalizedTtml)))
            val paragraphNodes = document.getElementsByTagNameNS("*", "p")
            if (paragraphNodes.length == 0 || paragraphNodes.length > MAX_TTML_PARAGRAPHS) {
                return null
            }

            val voices = AgentVoices(document)
            buildList<Pair<Int, String>> {
                for (index in 0 until paragraphNodes.length) {
                    val paragraph = paragraphNodes.item(index) as? Element ?: continue
                    val beginMs = resolveParagraphStartMs(paragraph) ?: continue
                    val extras = ParagraphExtras()
                    val serializedBody = normalizeParagraphBody(serializeChildren(paragraph, extras))
                        .withoutRedundantEndTags()
                    if (serializedBody.isBlank()) continue
                    val timestamp = "[${formatLrcTimestamp(beginMs)}]"
                    val voicePrefix = voices.voiceOf(paragraph)?.let { "v$it:" }.orEmpty()
                    add(
                        beginMs to buildString {
                            append(timestamp).append(voicePrefix).append(serializedBody)
                            extras.background.toString().replace('\n', ' ').trim().withoutRedundantEndTags()
                                .takeIf { it.isNotEmpty() }
                                ?.let { append("\n[bg:").append(it).append(']') }
                            extras.translation.toString().trim()
                                .takeIf { it.isNotEmpty() }
                                ?.let { append('\n').append(timestamp).append(it) }
                        }
                    )
                }
            }.sortedBy { it.first }
                .joinToString("\n") { it.second }
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun normalizeTtmlDocument(raw: String): String {
        val withoutLeadingNoise = raw.trimStart { char ->
            char.isWhitespace() ||
                char == '\uFEFF' ||
                Character.getType(char).toByte() == Character.FORMAT
        }

        return if (withoutLeadingNoise.startsWith("<?xml", ignoreCase = true)) {
            withoutLeadingNoise.substringAfter("?>", missingDelimiterValue = withoutLeadingNoise).trimStart()
        } else {
            withoutLeadingNoise
        }
    }

    private fun resolveParagraphStartMs(paragraph: Element): Int? {
        parseTimeExpression(paragraph.getAttribute("begin"))?.let { return it }

        val spans = paragraph.getElementsByTagNameNS("*", "span")
        for (index in 0 until spans.length) {
            val span = spans.item(index) as? Element ?: continue
            parseTimeExpression(span.getAttribute("begin"))?.let { return it }
        }
        return null
    }

    /**
     * Inline parts of a `<p>` that are not sung as the main line: backing vocals
     * (`ttm:role="x-bg"`) and translations (`ttm:role="x-translation"`, as AMLL writes them).
     */
    private class ParagraphExtras {
        val background = StringBuilder()
        val translation = StringBuilder()
    }

    /**
     * Maps `ttm:agent` ids to singer numbers: Apple's `v1`, `v2` and `v1000` keep their number,
     * agents declared as a group sing in the middle, and any other id is numbered by first use.
     */
    private class AgentVoices(document: Document) {
        private val groupAgentIds = buildSet {
            val agents = document.getElementsByTagNameNS(TTML_METADATA_NS, "agent")
            for (index in 0 until agents.length) {
                val agent = agents.item(index) as? Element ?: continue
                if (agent.getAttribute("type").equals("group", ignoreCase = true)) {
                    add(agent.getAttributeNS(XML_NS, "id").ifEmpty { agent.getAttribute("xml:id") })
                }
            }
        }
        private val ordinals = mutableMapOf<String, Int>()

        fun voiceOf(paragraph: Element): Int? {
            val agentId = agentIdOf(paragraph)
                ?: (paragraph.parentNode as? Element)?.let(::agentIdOf)
                ?: return null
            NUMBERED_AGENT_REGEX.matchEntire(agentId)?.let { match ->
                return match.groupValues[1].toInt()
            }
            if (agentId in groupAgentIds) return GROUP_AGENT_VOICE
            return ordinals.getOrPut(agentId) { ordinals.size + 1 }
        }

        private fun agentIdOf(element: Element): String? =
            element.getAttributeNS(TTML_METADATA_NS, "agent")
                .ifEmpty { element.getAttribute("ttm:agent") }
                .trim()
                .takeIf { it.isNotEmpty() }
    }

    private fun serializeChildren(node: Node, extras: ParagraphExtras?): String {
        val builder = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            builder.append(serializeNode(child, extras))
            child = child.nextSibling
        }
        return builder.toString()
    }

    private fun serializeNode(node: Node, extras: ParagraphExtras?): String {
        return when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> sanitizeTextFragment(node.nodeValue.orEmpty())
            Node.ELEMENT_NODE -> serializeElement(node as Element, extras)
            else -> ""
        }
    }

    /**
     * [extras] is null inside a backing-vocal span: its own translation and nested parts are
     * dropped rather than mixed into the main line.
     */
    private fun serializeElement(element: Element, extras: ParagraphExtras?): String {
        return when (localNameOf(element)) {
            "br" -> "\n"
            "span" -> when (roleOf(element)) {
                "x-bg" -> {
                    extras?.background?.appendSegment(serializeTimedSpan(element, extras = null))
                    ""
                }
                "x-translation" -> {
                    extras?.translation?.appendSegment(sanitizeTextFragment(element.textContent.orEmpty()).trim())
                    ""
                }
                // Romanization is generated when the lyrics are parsed; inlined it would repeat the line.
                "x-roman" -> ""
                else -> serializeTimedSpan(element, extras)
            }
            else -> serializeChildren(element, extras)
        }
    }

    private fun serializeTimedSpan(element: Element, extras: ParagraphExtras?): String {
        val content = normalizeInlineText(serializeChildren(element, extras))
        if (content.isBlank()) return ""
        val beginMs = parseTimeExpression(element.getAttribute("begin")) ?: return content
        // The closing tag tells the highlight when this syllable stops being sung.
        val endTag = parseTimeExpression(element.getAttribute("end"))
            ?.takeIf { it > beginMs }
            ?.let { "<${formatLrcTimestamp(it)}>" }
            .orEmpty()
        return "<${formatLrcTimestamp(beginMs)}>$content$endTag"
    }

    private fun String.withoutRedundantEndTags(): String = replace(REDUNDANT_END_TAG_REGEX, "$2")

    private fun roleOf(element: Element): String =
        element.getAttributeNS(TTML_METADATA_NS, "role")
            .ifEmpty { element.getAttribute("ttm:role") }
            .trim()
            .lowercase(Locale.ROOT)

    private fun StringBuilder.appendSegment(segment: String) {
        if (segment.isBlank()) return
        if (isNotEmpty()) append(' ')
        append(segment)
    }

    private fun localNameOf(element: Element): String {
        return element.localName?.lowercase()
            ?: element.tagName.substringAfterLast(':').lowercase()
    }

    private fun sanitizeTextFragment(raw: String): String {
        if (raw.isEmpty()) return raw

        val cleaned = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .filterNot { char ->
                Character.getType(char).toByte() == Character.FORMAT ||
                    (Character.isISOControl(char) && char != '\n' && char != '\t')
            }

        if (cleaned.isEmpty()) return ""
        if (cleaned.all { it.isWhitespace() }) {
            return if (cleaned.any { it == '\n' || it == '\t' }) "" else " "
        }

        return cleaned.replace(Regex("\\s+"), " ")
    }

    private fun normalizeInlineText(value: String): String {
        return value
            .replace(Regex("[ ]{2,}"), " ")
            .replace(Regex(" *\n *"), "\n")
            .trim('\n')
    }

    private fun normalizeParagraphBody(value: String): String {
        return value
            .lineSequence()
            .map { line -> line.replace(Regex("[ ]{2,}"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun parseTimeExpression(value: String?): Int? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return null

        if (normalized.endsWith("s", ignoreCase = true)) {
            val seconds = normalized.removeSuffix("s").toDoubleOrNull() ?: return null
            return (seconds * 1000).roundToInt()
        }

        normalized.toDoubleOrNull()?.let { seconds ->
            return (seconds * 1000).roundToInt()
        }

        val parts = normalized.split(':')
        return when (parts.size) {
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                val (seconds, millis) = parseSecondsWithFraction(parts[1]) ?: return null
                (minutes * 60_000L + seconds * 1_000L + millis).toInt()
            }
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val (seconds, millis) = parseSecondsWithFraction(parts[2]) ?: return null
                (hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis).toInt()
            }
            else -> null
        }
    }

    private fun parseSecondsWithFraction(value: String): Pair<Long, Long>? {
        val parts = value.split('.', limit = 2)
        val seconds = parts[0].toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1).orEmpty()
        if (fraction.isEmpty()) return seconds to 0L

        val digits = fraction.take(3).padEnd(3, '0')
        val millis = digits.toLongOrNull() ?: return null
        return seconds to millis
    }

    private fun formatLrcTimestamp(timeMs: Int): String = LyricsUtils.formatLrcTimestamp(timeMs)
}
