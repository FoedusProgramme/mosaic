pluginManagement {
	includeBuild("build-support")

	repositories {
		mavenCentral()
		google()
		gradlePluginPortal()
	}
}

plugins {
	id("com.gradle.develocity") version "4.6.1"
	// Provisions the daemon JDK declared in gradle/gradle-daemon-jvm.properties when none is installed.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

develocity {
	buildScan {
		termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
		if (System.getenv("CI") == "true") {
			termsOfUseAgree = "yes"
			tag("CI")
		} else {
			publishing.onlyIf { false }
		}
	}
}

rootProject.name = "mosaic"

include(":mosaic-animation")
include(":mosaic-runtime")
include(":mosaic-terminal")
include(":mosaic-testing")
include(":mosaic-tty")
include(":mosaic-tty-terminal")

include(":samples:catalog")
include(":samples:counter")
include(":samples:demo")
include(":samples:java-tty")
include(":samples:jest")
include(":samples:robot")
include(":samples:rrtop")
include(":samples:snake")

include(":tools:raw-mode-echo")
include(":tools:terminal-capability")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
