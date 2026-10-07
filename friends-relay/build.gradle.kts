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
    mainClass = "dev.catclient2.friends.relay.RelayServerMain"
}

repositories {
    mavenCentral()
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
    archiveBaseName = "cat-friends-relay"
    mergeServiceFiles()
}

tasks.named("build") {
    dependsOn("shadowJar")
}
