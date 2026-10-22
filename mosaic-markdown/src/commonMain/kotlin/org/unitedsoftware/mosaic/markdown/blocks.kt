package org.unitedsoftware.mosaic.markdown

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko

/** A block of a Markdown document, like a paragraph or a list. */
@Immutable
internal sealed interface Block

/** A heading of [level] 1 to 6, with its [text] still to be parsed for inline content. */
@Poko
internal class Heading(val level: Int, val text: String) : Block

/** A paragraph, with its [text] still to be parsed for inline content. */
@Poko
internal class Paragraph(val text: String) : Block

/**
 * Fenced code. [closed] is false while the closing fence did not arrive yet, in which case the
 * block runs until the end of the document.
 */
@Poko
internal class CodeBlock(val language: String, val code: String, val closed: Boolean) : Block

@Poko
internal class Quote(val children: List<Block>) : Block

@Poko
internal class ListBlock(
	/** The number of the first item, or null for a bullet list. */
	val start: Int?,
	val items: List<ListItem>,
	/** Whether the items were separated by blank lines. */
	val loose: Boolean,
) : Block

@Poko
internal class ListItem(
	val children: List<Block>,
	/** Whether this is a checked task, an unchecked one, or not a task at all. */
	val checked: Boolean?,
)

internal enum class ColumnAlignment { Start, Center, End }

@Poko
internal class Table(
	val header: List<String>,
	val alignments: List<ColumnAlignment>,
	val rows: List<List<String>>,
) : Block

internal object ThematicBreak : Block {
	override fun toString() = "ThematicBreak"
}

/**
 * Parses the blocks of a Markdown document, following CommonMark and GitHub Flavored Markdown for
 * the parts which matter in a terminal. Raw HTML and link reference definitions are left as text,
 * and indented code is not supported since it is easily confused with list continuations.
 *
 * Incomplete documents, like the ones streamed in from a language model, parse as far as they go:
 * unclosed code fences run until the end, and a table becomes one once its delimiter row arrives.
 */
internal fun parseBlocks(text: String): List<Block> = parseBlocks(text.lines().map { it.expandTabs() })

private fun String.expandTabs(): String {
	if ('\t' !in this) return this
	return buildString {
		for (char in this@expandTabs) {
			if (char == '\t') {
				do append(' ') while (length % 4 != 0)
			} else {
				append(char)
			}
		}
	}
}

private val fenceRegex = Regex("""^( {0,3})(`{3,}|~{3,})\s*([^`\s]*)[^`]*$""")
private val headingRegex = Regex("""^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$""")
private val thematicBreakRegex = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")
private val quoteRegex = Regex("""^ {0,3}> ?(.*)$""")
private val listItemRegex = Regex("""^( {0,3})([-*+]|\d{1,9}[.)])(?:( +)(.*)|$)""")
private val tableDelimiterRegex = Regex("""^ {0,3}\|?\s*:?-+:?\s*(?:\|\s*:?-+:?\s*)*\|?\s*$""")

private fun parseBlocks(lines: List<String>): List<Block> {
	val blocks = ArrayList<Block>()
	var index = 0
	while (index < lines.size) {
		val line = lines[index]
		if (line.isBlank()) {
			index++
			continue
		}

		val fenceMatch = fenceRegex.matchEntire(line)
		if (fenceMatch != null) {
			val indent = fenceMatch.groupValues[1].length
			val fence = fenceMatch.groupValues[2]
			val language = fenceMatch.groupValues[3]
			val code = ArrayList<String>()
			var closed = false
			index++
			while (index < lines.size) {
				val codeLine = lines[index]
				val trimmed = codeLine.trimStart(' ')
				index++
				if (codeLine.length - trimmed.length <= 3 &&
					trimmed.startsWith(fence) &&
					trimmed.trimEnd().all { it == fence[0] }
				) {
					closed = true
					break
				}
				code += codeLine.removeIndent(indent)
			}
			blocks += CodeBlock(language, code.joinToString("\n"), closed)
			continue
		}

		val headingMatch = headingRegex.matchEntire(line)
		if (headingMatch != null) {
			blocks += Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2])
			index++
			continue
		}

		if (thematicBreakRegex.matches(line)) {
			blocks += ThematicBreak
			index++
			continue
		}

		if (quoteRegex.matches(line)) {
			val quoted = ArrayList<String>()
			while (index < lines.size) {
				val match = quoteRegex.matchEntire(lines[index])
				if (match != null) {
					quoted += match.groupValues[1]
				} else if (lines[index].isNotBlank() && quoted.lastOrNull()?.isNotBlank() == true && !startsBlock(lines, index)) {
					// A lazy continuation of the quoted paragraph.
					quoted += lines[index]
				} else {
					break
				}
				index++
			}
			blocks += Quote(parseBlocks(quoted))
			continue
		}

		if (listItemRegex.matches(line)) {
			index = parseList(lines, index, blocks)
			continue
		}

		if (isTableStart(lines, index)) {
			index = parseTable(lines, index, blocks)
			continue
		}

		val paragraph = ArrayList<String>()
		while (index < lines.size && lines[index].isNotBlank()) {
			if (paragraph.isNotEmpty() && startsBlock(lines, index, interruptingParagraph = true)) break
			paragraph += lines[index].trim()
			index++
		}
		blocks += Paragraph(paragraph.joinToString("\n"))
	}
	return blocks
}

private fun String.removeIndent(indent: Int): String {
	var remove = 0
	while (remove < indent && remove < length && this[remove] == ' ') remove++
	return substring(remove)
}

/** Whether the line at [index] starts a block other than a paragraph. */
private fun startsBlock(lines: List<String>, index: Int, interruptingParagraph: Boolean = false): Boolean {
	val line = lines[index]
	if (fenceRegex.matches(line) || headingRegex.matches(line) || thematicBreakRegex.matches(line)) return true
	if (quoteRegex.matches(line)) return true
	val item = listItemRegex.matchEntire(line)
	if (item != null) {
		if (!interruptingParagraph) return true
		// Like in CommonMark, only lists which start with 1 and are not empty interrupt paragraphs.
		val marker = item.groupValues[2]
		val content = item.groupValues[4]
		return content.isNotBlank() && (!marker[0].isDigit() || marker.dropLast(1) == "1")
	}
	return isTableStart(lines, index)
}

private class ItemLines(val marker: String, val lines: MutableList<String>)

private fun parseList(lines: List<String>, start: Int, blocks: MutableList<Block>): Int {
	val first = listItemRegex.matchEntire(lines[start])!!
	val bullet = !first.groupValues[2][0].isDigit()
	val delimiter = first.groupValues[2].last()
	val items = ArrayList<ItemLines>()
	var loose = false
	var index = start
	var contentIndent = 0
	var sawBlank = false

	while (index < lines.size) {
		val line = lines[index]
		val item = listItemRegex.matchEntire(line)
		val itemIndent = item?.groupValues?.get(1)?.length ?: -1
		// Items indented as far as the content of the previous one are nested in it instead.
		if (item != null && (items.isEmpty() || itemIndent < contentIndent)) {
			val marker = item.groupValues[2]
			val sameKind = !marker[0].isDigit() == bullet && marker.last() == delimiter
			if (!sameKind) break
			if (sawBlank && items.isNotEmpty()) loose = true
			sawBlank = false
			val spaces = item.groupValues[3].length
			val content = item.groupValues[4]
			// With more than four spaces, the content is indented code, which counts as one space.
			val padding = if (spaces in 1..4) spaces else 1
			contentIndent = itemIndent + marker.length + padding
			val firstLine = if (spaces > 4) " ".repeat(spaces - 1) + content else content
			items += ItemLines(marker, mutableListOf(firstLine))
			index++
			continue
		}
		if (line.isBlank()) {
			sawBlank = true
			items.last().lines += ""
			index++
			continue
		}
		val indent = line.length - line.trimStart(' ').length
		if (indent >= contentIndent) {
			if (sawBlank) {
				// A blank line inside an item makes the list loose.
				loose = true
			}
			sawBlank = false
			items.last().lines += line.substring(contentIndent)
			index++
			continue
		}
		val lastLine = items.last().lines.lastOrNull()
		if (!sawBlank && lastLine != null && lastLine.isNotBlank() && !startsBlock(lines, index)) {
			// A lazy continuation of the item's paragraph.
			items.last().lines += line.trim()
			index++
			continue
		}
		break
	}

	val listItems = items.map { item ->
		while (item.lines.lastOrNull()?.isBlank() == true) item.lines.removeAt(item.lines.lastIndex)
		var checked: Boolean? = null
		val firstLine = item.lines.firstOrNull().orEmpty()
		if (firstLine.length >= 3 && firstLine[0] == '[' && firstLine[2] == ']' && firstLine[1] in " xX" &&
			(firstLine.length == 3 || firstLine[3] == ' ')
		) {
			checked = firstLine[1] != ' '
			item.lines[0] = firstLine.substring(3).trimStart()
		}
		ListItem(parseBlocks(item.lines), checked)
	}
	val startNumber = if (bullet) null else first.groupValues[2].dropLast(1).toInt()
	blocks += ListBlock(startNumber, listItems, loose)
	return index
}

private fun String.tableCells(): List<String> {
	var row = trim()
	if (row.startsWith('|')) row = row.substring(1)
	if (row.endsWith('|') && !row.endsWith("\\|")) row = row.dropLast(1)
	val cells = ArrayList<String>()
	val cell = StringBuilder()
	var inCode = false
	var index = 0
	while (index < row.length) {
		val char = row[index]
		when {
			char == '\\' && index + 1 < row.length && row[index + 1] == '|' -> {
				cell.append('|')
				index++
			}

			char == '`' -> {
				inCode = !inCode
				cell.append(char)
			}

			char == '|' && !inCode -> {
				cells += cell.toString().trim()
				cell.clear()
			}

			else -> cell.append(char)
		}
		index++
	}
	cells += cell.toString().trim()
	return cells
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
	if (index + 1 >= lines.size) return false
	val header = lines[index]
	val delimiter = lines[index + 1]
	if ('|' !in header || !tableDelimiterRegex.matches(delimiter)) return false
	// A delimiter row without pipes is a thematic break or setext underline.
	if ('|' !in delimiter && header.trim().let { !it.startsWith('|') }) return false
	return header.tableCells().size == delimiter.tableCells().size
}

private fun parseTable(lines: List<String>, start: Int, blocks: MutableList<Block>): Int {
	val header = lines[start].tableCells()
	val alignments = lines[start + 1].tableCells().map { cell ->
		val left = cell.startsWith(':')
		val right = cell.endsWith(':')
		when {
			left && right -> ColumnAlignment.Center
			right -> ColumnAlignment.End
			else -> ColumnAlignment.Start
		}
	}
	val rows = ArrayList<List<String>>()
	var index = start + 2
	while (index < lines.size) {
		val line = lines[index]
		if (line.isBlank() || ('|' !in line && startsBlock(lines, index))) break
		val cells = line.tableCells()
		// Rows have exactly as many cells as the header, padded or cut off.
		rows += List(header.size) { cells.getOrElse(it) { "" } }
		index++
	}
	blocks += Table(header, alignments, rows)
	return index
}
