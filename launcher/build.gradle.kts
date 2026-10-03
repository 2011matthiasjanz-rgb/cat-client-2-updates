import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask

plugins {
    application
    id("com.gradleup.shadow") version "9.6.1"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "dev.catclient2.launcher.Launcher"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":friends-protocol"))
    implementation(project(":friends-server"))
    implementation(libs.minecraft.auth)

    implementation("com.formdev:flatlaf:3.6")
    implementation("com.formdev:flatlaf-extras:3.6")

    implementation("com.google.code.gson:gson:2.11.0")
}

val copyModJar = tasks.register<Copy>("copyModJar") {
    dependsOn(":remapJar")
    from(project(":").tasks.named("remapJar", RemapJarTask::class).map { it.archiveFile })
    into(layout.buildDirectory.dir("generated/modResources/mod"))
    rename { "cat-client-2.jar" }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(copyModJar)

    val propertyMap = mapOf(
        "minecraft_version" to libs.versions.minecraft.get(),
        "loader_version" to libs.versions.fabric.loader.get(),
        "launcher_version" to libs.versions.launcher.get()
    )
    inputs.properties(propertyMap)
    filesMatching("launcher.properties") {
        expand(propertyMap)
    }
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/modResources"))
        }
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
    archiveBaseName = "cat-client-2-launcher"
    mergeServiceFiles()
}

tasks.register<Copy>("copyDistTemplate") {
    dependsOn("shadowJar")
    from("dist-template")
    into(layout.buildDirectory.dir("libs"))
}

tasks.named("shadowJar") {
    finalizedBy("copyDistTemplate")
}

// The dist tasks copy build/libs, which copyDistTemplate writes into, so they have to wait for it.
// Without this Gradle refuses the build with an implicit-dependency validation error.
listOf("installDist", "distZip", "distTar", "startScripts",
    "shadowInstallDist", "shadowDistZip", "shadowDistTar", "startShadowScripts")
    .forEach { name ->
        tasks.matching { it.name == name }.configureEach { dependsOn("copyDistTemplate") }
    }

// update-checker depends on this project and therefore reads its build/libs/launcher.jar output too -
// same implicit-dependency issue as above, since copyDistTemplate also writes into that directory.
gradle.projectsEvaluated {
    project(":update-checker").tasks.matching { it.name == "compileJava" }
        .configureEach { dependsOn(":launcher:copyDistTemplate") }
}

// ---------------------------------------------------------------------- jpackage

// jpackage ships inside the JDK itself (since JDK 16) - no third-party Gradle plugin is needed,
// just an Exec task pointed at the toolchain's own jpackage binary.
val jpackageToolchain = javaToolchains.launcherFor(java.toolchain).map { it.executablePath }

val jpackageInputDir = layout.buildDirectory.dir("jpackage-input")
val copyJarForJpackage = tasks.register<Copy>("copyJarForJpackage") {
    dependsOn("shadowJar", "copyDistTemplate", ":update-checker:shadowJar")
    from(tasks.named<ShadowJar>("shadowJar").map { it.archiveFile })
    // The update-checker jar rides along as a second file in the same app/ folder jpackage creates -
    // it is launched separately (see InstallDirs/Autostart.java), not on the app's own classpath.
    from(project(":update-checker").tasks.named("shadowJar").map {
        (it as ShadowJar).archiveFile
    })
    into(jpackageInputDir)
}

val jpackageOutputDir = layout.buildDirectory.dir("jpackage")
val appIcon = file("installer/icon.ico")

tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "Builds a native Windows installer (.exe) that bundles its own Java runtime."
    dependsOn(copyJarForJpackage)

    val jpackageBinary = jpackageToolchain.get().asFile.parentFile.resolve(
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) "jpackage.exe" else "jpackage"
    )

    doFirst {
        delete(jpackageOutputDir)
        mkdir(jpackageOutputDir)
    }

    // jpackage's default jlink-built runtime has no java/javaw executable (only the native launcher
    // it generates) - a plain "javaw -jar someOtherJar" against that runtime does not exist. The
    // update-checker therefore needs its own native launcher sharing this same install+runtime,
    // which --add-launcher creates from the properties file below.
    val updateCheckerLauncherProps = file("installer/update-checker-launcher.properties")

    val args = mutableListOf(
        "--type", "exe",
        "--name", "Cat Client 2",
        "--app-version", libs.versions.launcher.get(),
        "--vendor", "Cat Client 2",
        "--input", jpackageInputDir.get().asFile.absolutePath,
        "--main-jar", "cat-client-2-launcher.jar",
        "--main-class", "dev.catclient2.launcher.Launcher",
        "--dest", jpackageOutputDir.get().asFile.absolutePath,
        "--win-per-user-install",
        "--win-dir-chooser",
        "--win-menu",
        "--win-shortcut",
        "--win-upgrade-uuid", "6f1d2c8e-6b34-4a0a-9f7a-2b1f4c9d8e3a",
        "--add-launcher", "Cat Client 2 Update Checker=${updateCheckerLauncherProps.absolutePath}",
        // jpackage's default jlink options include --strip-native-commands, which deletes
        // java.exe/javaw.exe from the bundled runtime entirely. The launcher itself needs a real
        // java executable at runtime to spawn Minecraft and the Fabric installer as subprocesses
        // (see GameLauncher.javaBinary() / FabricInstaller.install(), both of which shell out to
        // "<runtime>/bin/java.exe") - so that one strip option must be dropped, keeping the other
        // three (safe: they only remove debug symbols/docs, nothing executable).
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files"
    )
    if (appIcon.exists()) args += listOf("--icon", appIcon.absolutePath)

    commandLine(listOf(jpackageBinary.absolutePath) + args)
}
