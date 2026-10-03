import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

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
    mainClass = "dev.catclient2.updatechecker.UpdateCheckerMain"
}

repositories {
    mavenCentral()
}

dependencies {
    // Reuses the launcher's own UpdateChecker/UpdateInstaller/LauncherConfig rather than
    // duplicating that logic - this is a separate *process* from the launcher, not a separate
    // implementation of the update check.
    implementation(project(":launcher"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
    archiveBaseName = "cat-client-2-update-checker"
    mergeServiceFiles()
}

tasks.named("build") {
    dependsOn("shadowJar")
}
