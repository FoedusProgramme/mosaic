# Example: catalog

An interactive tour of everything Mosaic can render, plus recipes for common components Mosaic
does not ship yet (borders, progress bars, spinners, tabs, tables, scrolling lists, text fields).

```
./gradlew :samples:catalog:installJvmDist
./samples/catalog/build/install/catalog-jvm/bin/catalog
```

Use ↑/↓ to move between pages and q or Esc to quit. Pages list their own keys in the footer.
The terminal must be at least 80×24.

| Section   | Pages                                                         |
|-----------|---------------------------------------------------------------|
| Text      | Text styles, Colors, Rich text                                |
| Layout    | Row & Column, Box & Alignment, Modifiers, Size & Weight, Intrinsics, Custom layout |
| Drawing   | Spacer & Filler, DrawScope                                    |
| Input     | Keyboard, Text input                                          |
| Animation | Animation                                                     |
| Terminal  | Terminal state, Static output                                 |
| Recipes   | Panels, Progress, Tabs, Table, Scrolling list                 |
| Gaps      | Limitations                                                   |

The recipes live in [`components.kt`](src/commonMain/kotlin/example/components.kt) and can be
copied into an app.

Keep in mind while reading the code:

 * Mosaic does not clip, and drawing outside of the terminal crashes. Every page is sized to fit
   the 52×18 content area of a minimum-size terminal.
 * `Text` ignores the constraints it is measured with, so `Modifier.width` or `fillMaxWidth` on a
   `Text` has no effect. Wrap the `Text` in a `Box` and size the `Box` instead.
 * Typing a non-ASCII character (for example through an IME) currently crashes Mosaic, see
   [#972](https://github.com/JakeWharton/mosaic/issues/972).
