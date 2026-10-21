import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.jvm)
	`java-gradle-plugin`
}

gradlePlugin {
	plugins {
		create("mosaicBuild") {
			id = "org.unitedsoftware.mosaic.build"
			displayName = "Mosaic Build plugin"
			description = "Gradle plugin for Mosaic build things"
			implementationClass = "org.unitedsoftware.mosaic.buildsupport.MosaicBuildPlugin"
		}
		create("mosaicTargets") {
			id = "org.unitedsoftware.mosaic.targets"
			displayName = "Mosaic targets plugin"
			description = "Adds every Kotlin target Mosaic supports"
			implementationClass = "org.unitedsoftware.mosaic.buildsupport.MosaicTargetsPlugin"
		}
		create("mosaicPublish") {
			id = "org.unitedsoftware.mosaic.publish"
			displayName = "Mosaic publish plugin"
			description = "Publishing, API validation, and docs for Mosaic libraries"
			implementationClass = "org.unitedsoftware.mosaic.buildsupport.MosaicPublishPlugin"
		}
	}
}

dependencies {
	compileOnly(libs.kotlin.gradlePlugin)
	compileOnly(libs.binaryCompatibilityValidator.gradlePlugin)
	implementation(libs.apache.compress)
}

java {
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
	explicitApi()
	compilerOptions.jvmTarget = JvmTarget.JVM_11
}
