package org.unitedsoftware.mosaic.tty.terminal

internal const val ESC = "\u001B"
internal const val ST = "${ESC}\\"

internal const val APC = "${ESC}_"
internal const val CSI = "$ESC["
internal const val DCS = "${ESC}P"
internal const val OSC = "$ESC]"
internal const val SS3 = "${ESC}O"

internal const val cursorMode = 25
internal const val cursorEnable = "$CSI?${cursorMode}h"
internal const val cursorDisable = "$CSI?${cursorMode}l"

internal const val focusMode = 1004
internal const val focusEnable = "$CSI?${focusMode}h"
internal const val focusDisable = "$CSI?${focusMode}l"

internal const val synchronizedOutputMode = 2026

internal const val systemThemeMode = 2031
internal const val systemThemeEnable = "$CSI?${systemThemeMode}h"
internal const val systemThemeDisable = "$CSI?${systemThemeMode}l"

internal const val inBandResizeMode = 2048
internal const val inBandResizeEnable = "$CSI?${inBandResizeMode}h"
internal const val inBandResizeDisable = "$CSI?${inBandResizeMode}l"

internal const val bracketedPasteMode = 2004
internal const val bracketedPasteEnable = "$CSI?${bracketedPasteMode}h"
internal const val bracketedPasteDisable = "$CSI?${bracketedPasteMode}l"

/** Push the Kitty keyboard protocol flag which disambiguates escape codes, such as Shift+Enter. */
internal const val kittyKeyboardPushDisambiguate = "$CSI>1u"
internal const val kittyKeyboardPop = "$CSI<u"

/**
 * Set xterm's modifyOtherKeys to 2, which reports keys with modifiers, such as Shift+Enter or Ctrl+C, as
 * `CSI 27 ; modifier ; codepoint ~`. Level 1 leaves Shift+Enter as Enter in iTerm2.
 */
internal const val modifyOtherKeysEnable = "$CSI>4;2m"
internal const val modifyOtherKeysReset = "$CSI>4m"
