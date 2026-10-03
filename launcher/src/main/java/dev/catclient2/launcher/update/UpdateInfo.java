package dev.catclient2.launcher.update;

/** A newer launcher release found on GitHub, ready to be downloaded and installed. */
public record UpdateInfo(String version, String downloadUrl, String releaseNotesUrl) {
}
