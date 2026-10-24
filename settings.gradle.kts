pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    // Скачивает JDK 21 для сборки, если на машине стоит только более старая Java.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "youlauncher"
include(":tools")

// jlink и jpackage читают файл аргументов (@argfile) в кодировке ANSI, а Gradle пишет его в UTF-8.
// Если путь проекта содержит не-ASCII символы (например, кириллицу), упаковка ломается.
// Поэтому каталог сборки переносится в ASCII-путь в %LOCALAPPDATA%. Делается до применения плагинов,
// иначе плагин Compose успевает запомнить старый путь для сгенерированных исходников.
gradle.beforeProject {
    if (!rootDir.absolutePath.all { it.code < 128 }) {
        val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")
        val id = Integer.toHexString(rootDir.absolutePath.hashCode())
        val name = if (path == ":") "root" else path.removePrefix(":").replace(':', '-')
        layout.buildDirectory.set(File(base, "YouLauncher-build/$id/$name"))
    }
}
