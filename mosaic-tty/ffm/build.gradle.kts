import de.infolektuell.gradle.jextract.tasks.JextractGenerateTask

// Generates the FFM bindings for mosaic.h which the mosaic-tty JDK 22 compilation compiles.
// The jextract plugin applies the 'java' plugin, so it cannot be applied to the multiplatform
// mosaic-tty project itself.
plugins {
	alias(libs.plugins.jextract)
}

jextract {
	// The bindings are loaded on JDK 22 and newer, so use the jextract release for JDK 22.
	installation.javaLanguageVersion = JavaLanguageVersion.of(22)
}

val mosaic = jextract.libraries.register("mosaic") {
	header = layout.projectDirectory.file("../src/commonMain/c/mosaic.h")
	includes.add(layout.projectDirectory.dir("../src/commonMain/c"))
	headerClassName = "Libmosaic"
	targetPackage = "org.unitedsoftware.mosaic.tty"
	generateSourceFiles = true
}

sourceSets.main {
	jextract.libraries.add(mosaic.get())
}

configurations.consumable("ffmBindingsElements") {
	attributes {
		attribute(Usage.USAGE_ATTRIBUTE, objects.named("mosaic-ffm-bindings"))
	}
	outgoing.artifact(
		tasks.named<JextractGenerateTask>(mosaic.get().generateBindingsTaskName).flatMap { it.sources },
	)
}
