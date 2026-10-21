package org.unitedsoftware.mosaic.buildsupport

import java.util.Locale.US
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Compiles C [sources] into a static library for a Kotlin/Native target with `zig cc`, so cinterop
 * can embed it into its klib.
 */
@CacheableTask
public abstract class ZigStaticLibraryTask : DefaultTask() {
	/** The directory of a Zig installation, such as the output of [ZigDownloadTask]. */
	@get:Internal
	public abstract val zigDirectory: DirectoryProperty

	@get:Input
	public abstract val zigVersion: Property<String>

	/** A Zig target triple, such as `x86_64-linux-gnu.2.19`. */
	@get:Input
	public abstract val target: Property<String>

	@get:Input
	public abstract val compilerArgs: ListProperty<String>

	@get:InputFiles
	@get:PathSensitive(RELATIVE)
	public abstract val sources: ConfigurableFileCollection

	/** Headers included by [sources]. Only used to track changes. */
	@get:InputFiles
	@get:PathSensitive(RELATIVE)
	public abstract val headers: ConfigurableFileCollection

	@get:OutputFile
	public abstract val library: RegularFileProperty

	@get:Inject
	protected abstract val execOperations: ExecOperations

	@TaskAction
	public fun compile() {
		val windowsHost = "windows" in System.getProperty("os.name").lowercase(US)
		val zig = zigDirectory.get().file(if (windowsHost) "zig.exe" else "zig").asFile.absolutePath
		val objectDir = temporaryDir.resolve("objects").apply {
			deleteRecursively()
			mkdirs()
		}

		val objects = sources.files.sorted().map { source ->
			val output = objectDir.resolve(source.nameWithoutExtension + ".o")
			execOperations.exec {
				it.executable(zig)
				it.args("cc", "-target", target.get())
				it.args(compilerArgs.get())
				it.args("-c", source.absolutePath, "-o", output.absolutePath)
			}
			output
		}

		val output = library.get().asFile
		output.delete()
		execOperations.exec {
			it.executable(zig)
			it.args("ar", "rcs", output.absolutePath)
			it.args(objects.map { it.absolutePath })
		}
	}
}
