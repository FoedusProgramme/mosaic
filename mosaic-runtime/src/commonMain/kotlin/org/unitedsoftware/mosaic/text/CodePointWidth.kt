package org.unitedsoftware.mosaic.text

import de.cketti.codepoints.codePointAt

private const val ZeroWidthJoiner = 0x200D

/** The number of terminal columns [codePoint] occupies: 0, 1, or 2. */
internal fun codePointWidth(codePoint: Int): Int {
	return when {
		// Nothing below the combining diacritical marks is zero or two columns wide.
		codePoint < 0x300 -> 1

		zeroWidthCodePointRanges.containsCodePoint(codePoint) -> 0

		wideCodePointRanges.containsCodePoint(codePoint) -> 2

		else -> 1
	}
}

/**
 * Calls [block] with each code point of this text and the number of columns it occupies.
 *
 * Zero width code points, such as combining marks, belong to the preceding code point. So does a
 * code point following a zero width joiner, since terminals render emoji joined that way as one.
 */
internal inline fun CharSequence.forEachCodePointWithWidth(
	block: (start: Int, end: Int, codePoint: Int, width: Int) -> Unit,
) {
	forEachCodePointWithWidth(0, length, block)
}

/** Like [forEachCodePointWithWidth], for the code points from [startIndex] until [endIndex]. */
internal inline fun CharSequence.forEachCodePointWithWidth(
	startIndex: Int,
	endIndex: Int,
	block: (start: Int, end: Int, codePoint: Int, width: Int) -> Unit,
) {
	var index = startIndex
	var joined = false
	while (index < endIndex) {
		val char = this[index]
		// Most text is in the Basic Multilingual Plane, which needs no decoding.
		val codePoint = if (char.isHighSurrogate()) codePointAt(index) else char.code
		val end = index + if (codePoint >= 0x10000) 2 else 1
		val width = if (joined) 0 else codePointWidth(codePoint)
		joined = codePoint == ZeroWidthJoiner
		block(index, end, codePoint, width)
		index = end
	}
}

/** The number of terminal columns this text occupies. */
internal fun CharSequence.columnWidth(): Int {
	var columns = 0
	forEachCodePointWithWidth { _, _, _, width ->
		columns += width
	}
	return columns
}

/** Binary search of inclusive ranges stored as pairs of first and last code point. */
private fun IntArray.containsCodePoint(codePoint: Int): Boolean {
	var low = 0
	var high = size / 2 - 1
	while (low <= high) {
		val middle = (low + high) ushr 1
		when {
			codePoint < this[middle * 2] -> high = middle - 1
			codePoint > this[middle * 2 + 1] -> low = middle + 1
			else -> return true
		}
	}
	return false
}
