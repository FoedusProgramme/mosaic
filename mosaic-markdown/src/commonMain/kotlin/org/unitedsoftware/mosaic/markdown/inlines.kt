package org.unitedsoftware.mosaic.markdown

import org.unitedsoftware.mosaic.text.AnnotatedString
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.text.buildAnnotatedString
import org.unitedsoftware.mosaic.ui.TextStyle
import org.unitedsoftware.mosaic.ui.isUnspecifiedTextStyle

/**
 * Parses the inline content of a paragraph or heading: code spans, emphasis, strong emphasis,
 * strikethrough, links, images (shown as their description), autolinks, escapes, and line
 * breaks. Soft line breaks become spaces like in CommonMark, while hard ones, ending in two
 * spaces or a backslash, are kept.
 *
 * Delimiters without a match, like the opening `**` of strong emphasis which is still being
 * streamed in, are shown as they are.
 */
internal fun parseInlines(text: String, theme: MarkdownTheme, base: SpanStyle? = null): AnnotatedString {
	return buildAnnotatedString {
		val parser = InlineParser(text, theme, this)
		if (base != null) {
			parser.styled(base) { parser.parse(0, text.length) }
		} else {
			parser.parse(0, text.length)
		}
	}
}

private const val Punctuation = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

private class InlineParser(
	private val text: String,
	private val theme: MarkdownTheme,
	private val out: AnnotatedString.Builder,
) {
	/** The text style of the enclosing spans, which nested ones add to rather than replace. */
	private var textStyle = TextStyle.Unspecified

	inline fun styled(style: SpanStyle, block: () -> Unit) {
		val outer = textStyle
		val combined = when {
			style.textStyle.isUnspecifiedTextStyle -> outer
			outer.isUnspecifiedTextStyle -> style.textStyle
			else -> outer + style.textStyle
		}
		textStyle = combined
		val index = out.pushStyle(style.copy(textStyle = combined))
		try {
			block()
		} finally {
			out.pop(index)
			textStyle = outer
		}
	}

	fun parse(start: Int, end: Int) {
		var index = start
		while (index < end) {
			val char = text[index]
			index = when {
				char == '\\' -> escape(index, end)

				char == '`' -> codeSpan(index, end)

				char == '*' || char == '_' || char == '~' -> emphasis(index, end)

				char == '!' && index + 1 < end && text[index + 1] == '[' -> link(index + 1, end, image = true) ?: literal(index, 2)

				char == '[' -> link(index, end, image = false) ?: literal(index, 1)

				char == '<' -> autolink(index, end) ?: literal(index, 1)

				char == 'h' && text.startsWith("http", index) && isUrlStart(index) -> bareUrl(index, end)

				char == '\n' -> lineBreak(index)

				else -> {
					var next = index + 1
					while (next < end && text[next] !in SpecialChars && !(text[next] == 'h' && text.startsWith("http", next))) next++
					// Trailing spaces before a line break are not shown.
					val chunkEnd = if (next < end && text[next] == '\n') text.trimEndIndex(index, next) else next
					out.append(text.substring(index, chunkEnd))
					next
				}
			}
		}
	}

	private fun literal(index: Int, length: Int): Int {
		out.append(text.substring(index, index + length))
		return index + length
	}

	private fun escape(index: Int, end: Int): Int {
		if (index + 1 < end) {
			val next = text[index + 1]
			if (next == '\n') {
				out.append('\n')
				return index + 2
			}
			if (next in Punctuation) {
				out.append(next)
				return index + 2
			}
		}
		return literal(index, 1)
	}

	private fun lineBreak(index: Int): Int {
		val hard = index >= 2 && text[index - 1] == ' ' && text[index - 2] == ' '
		out.append(if (hard) '\n' else ' ')
		return index + 1
	}

	private fun String.trimEndIndex(start: Int, end: Int): Int {
		var trimmed = end
		while (trimmed > start && this[trimmed - 1] == ' ') trimmed--
		return trimmed
	}

	private fun runLength(index: Int, end: Int, char: Char): Int {
		var length = 0
		while (index + length < end && text[index + length] == char) length++
		return length
	}

	private fun codeSpan(index: Int, end: Int): Int {
		val length = runLength(index, end, '`')
		var search = index + length
		while (search < end) {
			val close = text.indexOf('`', search)
			if (close == -1 || close >= end) break
			val closeLength = runLength(close, end, '`')
			if (closeLength == length) {
				var code = text.substring(index + length, close).replace('\n', ' ')
				if (code.length >= 2 && code.startsWith(' ') && code.endsWith(' ') && code.isNotBlank()) {
					code = code.substring(1, code.length - 1)
				}
				styled(theme.code) { out.append(code) }
				return close + closeLength
			}
			search = close + closeLength
		}
		return literal(index, length)
	}

	/** Returns the index of the end of the code span starting at [index], or null. */
	private fun codeSpanEnd(index: Int, end: Int): Int? {
		val length = runLength(index, end, '`')
		var search = index + length
		while (search < end) {
			val close = text.indexOf('`', search)
			if (close == -1 || close >= end) return null
			val closeLength = runLength(close, end, '`')
			if (closeLength == length) return close + closeLength
			search = close + closeLength
		}
		return null
	}

	private fun isWhitespace(index: Int) = index < 0 || index >= text.length || text[index].isWhitespace()

	private fun isPunctuation(index: Int) = index in text.indices && text[index] in Punctuation

	private fun canOpen(index: Int, length: Int, char: Char): Boolean {
		val after = index + length
		if (isWhitespace(after)) return false
		// Left-flanking: not followed by punctuation unless preceded by whitespace or punctuation.
		val leftFlanking = !isPunctuation(after) || isWhitespace(index - 1) || isPunctuation(index - 1)
		if (!leftFlanking) return false
		// Underscores inside words, like in snake_case, are not emphasis.
		return char != '_' || isWhitespace(index - 1) || isPunctuation(index - 1)
	}

	private fun canClose(index: Int, length: Int, char: Char): Boolean {
		if (isWhitespace(index - 1)) return false
		val rightFlanking = !isPunctuation(index - 1) || isWhitespace(index + length) || isPunctuation(index + length)
		if (!rightFlanking) return false
		return char != '_' || isWhitespace(index + length) || isPunctuation(index + length)
	}

	private fun emphasis(index: Int, end: Int): Int {
		val char = text[index]
		val run = runLength(index, end, char)
		if (char == '~') {
			// Only ~~ is strikethrough, like on GitHub.
			if (run != 2 || !canOpen(index, 2, char)) return literal(index, run)
			val close = findCloser(index + 2, end, char, 2) ?: return literal(index, run)
			styled(theme.strikethrough) { parse(index + 2, close) }
			return close + 2
		}
		if (!canOpen(index, run, char)) return literal(index, run)

		// Try the longest emphasis first: *** is strong emphasis inside emphasis.
		for (length in minOf(run, 3) downTo 1) {
			val close = findCloser(index + run, end, char, length) ?: continue
			if (run > length) out.append(text.substring(index, index + run - length))
			when (length) {
				1 -> styled(theme.emphasis) { parse(index + run, close) }
				2 -> styled(theme.strong) { parse(index + run, close) }
				else -> styled(theme.strong) { styled(theme.emphasis) { parse(index + run, close) } }
			}
			return close + length
		}
		return literal(index, run)
	}

	/**
	 * Results of [findCloser], since skipping nested emphasis searches the same ranges again.
	 * Without it, many unclosed delimiters would take exponential time.
	 */
	private val closers = HashMap<CloserKey, Int>()

	private data class CloserKey(val start: Int, val end: Int, val char: Char, val length: Int)

	/** Finds a run of exactly [length] [char]s which can close emphasis, skipping code and links. */
	private fun findCloser(start: Int, end: Int, char: Char, length: Int): Int? {
		val key = CloserKey(start, end, char, length)
		closers[key]?.let { return if (it == -1) null else it }
		val result = searchCloser(start, end, char, length)
		closers[key] = result ?: -1
		return result
	}

	private fun searchCloser(start: Int, end: Int, char: Char, length: Int): Int? {
		var index = start
		while (index < end) {
			when (text[index]) {
				'\\' -> {
					index += 2
					continue
				}

				'`' -> {
					val codeEnd = codeSpanEnd(index, end)
					if (codeEnd != null) {
						index = codeEnd
						continue
					}
				}

				'[' -> {
					val linkEnd = linkEnd(index, end)
					if (linkEnd != null) {
						index = linkEnd
						continue
					}
				}

				char -> {
					val run = runLength(index, end, char)
					if (index > start && run == length && canClose(index, run, char)) return index
					// Skip emphasis nested inside, like the strong part of "*a **b** c*".
					if (canOpen(index, run, char)) {
						val nested = (minOf(run, 3) downTo 1).firstNotNullOfOrNull { nestedLength ->
							findCloser(index + run, end, char, nestedLength)?.let { it + nestedLength }
						}
						if (nested != null) {
							index = nested
							continue
						}
					}
					// A longer run closes with its start, leaving the rest for enclosing emphasis,
					// like the "***" of "**a *b***".
					if (index > start && run > length && canClose(index, run, char)) return index
					index += run
					continue
				}
			}
			index++
		}
		return null
	}

	/** Returns the end of the `]` matching the `[` at [index], or null. */
	private fun bracketEnd(index: Int, end: Int): Int? {
		var depth = 0
		var search = index
		while (search < end) {
			when (text[search]) {
				'\\' -> search++

				'`' -> codeSpanEnd(search, end)?.let { search = it - 1 }

				'[' -> depth++

				']' -> {
					depth--
					if (depth == 0) return search
				}
			}
			search++
		}
		return null
	}

	private class LinkParts(val textEnd: Int, val url: String, val end: Int)

	private fun linkParts(index: Int, end: Int): LinkParts? {
		val close = bracketEnd(index, end) ?: return null
		if (close + 1 >= end || text[close + 1] != '(') return null
		var search = close + 2
		while (search < end && text[search] == ' ') search++
		val urlStart: Int
		val urlEnd: Int
		if (search < end && text[search] == '<') {
			urlStart = search + 1
			urlEnd = text.indexOf('>', urlStart).takeIf { it in urlStart until end } ?: return null
			search = urlEnd + 1
		} else {
			urlStart = search
			var depth = 0
			while (search < end && !text[search].isWhitespace()) {
				val c = text[search]
				if (c == '(') depth++
				if (c == ')') {
					if (depth == 0) break
					depth--
				}
				search++
			}
			urlEnd = search
		}
		// An optional title, which a terminal has no place for.
		while (search < end && text[search] == ' ') search++
		if (search < end && text[search] in "\"'(") {
			val closing = if (text[search] == '(') ')' else text[search]
			search = text.indexOf(closing, search + 1).takeIf { it in 0 until end } ?: return null
			search++
			while (search < end && text[search] == ' ') search++
		}
		if (search >= end || text[search] != ')') return null
		return LinkParts(close, text.substring(urlStart, urlEnd), search + 1)
	}

	private fun linkEnd(index: Int, end: Int): Int? = linkParts(index, end)?.end

	private fun link(index: Int, end: Int, image: Boolean): Int? {
		val parts = linkParts(index, end) ?: return null
		styled(theme.link.merge(SpanStyle(link = parts.url))) {
			if (image) {
				// Terminals cannot show the image itself here, so its description stands in.
				out.append("🖼 ")
			}
			parse(index + 1, parts.textEnd)
		}
		return parts.end
	}

	private fun autolink(index: Int, end: Int): Int? {
		val close = text.indexOf('>', index + 1)
		if (close == -1 || close >= end) return null
		val target = text.substring(index + 1, close)
		if (target.any { it.isWhitespace() || it == '<' }) return null
		val url = when {
			Regex("^[A-Za-z][A-Za-z0-9+.-]{1,31}:.*").matches(target) -> target
			Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(target) -> "mailto:$target"
			else -> return null
		}
		styled(theme.link.merge(SpanStyle(link = url))) { out.append(target) }
		return close + 1
	}

	private fun isUrlStart(index: Int): Boolean {
		if (!text.startsWith("http://", index) && !text.startsWith("https://", index)) return false
		return index == 0 || text[index - 1].isWhitespace() || text[index - 1] in "(*_~"
	}

	private fun bareUrl(index: Int, end: Int): Int {
		var urlEnd = index
		while (urlEnd < end && !text[urlEnd].isWhitespace() && text[urlEnd] != '<') urlEnd++
		// Trailing punctuation belongs to the sentence, and a closing parenthesis only if unbalanced.
		while (urlEnd > index && text[urlEnd - 1] in "?!.,:*_~'\"") urlEnd--
		if (urlEnd > index && text[urlEnd - 1] == ')' &&
			text.substring(index, urlEnd).count { it == ')' } > text.substring(index, urlEnd).count { it == '(' }
		) {
			urlEnd--
		}
		val url = text.substring(index, urlEnd)
		styled(theme.link.merge(SpanStyle(link = url))) { out.append(url) }
		return urlEnd
	}

	private companion object {
		const val SpecialChars = "\\`*_~![<\n"
	}
}
