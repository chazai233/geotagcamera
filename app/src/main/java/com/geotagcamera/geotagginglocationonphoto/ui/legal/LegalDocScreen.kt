package com.geotagcamera.geotagginglocationonphoto.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.ui.theme.MonoDataStyle
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The legal documents bundled in the app (copied from the repository at build time). */
enum class LegalDoc(val title: String, val asset: String) {
    PRIVACY("Privacy policy", "legal/PRIVACY.md"),
    TERMS("Terms of use", "legal/TERMS.md"),
    NOTICES("Open-source notices", "legal/THIRD_PARTY_NOTICES.md"),
    LICENSE("Licence (GPLv3)", "legal/LICENSE"),
    APACHE("Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
    OFL("SIL Open Font License 1.1", "legal/licenses/OFL-1.1.txt"),
    MAPLIBRE("MapLibre BSD 2-Clause", "legal/licenses/MapLibre-BSD-2-Clause.txt");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name }
    }
}

/** Reads one bundled document and shows it offline. Markdown gets light formatting; licences show as plain text. */
@Composable
fun LegalDocScreen(doc: LegalDoc, onBack: () -> Unit) {
    val context = LocalContext.current
    val blocks by produceState<List<MdBlock>?>(null, doc) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(doc.asset).bufferedReader().use { it.readText() } }
                .map { text -> if (doc.asset.endsWith(".md")) parseMarkdown(text) else listOf(MdBlock.Plain(text)) }
                .getOrElse { listOf(MdBlock.Paragraph("This document couldn't be opened. It is also at github.com/konkomaji/geotagcamera.")) }
        }
    }
    AppScaffold(title = doc.title, onBack = onBack) { padding ->
        val list = blocks
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@AppScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.L, vertical = Spacing.S),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(list) { block -> Box(Modifier.fillMaxWidth().widthIn(max = 720.dp)) { Block(block) } }
            item { Spacer(Modifier.size(Spacing.XXL)) }
        }
    }
}

@Composable
private fun Block(b: MdBlock) {
    val links = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.secondary, textDecoration = TextDecoration.Underline))
    when (b) {
        is MdBlock.Heading -> Text(
            inline(b.text, links),
            style = when (b.level) { 1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium },
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = if (b.level == 1) Spacing.S else Spacing.L, bottom = Spacing.XS)
        )
        is MdBlock.Paragraph -> Text(inline(b.text, links), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Spacing.XS))
        is MdBlock.Bullet -> Row(Modifier.padding(start = Spacing.S, top = 2.dp, bottom = 2.dp)) {
            Text("•", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(Spacing.S))
            Text(inline(b.text, links), style = MaterialTheme.typography.bodyMedium)
        }
        is MdBlock.Code -> Text(
            b.text,
            style = MonoDataStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.XS)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .horizontalScroll(rememberScrollState())
                .padding(Spacing.M)
        )
        is MdBlock.Table -> Column(Modifier.padding(vertical = Spacing.XS)) {
            b.rows.forEachIndexed { i, cells ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (i == 0) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .padding(Spacing.S)
                ) {
                    cells.forEachIndexed { c, cell ->
                        Text(
                            inline(cell, links),
                            style = if (c == 0) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.bodySmall,
                            color = if (c == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        is MdBlock.Plain -> Text(b.text, style = MaterialTheme.typography.bodySmall)
    }
}

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data class Plain(val text: String) : MdBlock
}

/** Just enough Markdown for our own documents: headings, paragraphs, bullets, code fences, tables. */
fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim()); para.clear() }
    val lines = src.replace("\r\n", "\n").lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) { code.appendLine(lines[i]); i++ }
                out += MdBlock.Code(code.toString().trimEnd())
            }
            t.startsWith("#") -> {
                flush()
                val level = t.takeWhile { it == '#' }.length
                out += MdBlock.Heading(level, t.drop(level).trim())
            }
            t.startsWith("- ") || t.startsWith("* ") -> { flush(); out += MdBlock.Bullet(t.drop(2)) }
            t.startsWith("|") -> {
                flush()
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                    if (!cells.all { it.matches(Regex(":?-{2,}:?")) }) rows += cells
                    i++
                }
                i--
                out += MdBlock.Table(rows)
            }
            t.isEmpty() -> flush()
            else -> { if (para.isNotEmpty()) para.append(' '); para.append(t) }
        }
        i++
    }
    flush()
    return out
}

/** **bold**, `code` and [text](url) / bare https links. */
private fun inline(text: String, links: TextLinkStyles): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)|(https?://[^\s)]+)""")
    var last = 0
    pattern.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        when {
            m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
            m.groups[2] != null -> withStyle(SpanStyle(fontFamily = MonoDataStyle.fontFamily)) { append(m.groupValues[2]) }
            m.groups[3] != null -> {
                val url = m.groupValues[4].let { if (it.startsWith("http")) it else "https://github.com/konkomaji/geotagcamera/blob/main/$it" }
                withLink(LinkAnnotation.Url(url, links)) { append(m.groupValues[3]) }
            }
            else -> withLink(LinkAnnotation.Url(m.value, links)) { append(m.value) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
