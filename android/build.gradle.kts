// TEACHING NOTE: plugins are the build's superpowers. AGP (Android Gradle
// Plugin) knows how to turn Kotlin + resources into an APK; Kotlin plugins
// compile Kotlin and wire up Compose's compiler.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
