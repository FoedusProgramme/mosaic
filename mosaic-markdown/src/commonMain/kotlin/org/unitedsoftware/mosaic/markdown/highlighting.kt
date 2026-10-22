package org.unitedsoftware.mosaic.markdown

import org.unitedsoftware.mosaic.text.AnnotatedString
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.text.buildAnnotatedString

/**
 * What a language looks like to the highlighter. It is a tokenizer rather than a parser, so it
 * knows about comments, strings, numbers, keywords, and the shape of type and function names.
 */
private class Language(
	keywords: String,
	val lineComments: List<String> = listOf("//"),
	val blockComment: Pair<String, String>? = "/*" to "*/",
	val quotes: String = "\"'",
	val tripleQuotes: Boolean = false,
	/** Whether `@name` is an annotation or decorator. */
	val annotations: Boolean = false,
	/** Whether `$name` is a variable, like in shells. */
	val dollarVariables: Boolean = false,
	val capitalizedTypes: Boolean = true,
) {
	val keywords = keywords.split(' ').filter { it.isNotEmpty() }.toSet()
}

private val kotlin = Language(
	"abstract actual annotation as break by catch class companion const constructor continue crossinline data do else enum expect external false final finally for fun get if import in infix init inline inner interface internal is lateinit noinline null object open operator out override package private protected public reified return sealed set super suspend this throw true try typealias val value var vararg when where while",
	tripleQuotes = true,
	annotations = true,
)
private val java = Language(
	"abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new null package permits private protected public record return sealed short static strictfp super switch synchronized this throw throws transient true false try var void volatile while yield",
	annotations = true,
)
private val javascript = Language(
	"abstract any as async await boolean break case catch class const constructor continue debugger declare default delete do else enum export extends false finally for from function get if implements import in instanceof interface keyof let new null number of package private protected public readonly return set static string super switch this throw true try type typeof undefined var void while with yield",
	quotes = "\"'`",
	annotations = true,
)
private val python = Language(
	"and as assert async await break case class continue def del elif else except False finally for from global if import in is lambda match None nonlocal not or pass raise return self True try while with yield",
	lineComments = listOf("#"),
	blockComment = null,
	tripleQuotes = true,
	annotations = true,
)
private val go = Language(
	"break case chan const continue default defer else fallthrough false for func go goto if import interface iota map nil package range return select struct switch true type var",
	quotes = "\"'`",
)
private val rust = Language(
	"as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while",
	quotes = "\"",
)
private val c = Language(
	"auto bool break case char class const constexpr continue default delete do double else enum explicit extern false float for friend goto if inline int long namespace new noexcept nullptr operator private protected public register return short signed sizeof static struct switch template this throw true try typedef typename union unsigned using virtual void volatile while #include #define #ifdef #ifndef #endif #pragma",
)
private val swift = Language(
	"actor as associatedtype async await break case catch class continue default defer deinit do else enum extension fallthrough false fileprivate for func guard if import in init inout internal is let nil open operator private protocol public repeat rethrows return self Self static struct subscript super switch throw throws true try typealias var where while",
	quotes = "\"",
	tripleQuotes = true,
	annotations = true,
)
private val shell = Language(
	"case do done echo elif else esac exit export fi for function if in local return set shift then unset until while",
	lineComments = listOf("#"),
	blockComment = null,
	dollarVariables = true,
	capitalizedTypes = false,
)
private val sql = Language(
	"add all alter and as asc between by case create delete desc distinct drop else end exists from group having in index inner insert into is join key left like limit not null on or order outer primary references right select set table then union update values view when where with",
	lineComments = listOf("--"),
	capitalizedTypes = false,
)
private val data = Language(
	"true false null yes no on off",
	lineComments = listOf("#"),
	blockComment = null,
	capitalizedTypes = false,
)
private val plain = Language("", lineComments = emptyList(), blockComment = null, capitalizedTypes = false)

private fun languageOf(name: String): Language? = when (name.lowercase()) {
	"kotlin", "kt", "kts" -> kotlin
	"java", "groovy", "gradle", "scala", "dart", "csharp", "cs", "c#" -> java
	"javascript", "js", "jsx", "mjs", "cjs", "typescript", "ts", "tsx" -> javascript
	"python", "py" -> python
	"go", "golang" -> go
	"rust", "rs" -> rust
	"c", "h", "cpp", "c++", "cc", "hpp", "cxx", "objc", "objective-c", "zig" -> c
	"swift" -> swift
	"sh", "bash", "zsh", "shell", "fish", "console", "shellscript" -> shell
	"sql" -> sql
	"json", "jsonc", "yaml", "yml", "toml", "ini", "properties" -> data
	"", "text", "txt", "plaintext" -> plain
	else -> null
}

/** Whether [language] names a diff, which is colored by line rather than by token. */
internal fun isDiff(language: String) = language.lowercase() in setOf("diff", "patch", "udiff")

/** Colors [code] for [language], or for the common parts of most languages if it is unknown. */
internal fun highlight(code: String, language: String, theme: SyntaxTheme): AnnotatedString {
	if (isDiff(language)) return highlightDiff(code, theme)
	val profile = languageOf(language) ?: Language("", quotes = "\"'")
	return buildAnnotatedString { Tokenizer(code, profile, theme, this).run() }
}

private fun highlightDiff(code: String, theme: SyntaxTheme): AnnotatedString = buildAnnotatedString {
	for ((index, line) in code.split('\n').withIndex()) {
		if (index > 0) append('\n')
		val style = when {
			line.startsWith("+++") || line.startsWith("---") -> theme.meta
			line.startsWith("diff ") || line.startsWith("index ") -> theme.meta
			line.startsWith("@@") -> theme.hunk
			line.startsWith('+') -> theme.inserted
			line.startsWith('-') -> theme.deleted
			else -> null
		}
		if (style != null) {
			val start = length
			append(line)
			addStyle(style, start, length)
		} else {
			append(line)
		}
	}
}

private class Tokenizer(
	private val code: String,
	private val language: Language,
	private val theme: SyntaxTheme,
	private val out: AnnotatedString.Builder,
) {
	private var index = 0

	private fun emit(end: Int, style: SpanStyle?) {
		val start = out.length
		out.append(code, index, end)
		if (style != null && style != theme.plain) out.addStyle(style, start, out.length)
		index = end
	}

	fun run() {
		while (index < code.length) {
			val char = code[index]
			val lineComment = language.lineComments.firstOrNull { code.startsWith(it, index) }
			val blockComment = language.blockComment
			when {
				lineComment != null && (lineComment != "#" || index == 0 || code[index - 1].isWhitespace()) -> {
					val end = code.indexOf('\n', index).let { if (it == -1) code.length else it }
					emit(end, theme.comment)
				}

				blockComment != null && code.startsWith(blockComment.first, index) -> {
					val close = code.indexOf(blockComment.second, index + blockComment.first.length)
					emit(if (close == -1) code.length else close + blockComment.second.length, theme.comment)
				}

				char in language.quotes -> string(char)

				char.isDigit() -> {
					var end = index + 1
					while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '.' || code[end] == '_')) end++
					emit(end, theme.number)
				}

				char == '@' && language.annotations && index + 1 < code.length && code[index + 1].isLetter() -> {
					emit(identifierEnd(index + 1), theme.annotation)
				}

				char == '$' && language.dollarVariables && index + 1 < code.length &&
					(code[index + 1].isLetter() || code[index + 1] == '{' || code[index + 1] == '_') -> {
					val end = if (code[index + 1] == '{') {
						code.indexOf('}', index).let { if (it == -1) code.length else it + 1 }
					} else {
						identifierEnd(index + 1)
					}
					emit(end, theme.keyword)
				}

				char.isLetter() || char == '_' || (char == '#' && language === c) -> word()

				else -> emit(index + 1, null)
			}
		}
	}

	private fun identifierEnd(start: Int): Int {
		var end = start
		while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) end++
		return end
	}

	private fun word() {
		val end = identifierEnd(index + 1)
		val word = code.substring(index, end)
		var next = end
		while (next < code.length && code[next] == ' ') next++
		val style = when {
			word in language.keywords || (language === sql && word.lowercase() in language.keywords) -> theme.keyword
			next < code.length && code[next] == '(' -> theme.function
			language.capitalizedTypes && word[0].isUpperCase() -> theme.type
			else -> null
		}
		emit(end, style)
	}

	private fun string(quote: Char) {
		if (language.tripleQuotes && code.startsWith("$quote$quote$quote", index)) {
			val close = code.indexOf("$quote$quote$quote", index + 3)
			emit(if (close == -1) code.length else close + 3, theme.string)
			return
		}
		var end = index + 1
		while (end < code.length) {
			val char = code[end]
			if (char == '\\') {
				end += 2
				continue
			}
			end++
			if (char == quote) break
			// Only template strings span lines.
			if (char == '\n' && quote != '`') break
		}
		emit(end.coerceAtMost(code.length), theme.string)
	}
}
