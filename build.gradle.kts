import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Base64
import java.util.concurrent.TimeUnit

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

group = "app.youlauncher"
// Версию релиза можно передать из тега: -PappVersion=v1.2.0 (так делает GitHub Actions).
version = (findProperty("appVersion") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "1.1.0"

// Каталог сборки при не-ASCII пути проекта переносится в settings.gradle.kts (см. комментарий там).

object Versions {
    const val material3 = "1.12.0-alpha03"
    const val compose = "1.12.2"
    const val materialKolor = "5.0.1"
    const val ktor = "3.6.0"
    const val coroutines = "1.11.0"
    const val serialization = "1.12.0"
    const val jna = "5.19.1"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:${Versions.material3}")
    implementation("org.jetbrains.compose.components:components-resources:${Versions.compose}")
    implementation("com.materialkolor:material-kolor:${Versions.materialKolor}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${Versions.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
    implementation("io.ktor:ktor-client-core:${Versions.ktor}")
    implementation("io.ktor:ktor-client-java:${Versions.ktor}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktor}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}")
    // JNA: Windows Credential Manager для токенов и скругление углов окна без рамки через DWM.
    implementation("net.java.dev.jna:jna-platform:${Versions.jna}")
    // Ktor пишет журнал через SLF4J; свои журналы у лаунчера отдельные, поэтому подключаем пустую реализацию.
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
    testImplementation("io.ktor:ktor-client-mock:${Versions.ktor}")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

// Ключ CurseForge лаунчера встраивается в сборку из переменной окружения YOULAUNCHER_CURSEFORGE_KEY
// на машине, где собирается приложение. В репозиторий и в кэш сборки Gradle он не попадает.
val buildSecretsDir = layout.buildDirectory.dir("generated/build-secrets")
val generateBuildSecrets by tasks.registering {
    group = "youlauncher"
    description = "Встраивает ключ CurseForge из переменной окружения в ресурсы сборки"
    outputs.dir(buildSecretsDir)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    // Версия и репозиторий обновлений тоже идут в ресурс сборки (одна версия для jar, MSI и «О программе»).
    val appVersion = project.version.toString()
    val updateRepo = (project.findProperty("updateRepo") as String?) ?: System.getenv("YOULAUNCHER_UPDATE_REPO")
    val msClientId = (project.findProperty("msClientId") as String?) ?: System.getenv("YOULAUNCHER_MS_CLIENT_ID")
    inputs.property("version", appVersion)
    doLast {
        val dir = buildSecretsDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        val lines = mutableListOf("version=$appVersion")
        updateRepo?.trim()?.takeIf { Regex("[\\w.-]+/[\\w.-]+").matches(it) }?.let { lines += "updateRepo=$it" }
        // ID приложения Azure для входа через Microsoft: общедоступный клиент, не секрет. Без него кнопки входа нет.
        msClientId?.trim()?.takeIf { Regex("[0-9a-fA-F-]{36}").matches(it) }?.let { lines += "msClientId=$it" }
        // Переменная прежнего названия тоже принимается, чтобы не задавать ключ заново.
        val key = (System.getenv("YOULAUNCHER_CURSEFORGE_KEY") ?: System.getenv("TERRANE_CURSEFORGE_KEY"))?.trim().orEmpty()
        // Сообщения сборки на ASCII: консоль PowerShell по умолчанию не в UTF-8.
        if (key.isEmpty()) {
            logger.lifecycle("CurseForge key NOT embedded: YOULAUNCHER_CURSEFORGE_KEY is not set in this terminal")
        } else {
            // Маска только прячет ключ от поиска по тексту в jar, это не шифрование.
            val mask = "youlauncher-build".toByteArray()
            val bytes = key.toByteArray().mapIndexed { i, b -> (b.toInt() xor mask[i % mask.size].toInt()).toByte() }.toByteArray()
            lines += "curseforge=" + Base64.getEncoder().encodeToString(bytes)
            logger.lifecycle("CurseForge key embedded into the build")
        }
        File(dir, "youlauncher-build.properties").writeText(lines.joinToString("\n") + "\n")
    }
}

// NOTICE со списком лицензий кладётся в ресурсы приложения (экран «О программе»).
tasks.processResources {
    from(layout.projectDirectory.file("NOTICE")) { rename { "NOTICE.txt" } }
    from(generateBuildSecrets)
}

tasks.test {
    useJUnitPlatform()
    // Тесты не ходят в сеть и не трогают реальный AppData.
    systemProperty("youlauncher.home", layout.buildDirectory.dir("test-home").get().asFile.absolutePath)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "app.youlauncher.resources"
    generateResClass = always
}

// Иконка и лицензия копируются в каталог сборки, чтобы путь к ним в @argfile jpackage был ASCII.
val stagePackaging by tasks.registering(Copy::class) {
    from(layout.projectDirectory.file("packaging/youlauncher.ico"), layout.projectDirectory.file("NOTICE"))
    into(layout.buildDirectory.dir("packaging"))
    rename("NOTICE", "NOTICE.txt")
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    dependsOn(stagePackaging)
}

// Образ рантайма не кладём в кэш сборки: восстановленная из кэша пустая папка ломала упаковку.
tasks.matching { it.name == "createRuntimeImage" }.configureEach { outputs.cacheIf { false } }

val jdk21Home: Provider<String> = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}.map { it.metadata.installationPath.asFile.absolutePath }

compose.desktop {
    application {
        mainClass = "app.youlauncher.MainKt"
        javaHome = jdk21Home.get()
        // Память: живых данных у лаунчера десятки мегабайт, поэтому сборщик Serial (у G1 около 50 МБ служебных структур),
        // куча растёт по мере нужды и отдаётся системе после сборки, потолок 768 МБ. Кэш GPU-ресурсов Skia 96 МБ.
        // Отзывчивость: стартовая куча 64 МБ и пороги метаданных классов и кэша кода с запасом, иначе в первые минуты
        // идут полные сборки мусора по 50–80 мс («Metadata/CodeCache GC Threshold»). Кэш классов (AppCDS) собирается
        // на компьютере пользователя (см. ClassCache); без архива Java работает как обычно.
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8", "-Xss2m",
            "-XX:+UseSerialGC", "-Xms64m", "-Xmx768m", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=25",
            "-XX:MetaspaceSize=128m", "-XX:ReservedCodeCacheSize=128m",
            // Без -Xshare:auto (оно и так по умолчанию): иначе оно перебило бы -Xshare:dump при сборке архива.
            // Прямой слэш: jpackage читает опции из файла аргументов, где обратный слэш экранирует символ.
            "-XX:SharedArchiveFile=\$APPDIR/youlauncher.jsa", "-Xlog:cds=off", "-Xlog:cds+dynamic=off",
            "-Dskiko.gpu.resourceCacheLimit=100663296",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "YouLauncher"
            // Установщики Windows принимают только числовую версию, суффикс пре-релиза (-beta.1) отбрасывается.
            packageVersion = project.version.toString().substringBefore('-')
            // Только ASCII: эти строки уходят в @argfile jpackage.
            description = "YouLauncher - unofficial Minecraft Java Edition launcher"
            vendor = "YouLauncher"
            copyright = "Unofficial launcher, not affiliated with Mojang or Microsoft"
            licenseFile = layout.buildDirectory.file("packaging/NOTICE.txt").get().asFile
            // Встроенная Java внутри приложения: пользователю ничего ставить не нужно.
            includeAllModules = true
            windows {
                iconFile = layout.buildDirectory.file("packaging/youlauncher.ico").get().asFile
                shortcut = true
                menu = true
                menuGroup = "YouLauncher"
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "5f3c9b0e-7a51-4c1e-9c7e-3d2a6b8f41a7"
            }
        }
        buildTypes.release.proguard {
            isEnabled = false
        }
    }
}

// Портативная сборка: после `gradlew.bat exportApp` в папке проекта появится YouLauncher-App\YouLauncher.exe,
// его можно запускать двойным кликом или перенести на другой компьютер вместе с папкой.
// Список классов для кэша классов Java: собранная программа один раз проходит по всем экранам на демо-данных
// (на экране мелькнёт окно примерно на полминуты) и записывает загруженные классы в app/classes.lst.
// Сам архив собирается уже у пользователя (пути в нём абсолютные). -PnoCds пропускает шаг.
val cdsClassList by tasks.registering {
    group = "distribution"
    description = "Готовит список классов для кэша классов Java (AppCDS) тренировочным запуском программы"
    dependsOn("createDistributable")
    onlyIf { !project.hasProperty("noCds") && System.getProperty("os.name").startsWith("Windows") }
    val dist = layout.buildDirectory.dir("compose/binaries/main/app/YouLauncher")
    doLast {
        val dir = dist.get().asFile
        val list = File(dir, "app/classes.lst").apply { delete() }
        val train = File(dir, "cds-train").apply { deleteRecursively() }
        // Пути относительно папки программы: JAVA_TOOL_OPTIONS не переносит пробелы и кириллицу.
        val pb = ProcessBuilder(File(dir, "YouLauncher.exe").path).directory(dir).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.environment()["JAVA_TOOL_OPTIONS"] = "-XX:DumpLoadedClassList=app\\classes.lst -Dyoulauncher.home=cds-train\\home " +
            "-Dyoulauncher.demo=true -Dyoulauncher.benchNavigation=cds-train\\report.txt"
        val p = pb.start()
        val done = p.waitFor(4, TimeUnit.MINUTES)
        if (!done) p.destroyForcibly()
        val report = File(train, "report.txt")
        if (done && list.length() > 0 && report.exists()) {
            logger.lifecycle("CDS class list: ${list.readLines().size} classes")
        } else {
            list.delete()
            logger.warn("CDS class list NOT created (training run failed), the app will start without the class cache")
        }
        train.deleteRecursively()
    }
}

val exportApp by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Собирает портативное приложение в папку YouLauncher-App (YouLauncher.exe со встроенной Java)"
    dependsOn("createDistributable", cdsClassList)
    from(layout.buildDirectory.dir("compose/binaries/main/app/YouLauncher"))
    into(layout.projectDirectory.dir("YouLauncher-App"))
}

tasks.register<Zip>("exportZip") {
    group = "distribution"
    description = "Упаковывает портативную сборку в YouLauncher-<версия>-windows.zip (этот файл ищет автообновление в релизах)"
    dependsOn(exportApp)
    from(layout.projectDirectory.dir("YouLauncher-App")) { into("YouLauncher-App") }
    archiveFileName = "YouLauncher-${project.version}-windows.zip"
    destinationDirectory = layout.projectDirectory
}
