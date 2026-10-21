package org.unitedsoftware.mosaic.text

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class CodePointWidthTest {
	@Test fun ascii() {
		assertThat(codePointWidth('A'.code)).isEqualTo(1)
		assertThat(codePointWidth(' '.code)).isEqualTo(1)
		assertThat(codePointWidth('~'.code)).isEqualTo(1)
	}

	@Test fun latin() {
		assertThat(codePointWidth('é'.code)).isEqualTo(1)
		assertThat(codePointWidth(0x00AD)).isEqualTo(1) // Soft hyphen.
	}

	@Test fun cjk() {
		assertThat(codePointWidth('中'.code)).isEqualTo(2)
		assertThat(codePointWidth('あ'.code)).isEqualTo(2)
		assertThat(codePointWidth('ア'.code)).isEqualTo(2)
		assertThat(codePointWidth('가'.code)).isEqualTo(2)
		assertThat(codePointWidth('，'.code)).isEqualTo(2) // Fullwidth comma.
		assertThat(codePointWidth('Ａ'.code)).isEqualTo(2) // Fullwidth A.
	}

	@Test fun unassignedCjkDefaultsToWide() {
		assertThat(codePointWidth(0x2FFFD)).isEqualTo(2)
		assertThat(codePointWidth(0x3FFFD)).isEqualTo(2)
	}

	@Test fun emoji() {
		assertThat(codePointWidth(0x1F600)).isEqualTo(2) // 😀
		assertThat(codePointWidth(0x231A)).isEqualTo(2) // ⌚
		assertThat(codePointWidth(0x26A1)).isEqualTo(2) // ⚡
	}

	@Test fun ambiguousIsNarrow() {
		assertThat(codePointWidth('①'.code)).isEqualTo(1)
		assertThat(codePointWidth('─'.code)).isEqualTo(1)
	}

	@Test fun zeroWidth() {
		assertThat(codePointWidth(0x0301)).isEqualTo(0) // Combining acute accent.
		assertThat(codePointWidth(0x200B)).isEqualTo(0) // Zero width space.
		assertThat(codePointWidth(0x200D)).isEqualTo(0) // Zero width joiner.
		assertThat(codePointWidth(0xFE0F)).isEqualTo(0) // Variation selector 16.
		assertThat(codePointWidth(0x1161)).isEqualTo(0) // Hangul medial vowel A.
	}

	@Test fun columnWidth() {
		assertThat("A中B".columnWidth()).isEqualTo(4)
		assertThat("é".columnWidth()).isEqualTo(1)
		assertThat("가".columnWidth()).isEqualTo(2) // ᄀ + ᅡ is the syllable 가.
		assertThat("😀😀".columnWidth()).isEqualTo(4)
	}

	@Test fun zeroWidthJoinerSequenceIsOneGlyph() {
		// Man, ZWJ, woman, ZWJ, girl.
		assertThat("👨‍👩‍👧".columnWidth()).isEqualTo(2)
	}
}
