import co.touchlab.cklib.gradle.CompileToBitcode.Language.C
import com.jakewharton.mosaic.buildsupport.ZigDownloadTask
import java.nio.file.FileSystems
import java.nio.file.Files
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.MAIN_COMPILATION_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.TEST_COMPILATION_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	id("com.jakewharton.mosaic.targets")
	id("com.jakewharton.mosaic.publish")
	alias(libs.plugins.cklib)
	id("com.jakewharton.mosaic.build")
	alias(libs.plugins.testDistribution)
	alias(libs.plugins.jextract)
	alias(libs.plugins.burst)
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("mosaic.tty")
}

val proGuardClasspath = configurations.create("proGuard")
val r8Classpath = configurations.create("r8")

repositories {
	maven {
		url = uri("https://storage.googleapis.com/r8-releases/raw/")
	}
}

dependencies {
	proGuardClasspath(libs.proguard)
	r8Classpath(libs.r8)
}

val zigDownload = tasks.register<ZigDownloadTask>("zigDownload") {
	version = libs.versions.zig
	outputDir = layout.buildDirectory.dir("zig")
}

jextract.libraries.register("mosaic") {
	header = file("src/commonMain/c/mosaic.h")
	includes.add(layout.projectDirectory.dir("src/commonMain/c"))
	headerClassName = "Libmosaic"
	targetPackage = "com.jakewharton.mosaic.tty"
}

kotlin {
	explicitApi()

	sourceSets {
		configureEach {
			languageSettings {
				optIn("kotlinx.cinterop.ExperimentalForeignApi")
			}
		}

		commonTest {
			dependencies {
				implementation(libs.androidx.annotation)
				implementation(libs.assertk)
				implementation(libs.kotlin.test)
				implementation(libs.kotlinx.coroutines.test)
			}
		}
	}

	targets.withType<KotlinJvmTarget>().configureEach {
		val target = this
		val mainCompilation = compilations.named(MAIN_COMPILATION_NAME)
		val testCompilation = compilations.named(TEST_COMPILATION_NAME)

		mainCompilation.configure {
			val headers = compileJavaTaskProvider!!.flatMap { it.options.headerOutputDirectory }
			val jniTask = tasks.register<Exec>("${target.name}JniZigBuild") {
				inputs.file("build.zig")
				inputs.dir("src/commonMain/c")
				inputs.dir("src/${target.name}Main/c")
				inputs.dir("src/${target.name}Main/include")

				val jniDir = layout.buildDirectory.dir("zigJni/${target.name}")
				outputs.dir(jniDir)

				dependsOn(headers)
				args("build", "-p", jniDir.get().asFile.absolutePath, "-Djavah=${relativePath(headers.get().asFile)}")

				dependsOn(zigDownload)
				executable(zigDownload.get().zigExecutable.path)
			}
			defaultSourceSet.resources.srcDir(jniTask)
		}

		val jdk22Compilation = compilations.create("jdk22") {
			// I can't find a way to add a Java srcDir, so we have to add it both to Kotlin task so its
			// sources can link and to Java task so the files are actually compiled to classes.
			val mosaicFfmJava = tasks.named("mosaicJextractGenerateBindings")
			defaultSourceSet.kotlin.srcDir(mosaicFfmJava)
			compileJavaTaskProvider!!.configure {
				source(mosaicFfmJava)
			}

			compileTaskProvider.configure {
				compilerOptions {
					jvmTarget = JvmTarget.JVM_22
					freeCompilerArgs.add("-Xexplicit-api=strict")
				}
			}
			compileJavaTaskProvider!!.configure {
				sourceCompatibility = "22"
				targetCompatibility = "22"
			}
			associateWith(mainCompilation.get())
		}

		val mainJarTask = tasks.named<Jar>(artifactsTaskName) {
			from(jdk22Compilation.output.allOutputs) {
				into("META-INF/versions/22")
				// Don't add a nested .kotlin_module file.
				exclude("META-INF")
				// Empty class noise produced by jextract.
				exclude("**/_*.class")
			}
			manifest {
				attributes(mapOf("Multi-Release" to "true"))
			}
		}

		val mainJar = mainJarTask.flatMap { it.archiveFile }
		val testJar = tasks.register<Jar>("${target.name}TestJar") {
			from(testCompilation.map { it.output.allOutputs })
			archiveBaseName = base.archivesName.map { "$it-${target.name}" }
			archiveClassifier = "tests"
		}.flatMap { it.archiveFile }

		val testDependencyFiles = testCompilation.map { it.runtimeDependencyFiles!! }

		// Resolved at execution time, after the jars have been built.
		val shrinkerInputJars = CommandLineArgumentProvider {
			testDependencyFiles.get().files.filter { it.isFile }.map { it.absolutePath } +
				mainJar.get().asFile.absolutePath +
				testJar.get().asFile.absolutePath
		}

		val r8Rules = layout.buildDirectory.file("shrinker/r8.txt")
		val r8RulesExtract = tasks.register<JavaExec>("${target.name}ExtractR8Rules") {
			inputs.files(mainJar)
			inputs.files(testJar)
			inputs.files(r8Classpath)
			inputs.files(testDependencyFiles)
			outputs.file(r8Rules.get())

			classpath(r8Classpath)
			mainClass = "com.android.tools.r8.ExtractR8Rules"
			args(
				"--rules-output",
				r8Rules.get().asFile.absolutePath,
				"--include-origin-comments",
			)
			argumentProviders.add(shrinkerInputJars)
		}

		val r8Jar = base.archivesName.flatMap { archivesName ->
			layout.buildDirectory.file("libs/$archivesName-${target.name}-$version-testsR8.jar")
		}
		val r8Task = tasks.register<JavaExec>("${target.name}TestJarR8") {
			group = BUILD_GROUP
			description = "Assembles an archive containing the test classes run through ProGuard."

			inputs.files(r8Classpath)
			inputs.files(testDependencyFiles)
			inputs.files(r8RulesExtract)
			outputs.file(r8Jar.get())

			classpath(r8Classpath)
			mainClass = "com.android.tools.r8.R8"
			args(
				"--classfile",
				"--output",
				r8Jar.get().asFile.absolutePath,
				"--pg-conf",
				r8Rules.get().asFile.absolutePath,
				"--lib",
				System.getProperty("java.home"),
			)
			argumentProviders.add(shrinkerInputJars)
			doLast {
				// Quick work around for https://issuetracker.google.com/issues/134372167.
				FileSystems.newFileSystem(r8Jar.get().asFile.toPath(), null as ClassLoader?).use { fs ->
					val root = fs.rootDirectories.first()
					Files.delete(root.resolve("module-info.class"))
					Files.delete(root.resolve("META-INF/versions/9/module-info.class"))
				}
			}
		}

		testRuns.create("r8") {
			executionTask.configure {
				dependsOn(r8Task)
			}
			setExecutionSourceFrom(
				files(r8Jar.get()),
				testDependencyFiles.get().filter { it.isDirectory },
			)
		}

		val proGuardJar = base.archivesName.flatMap { archivesName ->
			layout.buildDirectory.file("libs/$archivesName-${target.name}-$version-testsProGuard.jar")
		}
		val proGuardTask = tasks.register<JavaExec>("${target.name}TestJarProGuard") {
			group = BUILD_GROUP
			description = "Assembles an archive containing the test classes run through ProGuard."

			inputs.files(proGuardClasspath)
			inputs.files(testDependencyFiles)
			outputs.file(proGuardJar.get())

			classpath(proGuardClasspath)
			mainClass = "proguard.ProGuard"
			args(
				"-libraryjars",
				"<java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)",
				// TODO These should be pulled from the jars, but for now this unblocks us.
				"@src/jvmMain/resources/META-INF/proguard/com.jakewharton.mosaic-tty.pro",
				"@src/jvmTest/resources/META-INF/proguard/com.jakewharton.mosaic-tty-tests.pro",
			)
			argumentProviders.add(
				CommandLineArgumentProvider {
					listOf(
						"-injars",
						testDependencyFiles.get().files.filter { it.exists() }.joinToString(File.pathSeparator),
						// These do not need deferred, but since it must come after -injars it has to go here.
						"-outjars",
						proGuardJar.get().asFile.toString(),
					)
				},
			)
		}

		testRuns.create("proGuard") {
			executionTask.configure {
				dependsOn(proGuardTask)
			}
			setExecutionSourceFrom(
				files(proGuardJar.get()),
				testDependencyFiles.get().filter { it.isDirectory },
			)
		}

		// Adding additional test runs somehow removes the configuration capabilities which allow
		// automatic resolution of the junit test dependency. Add it explicitly instead.
		testCompilation.configure {
			defaultSourceSet.dependencies {
				implementation(libs.kotlin.test.junit)
			}
		}
	}

	val linkNativeDebugTests = tasks.register("linkNativeDebugTests")

	targets.withType<KotlinNativeTarget>().configureEach {
		compilations.getByName(MAIN_COMPILATION_NAME).cinterops.create("mosaic") {
			header(file("src/commonMain/c/mosaic.h"))
			packageName("com.jakewharton.mosaic.tty")
			compilerOpts(
				"-std=gnu11",
				"-Wall",
				"-Wextra",
				"-Werror",
			)
		}
		val testBinariesTaskName = compilations.getByName(TEST_COMPILATION_NAME).binariesTaskName
		linkNativeDebugTests.configure {
			dependsOn(testBinariesTaskName)
		}
	}

	compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}

cklib {
	config.kotlinVersion = libs.versions.kotlin.get()
	create("mosaic", file("src/commonMain/c"), listOf("main")) {
		srcDirs = files("src/commonMain/c")
		language = C
	}
}
