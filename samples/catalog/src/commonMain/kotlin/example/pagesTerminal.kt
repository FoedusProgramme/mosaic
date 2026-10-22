package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import kotlinx.io.writeString
import org.unitedsoftware.mosaic.LocalTerminalControl
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Spacer
import org.unitedsoftware.mosaic.ui.Text

private val notePath = Path(SystemTemporaryDirectory, "mosaic-catalog-note.txt")

private fun readNote(): String {
	if (!SystemFileSystem.exists(notePath)) {
		SystemFileSystem.sink(notePath).buffered().use {
			it.writeString("Edit this note, save, and quit the editor.\nThe catalog shows it again once it is back.\n")
		}
	}
	return SystemFileSystem.source(notePath).buffered().use { it.readString() }
}

@Composable
fun HandingOverPage() {
	val palette = LocalPalette.current
	val control = LocalTerminalControl.current
	val scope = rememberCoroutineScope()
	var note by remember { mutableStateOf(readNote()) }
	var status by remember { mutableStateOf("nothing run yet") }

	fun variable(name: String) = environment(name)?.takeIf { it.isNotBlank() }

	fun run(name: String, command: String) {
		scope.launch {
			val exit = control.released {
				// Blocking until the other program exits, away from the frame loop.
				withContext(Dispatchers.IO) { runInTerminal(command) }
			}
			note = readNote()
			status = "$name exited with $exit"
		}
	}

	Column(
		Modifier.onKeyEvent {
			when (it) {
				KeyEvent("e") -> {
					val editor = variable("VISUAL") ?: variable("EDITOR") ?: if (isWindows) "notepad" else "vi"
					run(editor, "$editor \"$notePath\"")
					true
				}

				KeyEvent("s") -> {
					val shell = variable(if (isWindows) "COMSPEC" else "SHELL") ?: "sh"
					run(shell, shell)
					true
				}

				KeyEvent("z") -> {
					scope.launch {
						status = if (control.suspendProcess()) "continued after suspending" else "suspending is not supported here"
					}
					true
				}

				else -> false
			}
		},
	) {
		Text("TerminalControl.released gives the terminal back to run other")
		Text("programs, like an editor or a shell, and takes it over again")
		Text("afterwards, drawing everything anew.")
		Spacer(Modifier.height(1))
		Labeled("last run") { Text(status, color = palette.accent) }
		Labeled("note") {
			Column {
				for (line in note.lines().filter { it.isNotBlank() }.take(4)) Text(line, color = palette.good)
			}
		}
		Spacer(Modifier.height(1))
		Text("e opens the note in \$VISUAL or \$EDITOR, s starts \$SHELL,", color = palette.muted)
		Text("and z suspends the catalog until fg, like Ctrl+Z anywhere", color = palette.muted)
		Text("that no key handler takes it.", color = palette.muted)
	}
}
