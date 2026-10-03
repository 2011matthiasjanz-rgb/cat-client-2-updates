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
