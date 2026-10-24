// Вспомогательные утилиты разработки: генерация иконок Material Symbols, рендер логотипа в .ico/.png,
// скриншоты экранов. В приложение не входят.
plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("org.jetbrains.compose.components:components-resources:1.12.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
}

val root = rootProject.layout.projectDirectory

tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

// Генератор иконок не зависит от приложения, чтобы его можно было запустить до первой сборки.
val gen: SourceSet by sourceSets.creating
dependencies {
    // Плагин компилятора Compose применяется ко всем наборам исходников и требует runtime в classpath.
    "genImplementation"("org.jetbrains.compose.runtime:runtime:1.12.2")
}

tasks.register<JavaExec>("generateSymbols") {
    group = "youlauncher"
    description = "Скачивает Material Symbols Rounded и генерирует Symbols.kt"
    classpath = gen.runtimeClasspath
    mainClass = "app.youlauncher.tools.GenerateSymbolsKt"
    args(root.file("tools/symbols.txt").asFile.absolutePath, root.file("src/main/kotlin/app/youlauncher/ui/icons/Symbols.kt").asFile.absolutePath)
}

tasks.register<JavaExec>("renderIcons") {
    group = "youlauncher"
    description = "Рисует логотип YouLauncher кодом и сохраняет packaging/youlauncher.ico и PNG"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.RenderIconsKt"
    args(root.dir("packaging").asFile.absolutePath, root.file("src/main/composeResources/drawable").asFile.absolutePath)
}

tasks.register<JavaExec>("renderScreens") {
    group = "youlauncher"
    description = "Делает скриншоты всех экранов в светлой и тёмной теме в docs/screens"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.RenderScreensKt"
    // -PshotWidth=900 -PshotHeight=640 -PshotDir=docs/screens/narrow -PshotOnly=home,library
    val dir = (project.findProperty("shotDir") as String?) ?: "docs/screens"
    args(listOf(root.dir(dir).asFile.absolutePath) + ((project.findProperty("shotOnly") as String?)?.split(',') ?: emptyList()))
    systemProperty("youlauncher.home", layout.buildDirectory.dir("screens-home").get().asFile.absolutePath)
    systemProperty("youlauncher.demo", "true")
    systemProperty("youlauncher.shot.width", (project.findProperty("shotWidth") as String?) ?: "1440")
    systemProperty("youlauncher.shot.height", (project.findProperty("shotHeight") as String?) ?: "900")
    (project.findProperty("shotLang") as String?)?.let { lang ->
        systemProperty("youlauncher.shot.lang", lang)
        // Демо-данные (названия экземпляров, описания модов) берут язык из локали JVM; у каждого языка своя папка данных.
        if (lang == "English") jvmArgs("-Duser.language=en", "-Duser.country=US")
        systemProperty("youlauncher.home", layout.buildDirectory.dir("screens-home-$lang").get().asFile.absolutePath)
    }
}

tasks.register<JavaExec>("renderEmblems") {
    group = "youlauncher"
    description = "Лист всех эмблем версий в docs/screens для проверки глазами"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.RenderEmblemsKt"
    args(root.dir("docs/screens").asFile.absolutePath)
}

tasks.register<JavaExec>("renderWallpapers") {
    group = "youlauncher"
    description = "Лист всех встроенных обоев в docs/screens для проверки глазами"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.RenderWallpapersKt"
    args(root.dir("docs/screens").asFile.absolutePath)
}

tasks.register<JavaExec>("benchFrames") {
    group = "youlauncher"
    description = "Замер времени кадра основных экранов (сравнение до и после оптимизаций)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.BenchFramesKt"
    systemProperty("youlauncher.home", layout.buildDirectory.dir("screens-home").get().asFile.absolutePath)
    systemProperty("youlauncher.demo", "true")
    systemProperty("stdout.encoding", "UTF-8")
}

tasks.register<JavaExec>("verifyJava") {
    group = "youlauncher"
    description = "Реально скачивает Temurin (-Pmajor=8), проверяет sha256 и запуск"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.VerifyJavaKt"
    args(listOf((project.findProperty("major") as String?) ?: "8"))
    systemProperty("youlauncher.home", (System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")) + "/YouLauncher-verify")
    systemProperty("stdout.encoding", "UTF-8")
}

tasks.register<JavaExec>("verifyRepair") {
    group = "youlauncher"
    description = "Реально портит файлы версии (-Pversion=1.21.1) и проверяет починку"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.VerifyRepairKt"
    args(listOf((project.findProperty("version") as String?)?.takeIf { it != "unspecified" } ?: "1.21.1"))
    systemProperty("youlauncher.home", (System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")) + "/YouLauncher-verify")
    systemProperty("stdout.encoding", "UTF-8")
}

tasks.register<JavaExec>("verifyPing") {
    group = "youlauncher"
    description = "Реально опрашивает серверы по Server List Ping (-Pservers=адрес,адрес)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.VerifyPingKt"
    args(((project.findProperty("servers") as String?)?.split(',') ?: emptyList<String>()))
    systemProperty("stdout.encoding", "UTF-8")
}

tasks.register<JavaExec>("verifyLaunch") {
    group = "youlauncher"
    description = "Реально скачивает и запускает latest release и 1.8.9, итог в docs/verification.md"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.VerifyLaunchKt"
    args(listOf(root.file((project.findProperty("report") as String?) ?: "docs/verification.md").asFile.absolutePath) + ((project.findProperty("versions") as String?)?.split(',') ?: emptyList()))
    systemProperty("youlauncher.home", (System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")) + "/YouLauncher-verify")
}

tasks.register<JavaExec>("renderBanner") {
    group = "youlauncher"
    description = "Баннер для страницы проекта в docs (светлый и тёмный)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.RenderBannerKt"
    args(root.dir("docs").asFile.absolutePath)
}

tasks.register<JavaExec>("benchNavigation") {
    group = "youlauncher"
    description = "Переходы между экранами в настоящем окне: пауза до первого кадра и худший кадр"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.BenchNavigationKt"
    systemProperty("youlauncher.home", layout.buildDirectory.dir("bench-home").get().asFile.absolutePath)
    systemProperty("youlauncher.demo", "true")
    jvmArgs("-XX:+UseSerialGC", "-Xms64m", "-Xmx768m", "-XX:MetaspaceSize=128m", "-XX:ReservedCodeCacheSize=128m", "-Dskiko.gpu.resourceCacheLimit=100663296")
    (project.findProperty("benchJvm") as String?)?.split(' ')?.let { jvmArgs(it) }
}

tasks.register<JavaExec>("renderSetup") {
    group = "youlauncher"
    description = "Снимки шагов установщика и удаления в docs/screens/setup"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "app.youlauncher.tools.RenderSetupKt"
    args(root.dir((project.findProperty("shotDir") as String?) ?: "docs/screens/setup").asFile.absolutePath)
    (project.findProperty("shotLang") as String?)?.let { systemProperty("youlauncher.shot.lang", it) }
}
