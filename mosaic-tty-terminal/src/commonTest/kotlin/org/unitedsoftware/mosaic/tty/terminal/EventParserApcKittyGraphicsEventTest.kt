package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.unitedsoftware.mosaic.terminal.KittyGraphicsEvent

class EventParserApcKittyGraphicsEventTest : BaseEventParserTest() {
	@Test fun pasteStart() {
		testTerminal.write("${APC}Gi=31;OK$ST")
		assertThat(parser.next()).isEqualTo(KittyGraphicsEvent(31, "OK"))
	}
}
