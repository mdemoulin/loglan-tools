// Part of loglan-tools <https://github.com/mdemoulin/loglan-tools>.
// Copyright (C) 2026  Michael A. Demoulin <35350967+mdemoulin@users.noreply.github.com>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package io.github.mdemoulin.loglan.teach

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A lesson of m1-text, on a page of its own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonPage(n: Int, text: String, done: () -> Unit) {
    val blocks = remember(text) { lessonBlocks(text) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Lesson $n") }) },
        bottomBar = {
            Button(onClick = done, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Text("Go on")
            }
        },
    ) { padding ->
        SelectionContainer {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (b in blocks) when (b) {
                    is LessonBlock.Prose -> Text(b.text, style = MaterialTheme.typography.bodyLarge)
                    is LessonBlock.Table -> Table(b.text)
                }
            }
        }
    }
}

/**
 * A table, in a fixed-width font small enough for its widest line to fit
 * the screen, down to 9sp; wider than that, it scrolls sideways.
 */
@Composable
private fun Table(text: String) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widest = text.lines().maxBy { it.length }
        val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 0.sp)
        val width = measurer.measure(widest, style, softWrap = false).size.width.coerceAtLeast(1)
        // A little to spare: the measure and the drawing can differ by a pixel or two.
        val size = (14f * 0.97f * constraints.maxWidth / width).coerceIn(9f, 14f)
        Text(
            text, style = style.copy(fontSize = size.sp, lineHeight = (size * 1.4f).sp), softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

sealed interface LessonBlock {
    data class Prose(val text: String) : LessonBlock
    data class Table(val text: String) : LessonBlock
}

/**
 * m1-text is written for an 80-column terminal: paragraphs wrapped by hand,
 * each begun with an indent, and tables laid out with tabs.  On a phone the
 * paragraphs are rewrapped -- the lines of one joined, a word broken at a
 * hyphen made whole again -- and a block whose every line is indented (or
 * has a tab in it) is kept as it is, in a fixed-width font.
 */
fun lessonBlocks(text: String): List<LessonBlock> {
    val out = mutableListOf<LessonBlock>()
    for (block in text.split(Regex("""\n\s*\n"""))) {
        val lines = block.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) continue
        val indented = { l: String -> l.first().isWhitespace() }
        if (lines.any { '\t' in it.trim() } && lines.all { indented(it) || '\t' in it } ||
            lines.size > 1 && lines.all(indented)) {
            val expanded = lines.map(::expandTabs)
            val indent = expanded.minOf { l -> l.takeWhile { it == ' ' }.length }
            out += LessonBlock.Table(expanded.joinToString("\n") { it.drop(indent).trimEnd() })
            continue
        }
        val para = StringBuilder()
        for (l in lines) {
            if (indented(l) && para.isNotEmpty()) {
                out += LessonBlock.Prose(para.toString())
                para.clear()
            }
            val t = l.trim().replace('\t', ' ')
            if (para.isNotEmpty() && !para.endsWith("-")) para.append(' ')
            para.append(t)
        }
        out += LessonBlock.Prose(para.toString())
    }
    return out
}

private fun expandTabs(line: String): String {
    val s = StringBuilder()
    for (c in line) {
        if (c == '\t') do s.append(' ') while (s.length % 8 != 0) else s.append(c)
    }
    return s.toString()
}
