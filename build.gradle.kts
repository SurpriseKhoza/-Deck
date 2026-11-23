plugins {
    id("com.android.application") version "9.5.0" apply false
    // AGP 9 has built-in Kotlin, so org.jetbrains.kotlin.android is no longer applied.
    // The Compose compiler plugin version must match the Kotlin version.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
