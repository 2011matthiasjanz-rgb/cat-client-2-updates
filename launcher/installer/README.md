# Installer assets

Drop an `icon.ico` file in this folder to give the Windows installer and the installed app a custom
icon. It is picked up automatically by the `:launcher:jpackage` Gradle task (`launcher/build.gradle.kts`)
if present; without it, jpackage falls back to its own default icon.
