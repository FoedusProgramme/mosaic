package org.unitedsoftware.mosaic.ui

import kotlin.io.encoding.Base64

/**
 * Whether the terminal named [name] supports the inline images of iTerm2, which unlike Sixel keep
 * transparency. iTerm2 also answers the Kitty graphics query, but without the placeholders which
 * Mosaic needs, and fills transparent parts of Sixel images with a color.
 */
internal fun supportsInlineImages(name: String?): Boolean {
	if (name == null) return false
	val lowercase = name.lowercase()
	return lowercase.startsWith("iterm2") || lowercase.startsWith("wezterm")
}

/**
 * The escape sequence which shows [pixels] of [width] by [height] across [columns] by [rows] cells
 * at the cursor, using the inline images protocol of iTerm2.
 *
 * See https://iterm2.com/documentation-images.html.
 */
internal fun encodeInlineImage(pixels: IntArray, width: Int, height: Int, columns: Int, rows: Int): String {
	val png = encodePng(pixels, width, height)
	return buildString {
		append("\u001B]1337;File=inline=1;size=")
		append(png.size)
		append(";width=")
		append(columns)
		append(";height=")
		append(rows)
		append(";preserveAspectRatio=1:")
		append(Base64.encode(png))
		append('\u0007')
	}
}

/**
 * Encodes [pixels] as a PNG with an alpha channel. The image data is stored without compression,
 * since a deflate stream of stored blocks is valid and needs no compressor.
 */
internal fun encodePng(pixels: IntArray, width: Int, height: Int): ByteArray {
	// Each row starts with filter type 0, followed by the RGBA bytes of its pixels.
	val raw = ByteArray(height * (1 + width * 4))
	var offset = 0
	for (y in 0 until height) {
		raw[offset++] = 0
		for (x in 0 until width) {
			val pixel = pixels[y * width + x]
			raw[offset++] = (pixel shr 16).toByte()
			raw[offset++] = (pixel shr 8).toByte()
			raw[offset++] = pixel.toByte()
			raw[offset++] = (pixel ushr 24).toByte()
		}
	}

	val out = ByteBuilder(raw.size + raw.size / 65535 * 5 + 128)
	out.bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
	out.chunk("IHDR") {
		int(width)
		int(height)
		// 8 bits per channel, RGBA, deflate, adaptive filtering, no interlacing.
		bytes(8, 6, 0, 0, 0)
	}
	out.chunk("IDAT") {
		// A zlib header for deflate without a preset dictionary, then stored blocks.
		bytes(0x78, 0x01)
		var start = 0
		do {
			val length = minOf(65535, raw.size - start)
			val last = start + length == raw.size
			bytes(if (last) 1 else 0, length and 0xFF, length shr 8, length.inv() and 0xFF, length.inv() shr 8 and 0xFF)
			append(raw, start, length)
			start += length
		} while (start < raw.size)
		int(adler32(raw))
	}
	out.chunk("IEND") {}
	return out.toByteArray()
}

private class ByteBuilder(capacity: Int) {
	private var buffer = ByteArray(capacity)
	var size = 0
		private set

	private fun ensure(extra: Int) {
		if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
	}

	fun bytes(vararg values: Int) {
		ensure(values.size)
		for (value in values) buffer[size++] = value.toByte()
	}

	fun int(value: Int) = bytes(value ushr 24, value shr 16 and 0xFF, value shr 8 and 0xFF, value and 0xFF)

	fun append(bytes: ByteArray, offset: Int, length: Int) {
		ensure(length)
		bytes.copyInto(buffer, size, offset, offset + length)
		size += length
	}

	/** Writes a chunk of [type] whose data [block] writes, with its length and checksum. */
	fun chunk(type: String, block: ByteBuilder.() -> Unit) {
		val lengthOffset = size
		int(0)
		val typeOffset = size
		for (char in type) bytes(char.code)
		block()
		val length = size - typeOffset - 4
		buffer[lengthOffset] = (length ushr 24).toByte()
		buffer[lengthOffset + 1] = (length shr 16).toByte()
		buffer[lengthOffset + 2] = (length shr 8).toByte()
		buffer[lengthOffset + 3] = length.toByte()
		int(crc32(buffer, typeOffset, size - typeOffset))
	}

	fun toByteArray() = buffer.copyOf(size)
}

private val crcTable = IntArray(256) { n ->
	var c = n
	repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
	c
}

internal fun crc32(bytes: ByteArray, offset: Int, length: Int): Int {
	var crc = -1
	for (index in offset until offset + length) {
		crc = crcTable[(crc xor bytes[index].toInt()) and 0xFF] xor (crc ushr 8)
	}
	return crc.inv()
}

private fun adler32(bytes: ByteArray): Int {
	var a = 1
	var b = 0
	for (byte in bytes) {
		a = (a + (byte.toInt() and 0xFF)) % 65521
		b = (b + a) % 65521
	}
	return (b shl 16) or a
}
