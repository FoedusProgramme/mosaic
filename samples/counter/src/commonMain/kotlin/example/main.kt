@file:JvmName("Main")

package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.jvm.JvmName
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.unitedsoftware.mosaic.NonInteractivePolicy.Ignore
import org.unitedsoftware.mosaic.runMosaicBlocking
import org.unitedsoftware.mosaic.ui.Text

@Composable
fun Counter() {
	var count by remember { mutableIntStateOf(0) }

	Text("The count is: $count")

	LaunchedEffect(Unit) {
		for (i in 1..20) {
			delay(250.milliseconds)
			count = i
		}
	}
}

fun main() {
	runMosaicBlocking(onNonInteractive = Ignore) {
		Counter()
	}
}
