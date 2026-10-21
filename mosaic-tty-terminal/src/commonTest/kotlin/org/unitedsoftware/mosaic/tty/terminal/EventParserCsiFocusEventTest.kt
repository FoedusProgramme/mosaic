package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.unitedsoftware.mosaic.terminal.FocusEvent

class EventParserCsiFocusEventTest : BaseEventParserTest() {
	@Test fun focusedTrue() {
		testTerminal.write("${CSI}I")
		assertThat(parser.next()).isEqualTo(FocusEvent(focused = true))
	}

	@Test fun focusedFalse() {
		testTerminal.write("${CSI}O")
		assertThat(parser.next()).isEqualTo(FocusEvent(focused = false))
	}
}
