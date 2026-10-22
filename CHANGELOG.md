# Change log

## [Unreleased]
[Unreleased]: https://github.com/FoedusProgramme/mosaic/compare/0.19.0...HEAD

New:
- `LocalTerminalControl` hands the terminal over to other programs with `release`, like an editor or an interactive command, and takes it over again afterwards, drawing everything anew. Meanwhile, nothing is drawn, input is left to those programs, and stdout and stderr are no longer intercepted.
- Ctrl+Z suspends the program when no key handler consumes it, like it does in a shell, and `fg` resumes it. `TerminalControl.suspendProcess` does the same. Neither is supported on Windows.
- `Terminal.released`, `Terminal.suspendProcess`, and `Tty.suspendProcess` are what these are built on.


## [0.19.0] - 2026-10-22
[0.19.0]: https://github.com/FoedusProgramme/mosaic/releases/tag/0.19.0

The first release of the fork, under the `org.unitedsoftware.mosaic` group and packages.

New:
- JVM support for RISC-V and s390x!
- Keys with modifiers which used to be indistinguishable, such as Shift+Enter and Enter, are now reported separately in terminals which support the Kitty keyboard protocol.
- `Modifier.onPasteEvent` receives pasted text as a single `PasteEvent` in terminals which support bracketed paste, rather than one key event per character with `Enter` for each line break. Unhandled pastes are still delivered as key events.
- `TestMosaic.sendEvent` sends any terminal event, such as the `BracketedPasteEvent`s around pasted text.
- `DrawScope.drawCursor` places and shows the terminal's cursor once the frame is rendered, so that input methods show their composition at the caret of text input.
- `TextField` and `TextFieldState` for editing text. Text wraps to the field's width, `maxLines` limits its height, and the terminal's cursor marks the caret. With `onSubmit`, Enter submits and Shift+Enter or Alt+Enter break lines. Pastes are inserted as a whole, and Ctrl+A, Ctrl+E, Ctrl+U, Ctrl+K, and Ctrl+W work like in a shell.
- `runMosaic`, `runMosaicBlocking`, and `runMosaicMain` take `alternateScreen`, which renders full screen on the terminal's alternate screen and restores what it showed before when done. Static output is written once done.
- `WindowTitle` sets the title of the terminal window while it is in the composition, restoring the previous title afterwards in terminals which support it.
- `LocalClipboard` copies text to the clipboard with OSC 52, and `LocalNotifier` shows desktop notifications with the Kitty notification protocol or OSC 9.
- `SpanStyle.link` and `withLink` link text to a URL with OSC 8 hyperlinks.
- `Modifier.clipToBounds` keeps the content of a node, including its children, within its bounds.
- `Modifier.verticalScroll` and `Modifier.horizontalScroll` with `ScrollState` show part of content which is larger than the node. `ScrollState.scrollToEnd` keeps following the end as the content grows.
- `LazyColumn` with `LazyListState` only composes the items which are visible, so it can hold long lists like the messages of a conversation. Keyed items keep their position when items are added before them, and `scrollToEnd` scrolls to the latest item.
- A focus system: `Modifier.focusable`, `Modifier.focusRequester`, `Modifier.onFocusChanged`, and `LocalFocusManager`. Key and paste events go to the focused node and its ancestors first, and to the other nodes only when those do not handle them. Tab and Shift+Tab move the focus, and the first focusable node is focused when nothing else is.
- `Popup` shows content on top of everything else, like a completion menu, without taking up space in the layout or being clipped by its ancestors. Its `alignment` lets it extend up from its position, for example above a text field at the bottom of the screen.
- Writes to stdout and stderr by other means than Mosaic, like `println` or a logging library, are intercepted while `runMosaic` runs and shown as static output above the display, rather than corrupting it (#681).
- `Text` takes `softWrap`, `maxLines`, and `overflow`. `TextOverflow.Ellipsis` ends text which does not fit with an ellipsis.
- `Image` shows an `ImageBitmap`, scaled to fit. kitty and Ghostty show the image itself with the Kitty graphics protocol, using Unicode placeholders so that it moves with the text. iTerm2 and WezTerm use their inline images, which keep transparency, terminals which report Sixel support Sixel graphics, and others half block characters.
- `Terminal.Capabilities.sixelGraphics` is true when the terminal reports Sixel support in its primary device attributes.
- `WindowPointerShape` sets the shape of the mouse pointer with the Kitty pointer shape protocol while it is in the composition, restoring the previous shape afterwards.
- Mouse input: `Modifier.onPointerEvent`, `onClick`, and `onHover`. Events go to the innermost node under the pointer first, then to its ancestors, and drags and releases go to the nodes the press went to. Clicks focus focusable nodes and place the cursor of `TextField`s, and the wheel scrolls `verticalScroll`, `horizontalScroll` (with Shift), and `LazyColumn`. `runMosaic` takes `mouse`, which is enabled on the alternate screen by default. Inline, the output is located on the screen with cursor position requests.
- `Modifier.border` draws a box of a `BorderStyle` around the content, with an optional title.
- New `mosaic-markdown` library with `Markdown`, which renders Markdown with syntax highlighted code, diffs, tables, task lists, and OSC 8 links. It handles incomplete input, so responses of language models can be rendered while they stream in.

Changed:
- `TextField` is focusable and edits while focused, instead of taking a `focused` parameter.
- `Text` respects the incoming constraints. Lines wider than the maximum width wrap, after the last space which fits where possible, and text is clipped to the bounds of its node. Previously text was always as large as its content and drew over its surroundings.
- Only rows which changed since the last frame are written to the terminal again.
- Output taller than the terminal shows its bottom instead of corrupting the screen, since the cursor cannot move above the top of the screen to redraw it.
- Content is measured with the width of the terminal as its maximum width. With `alternateScreen`, it is measured to exactly the size of the terminal.
- Use the AndroidX versions of the Compose runtime and lifecycle dependencies which are now fully multiplatform. Dependency constraints to the corresponding JetBrains artifacts (which are now empty) are present to prevent symbol duplication.
- Remove support for x86 macOS targets. These have been deprecated for a bit, but support has been removed from the Compose runtime so we have no choice but to match.

Fixed:
- Clear the set of states which were read during layout and draw passes before each pass. Previously these sets grew infinitely, which was both a memory leak and a performance problem.
- Close the underlying TTY at the end of the `runMosaic*` family of functions.
- Frame times on Linux and Windows no longer jump backwards once per second.
- Fix arc-based animations on native targets, which built their lookup table incorrectly.
- Report Shift+Tab as a key event when the terminal sends it as `CSI Z`.
- Typing characters outside of ASCII, such as CJK text committed by an input method, no longer crashes. They are delivered as `KeyEvent`s whose `key` is the character.
- Wide characters, such as CJK text and most emoji, are measured and drawn as two columns, so they no longer push the rest of the row out of place. Combining marks and emoji joined with zero width joiners stay in the column of the character they modify. Widths follow Unicode 18.
- Drawing outside of the surface, for example while the terminal is being resized, is clipped instead of crashing.
- The initial size comes from the TTY in terminals which support in-band resize events but do not report their size when they are enabled, like iTerm2. Previously the size stayed at 80 by 24 until the window was resized.
- Inline output can be as tall as the terminal. The cursor rests at the end of the last row rather than on a line below the output, which kept the last row of the terminal empty.
- Resizing terminals which do not report size changes in-band, such as tmux and Terminal.app, no longer crashes the JVM. The resize callback was invoked from the signal handler, on whichever thread received the signal, with the JNI environment of another thread.


## [0.18.0] - 2025-08-21
[0.18.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.18.0

New:
- JVM artifacts can now be used as modules in the Java module system.
- On Java 22 and newer, Mosaic now uses the [foreign function & memory API](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html) to call into its native library instead of JNI.

Changed:
- Unsolicited focus, theme, and resize events are now ignored unless the terminal has reported that it supports each of those modes.
  This should have no real impact on anything, except that now the `Terminal.capabilities` value can now be trusted to indicate whether `Terminal.state` will ever change in the case of focus and theme.
  For terminal size, a platform-specific fallback exists which will attempt to still correctly report the size, but asynchronously.
- Terminal theme is now always queried for an initial value regardless of whether the terminal supports sending theme changes.
- Java 11 is now the minimum-supported JVM version.
- In-development snapshots of Mosaic are now published to the Central Portal Snapshots repository at https://central.sonatype.com/repository/maven-snapshots/.

Fixed:
- Windows now reports the terminal size correctly. Previously it reported the buffer size instead of the window size.
- Windows Terminal and ConEmu are now correctly detected as supporting color.


## [0.17.0] - 2025-04-11
[0.17.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.17.0

New:
- `Terminal` and `LocalTerminal` are now renamed to `TerminalState` and `LocalTerminalState`, respectively, along with some new capabilities:
  - Add `focused` boolean which defaults to true. If the terminal supports sending focus changes, this will be updated in real time.
  - Add `theme` property which defaults to 'unknown'. If the terminal supports querying theme state, this will be 'light' or 'dark' and be updated in real time. Note: this is not the OS theme, but instead reflects the theme of the terminal color scheme.
  - Size property now features the size in pixels in addition to cells. These values will be 0 if unsupported by the terminal.
- Bind `TerminalState.focused` to a `Lifecycle` and expose into the composition as `LocalLifecycleOwner`. This allows using Compose lifecycle helpers such as `LifecycleResumeEffect` and others.
- Fancy underline styles (single, double, dashed, dotted, curved) and colors can now be specified for text and annotated string spans.
- `LocalStaticLogger` composition local provides access to `StaticLogger` which allows logging plain strings at arbitrary points for inclusion in the next frame. This can be used from effects, callback, state classes, etc. Support for logging `AnnotatedString`s will come in a future release.
- `runMosaicMain` function replaces the existing `runMosaicBlocking` for use specifically in `fun main()` or main-like scenarios.

Changed:
- Switched to our own terminal integration and parsing library. Report any issues with keyboard input, incorrect size reporting, or garbled output. This unlocks many of the features listed above, as well as many more planned in the future.
- Only disable the cursor and emit synchronized rendering markers if the terminal reports support for those features.
- `Static` function is now called `StaticEffect` to better indicate that it only renders its content once.
- `runMosaic` and `runMosaicBlocking` now accept a `NonInteractivePolicy` argument which dictates the behavior when Mosaic cannot connect directly to the TTY.

Fixed:
- Prevent final character from being erased when a row writes into the last column of the terminal.
- Do not emit ANSI style reset escape sequence when colors are disabled (such as in testing).
- Do not draw blank spaces at the end of every line.

Removed:
- `renderMosaic` was removed without replacement. As the capabilities of the library grow, supporting a string as a render target was increasingly difficult.


## [0.16.0] - 2025-02-14
[0.16.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.16.0

New:
- Add `setContentAndSnapshot` to 'mosaic-testing' which returns the initial composition snapshot. This avoids a potential problem with calling `setContent` and then `awaitSnapshot` since the latter will trigger a subsequent recomposition (if needed), preventing observation of the initial state.

Changed:
- `Static` composable no longer takes a list. Instead, it behaves like a normal composable which only renders its function once.
- Drop use of Jansi library for supporting ANSI color output on Windows.


## [0.15.0] - 2025-01-07
[0.15.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.15.0

New:
- Create `mosaic-animation` library, that provides various possibilities for animating Mosaic. An analog of [androidx.compose.animation-core](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/package-summary).
- Add `IntrinsicSize` and related `Modifier.width`/`height`/`requiredWidth`/`requiredHeight`.
- New `mosaic-testing` library for deterministically rendering Mosaic composables under test.
- Add `Mosaic.layoutId` which allows identifying the element within its parent during layout.
- Add `Modifier.composed` that allows creating custom reusable modifiers with access to Compose functionality inside.

Changed:
- Rendering now occurs as fast as possible, although still only when necessary. Previously the maximum FPS was capped to 20, which could cause minor visual delays when processing events.

Fixed:
- Fix the handling of custom `Modifier`s that have multiple parents from `LayoutModifier`, `DrawModifier`, `KeyModifier`, `ParentDataModifier`.


## [0.14.0] - 2024-10-07
[0.14.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.14.0

New:
- Support synchronized terminal update for rendering. This should eliminate tearing when rendering updates for terminals that support this feature.
- The terminal cursor is now automatically hidden during rendering and restored afterward.
- Added `Modifier.onKeyEvent` or `Modifier.onPreKeyEvent` to listen to keyboard events.
- Send real frame times into Compose which can be used for things like animations.

Changed:
- The entrypoints (`runMosaic` and `runMosaicBlocking`) have been changed to directly accept a composable lambda. Asynchronous work should now be performed inside Compose's effect system (e.g., `LaunchedEffect`), and rendering will complete when all effects have completed. Check out our samples for more information about how to do this.
- Сhange `DrawScope#drawRect` API with the ability to draw with text characters and specify `DrawStyle` (`Fill` or `Stroke`).
- Drop support for JS target. If you were using this, please file an issue so we can discuss support.


## [0.13.0] - 2024-05-28
[0.13.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.13.0

New:
- Support Kotlin 2.0.0!

Changed:
- Remove our Gradle plugin in favor of JetBrains' (see below for more).

Note: Version 0.12.0 was also released today, but it still supports Kotlin 1.9.24.
Check out [its release entry](https://github.com/JakeWharton/mosaic/releases/tag/0.12.0) for more on what's new.


### Gradle plugin removed

This version of Mosaic removes the custom Gradle plugin in favor of [the official JetBrains Compose compiler plugin](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compiler.html) which ships as part of Kotlin itself.
Each module in which you had previously applied the `com.jakewharton.mosaic` plugin should be changed to apply `org.jetbrains.kotlin.plugin.compose` instead.
The Mosaic runtime will no longer be added as a result of the plugin change, and so any module which references Mosaic APIs should apply the `com.jakewharton.mosaic:mosaic-runtime` dependency.

For posterity, the Kotlin version compatibility table and compiler version customization for our old Mosaic Gradle plugin will be archived here:

<details>
<summary>Mosaic 0.12.0 Gradle plugin Kotlin compatibility table</summary>
<p>

Since Kotlin compiler plugins are an unstable API, certain versions of Mosaic only work with
certain versions of Kotlin.

| Kotlin | Mosaic        |
|--------|---------------|
| 1.9.24 | 0.12.0        |
| 1.9.22 | 0.11.0        |
| 1.9.20 | 0.10.0        |
| 1.9.10 | 0.9.1         |
| 1.9.0  | 0.8.0 - 0.9.0 |
| 1.8.22 | 0.7.1         |
| 1.8.21 | 0.7.0         |
| 1.8.20 | 0.6.0         |
| 1.8.10 | 0.5.0         |
| 1.8.0  | 0.3.0 - 0.4.0 |
| 1.7.10 | 0.2.0         |
| 1.5.10 | 0.1.0         |

</p>
</details>

<details>
<summary>Mosaic 0.12.0 Gradle plugin Compose compiler customization instructions</summary>
<p>

Each version of Mosaic ships with a specific JetBrains Compose compiler version which works with
a single version of Kotlin (see [version table](#usage) above). Newer versions of the Compose
compiler or alternate Compose compilers can be specified using the Gradle extension.

To use a new version of the JetBrains Compose compiler version:
```kotlin
mosaic {
  kotlinCompilerPlugin.set("1.4.8")
}
```

To use an alternate Compose compiler dependency:
```kotlin
mosaic {
  kotlinCompilerPlugin.set("com.example:custom-compose-compiler:1.0.0")
}
```

</p>
</details>


## [0.12.0] - 2024-05-28
[0.12.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.12.0

New:
- Add `linuxArm64` target.
- Add `rrtop` sample.
- Support true color palette.

Changed:
- Disable klib signature clash checks for JS compilations. These occasionally occur as a result of Compose compiler behavior, and are safe to disable (the first-party JetBrains Compose Gradle plugin also disables them).
- Remove `Terminal$Size` and use `IntSize` instead in `Terminal#size` for optimization purposes.
- Remove `Color.Bright*` constants. Use `Color` function to create the desired color.
- Replace nullable `Color` and `TextStyle` with `Color.Unspecified` and `TextStyle.Unspecified` respectively. Also make `TextStyle` an inline class.

Fixed:
- Use CRLF line endings to fix rendering when a terminal is in raw mode.


## [0.11.0] - 2024-02-27
[0.11.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.11.0

New:
- Support Kotlin 1.9.22 via JetBrains Compose compiler 1.5.10.
- `Filler` composable is like a `Spacer` but fills its area with a character instead of a space.
- `Box` without content provides the ability to render using drawing modifiers without needing an empty chidlren lambda.
- `Modifier.aspectRatio` attempts to constrain a composable to an aspect ratio in either the vertical or horizontal direction.
- `Modifier.offset` offsets the composable in its parent by the given coordinates.
- `Modifier.fillMaxWidth`, `Modifier.fillMaxHeight`, `Modifier.fillMaxSize`, `Modifier.wrapContentWidth`, `Modifier.wrapContentHeight`, `Modifier.wrapContentSize`, and `Modifier.defaultMinSize` help size composable measurement in relation to their parent.
- `Modifier.weight` allows sizing a composable proportionally to others within the same parent.
- `Row` and `Column` each feature an arrangement parameter which controls the placement of children on the main axis of the container.

Changed:
- `Modifier` parameter is now universally called `modifier` in the API.
- Disable decoy generation for JS target to make compatible with JetBrains Compose 1.6. This is an ABI-breaking change, so all Compose-based libraries targeting JS will also need to have been recompiled.

Fix:
- Ensure ANSI control sequences are written properly to Windows terminals.
- Robot sample now correctly moves on Windows.

This version works with Kotlin 1.9.22 by default.


## [0.10.0] - 2023-11-13
[0.10.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.10.0

New:
- Support Kotlin 1.9.20 via JetBrains Compose compiler 1.5.3.

- `@MosaicComposable` annotation restricts composable function usage to those meant for Mosaic
  (e.g., our `Text`) or general-purpose (e.g., Compose's `remember`). In most cases the Compose
  compiler can infer this automatically, but it's available for explicit use if needed.

- `LocalTerminal` composition local provides the size of the terminal if it can be detected.
  If the size changes, your function will automatically be recomposed with the new values.

    ```kotlin
    val size = LocalTerminal.current.size
    Text("Terminal(w=${size.width}, h=${size.height})")
    ```

- `Row`, `Column`, and `Box` now support horizontal and vertical alignment of their children.

    ```kotlin
    Column {
      Text("This is very long")
      Text(
        "On the right",
        modifier = Modifier.align(End),
      )
    }
    ```

- Add `AnnotatedString` with `SpanStyle` for string customization. Instead of writing a series of
  `Text` functions in a `Row`, emit a single `Text` with formatting changes within the string.

    ```kotlin
    Text(buildAnnotatedString {
      append("Plain! ")
      withStyle(SpanStyle(color = BrightGreen)) {
        append("Green! ")
      }
      withStyle(SpanStyle(color = BrightBlue)) {
        append("Blue!")
      }
    })
    ```

- `Spacer` node for occupying space within layouts.

- Constraints and intrinsics are now available in the layout system.

This version works with Kotlin 1.9.20 by default.


## [0.9.1] - 2023-09-14
[0.9.1]: https://github.com/JakeWharton/mosaic/releases/tag/0.9.1

New:
- Support Kotlin 1.9.10 via JetBrains Compose compiler 1.5.2.

This version works with Kotlin 1.9.10 by default.


## [0.9.0] - 2023-08-09
[0.9.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.9.0

New:
- Support for specifying custom Compose compiler versions. This will allow you to use the latest
  version of Molecule with newer versions of Kotlin than it explicitly supports.

  See [the README](https://github.com/JakeWharton/mosaic/#custom-compose-compiler) for more information.


## [0.8.0] - 2023-07-20
[0.8.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.8.0

New:
- Support Kotlin 1.9.0 via JetBrains Compose compiler 1.5.0.


## [0.7.1] - 2023-06-30
[0.7.1]: https://github.com/JakeWharton/mosaic/releases/tag/0.7.1

New:
- Support Kotlin 1.8.22 via JetBrains Compose compiler 1.4.8.


## [0.7.0] - 2023-06-26
[0.7.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.7.0

New:
- Support Kotlin 1.8.21 via JetBrains Compose compiler 1.4.7.
- Add support for modifiers on layouts and built-in components.
  There are two types which are built-in: `DrawModifier` and `LayoutModifier`.
  The built-in functions are `drawBehind`, `background`, `layout`, and `padding`.
- Add Box component.

Changed:
- Frames are now emitted slightly differently relying on Compose for signaling when a change has happened. Please report any problems.


## [0.6.0] - 2023-04-17
[0.6.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.6.0

New:
- Support Kotlin 1.8.20 via JetBrains Compose compiler 1.4.5.

Changed:
- Upgrade JetBrains Compose runtime to 1.4.0.
- Reduce string copies and string allocation required to perform a single frame render.
- Only split text strings on newline when its value changes by caching layout information across recomposition.
- Canvas is no longer clipped for each node. If you notice any overlapping drawing, please report a bug.

Breaking:
- Composables were moved into `ui` subpackage.
- `Layout` and related interfaces were moved into `layout` subpackage.
- `Static` content is now based on `SnapshotStateList` instead of `Flow`.


## [0.5.0] - 2023-03-09
[0.5.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.5.0

 - Support Kotlin 1.8.10 via JetBrains Compose compiler 1.4.2.
 - New: `renderMosaic` function returns a single string of the composition for tools that require only static output.
 - New: Expose a custom `Layout` composable similar to Compose UI. This is just the beginning of a larger change to expose more powerful primitives.
 - Implicit root node is no longer a `Row`. Multiple children at the root will now draw on top of each other. Choose a `Row` or `Column` as the root composable yourself.
 - Each `Static` content is no longer wrapped in a `Row`. Multiple children in a single `Static` composable will draw on top of each other. Choose a `Row` or `Column` if you have multiple items. Multiple `Static` composables will still render in `Column`-like behavior


## [0.4.0] - 2023-02-19
[0.4.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.4.0

 - Mosaic is now multiplatform!

   The following targets are now supported in addition to the JVM:
     - Linux (X64)
     - MacOS (ARM & X64)
     - Windows (X64)
     - JS (experimental)

   Additionally, the JetBrains Compose compiler is now used instead of AndroidX which
   should offer better support for native and JS targets.

 - `runMosaic` is now a suspending function which will return when the composition ends.
   For the previous behavior, a `runMosaicBlocking` function is provided (JVM + native).


## [0.3.0] - 2023-01-17
[0.3.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.3.0

 - Support Kotlin 1.8.0 via Compose compiler 1.4.0.
 - New: `Static` composable for rendering permanent output.
 - Fix: Correct line calculation to prevent output from drifting downward over time when its height changes.


## [0.2.0] - 2022-08-12
[0.2.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.2.0

 - Support Kotlin 1.7.10 via Compose compiler 1.3.0.
 - Migrate from custom build of Compose compiler and Compose runtime to Google's Compose compiler and JetBrains' multiplatform Compose runtime. Note that this will require you have the Google Maven repositories in your Gradle repositories (`google()`).


## [0.1.0] - 2021-06-25
[0.1.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.1.0

Initial release!
