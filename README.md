# Mosaic

An experimental tool for building console UI in Kotlin using the Jetpack Compose compiler/runtime.
Inspired by [Ink](https://github.com/vadimdemedes/ink).

This is United Software's fork of [JakeWharton/mosaic](https://github.com/JakeWharton/mosaic),
published under the `org.unitedsoftware.mosaic` group and package so that it can be used next to
the upstream artifacts.

<img src="samples/jest/demo.gif">

Jump to:
[Introduction](#Introduction) |
[Usage](#Usage) |
[Samples](#Samples) |
[Building](#Building) |
[FAQ](#FAQ) |
[License](#License)


## Introduction

The entrypoint to Mosaic is the `runMosaic` function.
The lambda passed to this function is responsible for both output and performing work.

```kotlin
suspend fun main() = runMosaic {
  Text("The count is: 0")
}
```

To change the output dynamically we can use local properties to hold state.
Let's update our counter to actually count to 20.

```kotlin
suspend fun main() = runMosaic {
  var count by remember { mutableIntStateOf(0) }

  Text("The count is: $count")

  LaunchedEffect(Unit) {
    for (i in 1..20) {
      delay(250.milliseconds)
      count = i
    }
  }
}
```

(Note: You may need to add imports for `androidx.compose.runtime.getValue` and `import androidx.compose.runtime.setValue` manually.)

<img src="samples/counter/demo.gif">

That is the most basic sample of Mosaic.
From there, the limit is only your imagination.

<img src="samples/jest/demo.gif">

<img src="samples/rrtop/demo.gif">

_(Note: graphs in the above have rendering problems due to asciinema/agg which do not appear in the real output)_

## Usage

Mosaic is a library for Compose, and it relies on JetBrains' Kotlin Compose plugin to be present for use.
Any module which wants to call `runMosaic` or define `@Composable` functions for use with Mosaic must have this plugin applied.
For more information, see [the JetBrains Compose compiler documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compiler.html).

Mosaic itself can then be added like any other dependency.
Nothing has been published under the `org.unitedsoftware.mosaic` group yet. Until then, run
`./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories. Once enabled, snapshots
of `master` will be published to the Central Portal Snapshots repository:

```kotlin
repositories {
  mavenCentral()
  maven("https://central.sonatype.com/repository/maven-snapshots/")
}
dependencies {
  implementation("org.unitedsoftware.mosaic:mosaic-runtime:0.19.0-SNAPSHOT")
}
```

Snapshot documentation is available at
[foedusprogramme.github.io/mosaic/docs/latest/](https://foedusprogramme.github.io/mosaic/docs/latest/).

`mosaic-animation` adds Compose's animation APIs, and `mosaic-markdown` renders Markdown, also
while it streams in. See [its README](mosaic-markdown/README.md).

Upstream releases up to 0.18.0 are available as `com.jakewharton.mosaic:mosaic-runtime`.


## Samples

Run `./gradlew installJvmDist` to build the JVM sample binaries, then run them from a terminal.
Native binaries are built with `./gradlew linkReleaseExecutable<Target>`, for example
`./gradlew :samples:counter:linkReleaseExecutableMacosArm64` produces
`samples/counter/build/bin/macosArm64/releaseExecutable/counter.kexe`.

 * [Catalog](samples/catalog): An interactive tour of every component, modifier, and capability, plus recipes for components Mosaic does not ship.

   `./samples/catalog/build/install/catalog-jvm/bin/catalog`

 * [Counter](samples/counter): A simple increasing number from 0 until 20.

   `./samples/counter/build/install/counter-jvm/bin/counter`

 * [Demo](samples/demo): A playground for demonstrating many features of Mosaic.

   `./samples/demo/build/install/demo-jvm/bin/demo`

 * [Jest](samples/jest): Example output of a test framework (such as JS's 'Jest').

   `./samples/jest/build/install/jest-jvm/bin/jest`

 * [Robot](samples/robot): An interactive, game-like program with keyboard control.

   `./samples/robot/build/install/robot-jvm/bin/robot`

 * [rrtop](samples/rrtop): An example inspired by [rrtop](https://github.com/wojciech-zurek/rrtop).

   `./samples/rrtop/build/install/rrtop-jvm/bin/rrtop`

 * [snake](samples/snake): Snake game.

   `./samples/snake/build/install/snake-jvm/bin/snake`

## Building

Run `./gradlew build` to build and test everything.

 * Gradle runs on Azul Zulu JDK 26, as declared in `gradle/gradle-daemon-jvm.properties`. It is
   found among the installed JDKs, or downloaded automatically, so `JAVA_HOME` does not matter.
 * Zig is downloaded automatically to compile the C parts of `mosaic-tty`, both the JNI library for
   the JVM and the static library embedded into the native klibs.
 * Building every native target (Linux, macOS, and Windows) works from a Mac.
   Pass `-Pmosaic.hostOnly=true`, or set it in `~/.gradle/gradle.properties`, to only build the JVM
   and the native target of the current machine, which is much faster.
   This skips the klib API check, which CI still runs.
 * After changing public API, run `./gradlew apiDump` (without `mosaic.hostOnly`) and commit the
   updated files in each module's `api/` directory.

## FAQ

### I thought Compose was a UI toolkit for Android?

Compose is, at its core, a general-purpose compiler and runtime to do state tracking and tree node
and property manipulation. This can be used on any platform supported by Kotlin for any kind of
state or with any tree. It's an amazing piece of technology.

Compose UI is the modern UI toolkit for Android which also runs on the desktop, iOS, and the web.
The lack of differentiation between these two technologies has unfortunately caused Compose UI to
overshadow the core under the single "Compose" moniker in an unforced marketing error.

Other examples of non-Compose UI-based Compose projects are Cash App's Redwood,
JetBrains' Compose HTML, or Google's Jetpack Glance.

### Broken output with `./gradlew run` and/or inside IntelliJ IDEA

Running within Gradle or IntelliJ IDEA will not work. These tools do not provide access to the TTY
in order to be interactive. And for non-interactive programs, both will strip ANSI control
characters which prevent Mosaic from redrawing over a previous frame. The output will likely just
render in successive lines instead.

Run your programs directly in a terminal emulator–no IDE and no Gradle.

### "A restricted method in java.lang.System has been called" warning on JDK 24 and newer

Mosaic loads a small native library to talk to the terminal, which recent JDKs warn about unless
native access is enabled. Pass `--enable-native-access=ALL-UNNAMED` to `java`, or
`--enable-native-access=org.unitedsoftware.mosaic.tty` when using the module path.
The flag is not recognized before JDK 17.


# License

    Copyright 2020 Jake Wharton
    Copyright 2026 United Software

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
