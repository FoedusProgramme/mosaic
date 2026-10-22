# Mosaic Markdown

Renders Markdown in Mosaic, including Markdown which is still streaming in from a language model.

```kotlin
Markdown(response)
```

Supported: ATX headings, paragraphs, emphasis, strong emphasis, strikethrough, code spans, links
(as OSC 8 hyperlinks), autolinks and bare URLs, images (as their description), bullet, ordered, and
task lists, quotes, thematic breaks, fenced code with syntax highlighting, diffs, and GitHub
Flavored Markdown tables. Raw HTML, link reference definitions, setext headings, and indented code
are shown as text.

Incomplete input renders as far as it goes: unclosed code fences run to the end, unmatched
delimiters show as they are, and a table appears once its delimiter row arrives. Every change parses
the text again, which is fast, and only the blocks which changed are composed again.

Highlighting is a tokenizer for comments, strings, numbers, keywords, annotations, and type and
function names, with keywords for Kotlin, Java, JavaScript and TypeScript, Python, Go, Rust, C and
C++, Swift, shells, SQL, and data formats like JSON and YAML. Pass a `MarkdownTheme` to change the
styles.
