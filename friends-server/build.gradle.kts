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
    mainClass = "dev.catclient2.friends.server.FriendsServerMain"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":friends-protocol"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
    archiveBaseName = "cat-friends-server"
    mergeServiceFiles()
}

tasks.named("build") {
    dependsOn("shadowJar")
}
