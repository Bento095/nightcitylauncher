// AGP 9 já compila Kotlin sozinho (não use o plugin org.jetbrains.kotlin.android).
plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
