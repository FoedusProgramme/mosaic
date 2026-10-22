package example

import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.posix.system

actual fun runInTerminal(command: String): Int = system(command)

@OptIn(ExperimentalForeignApi::class)
actual fun environment(name: String): String? = getenv(name)?.toKString()

@OptIn(ExperimentalNativeApi::class)
actual val isWindows: Boolean = Platform.osFamily == OsFamily.WINDOWS
