package dev.catclient2.launcher.update;

import dev.catclient2.launcher.install.DownloadUtil;
import dev.catclient2.launcher.util.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Downloads the new release's installer and runs it silently to upgrade the current install in
 * place.
 *
 * <p>The launcher is distributed as a jpackage-built Windows installer (a WiX Burn bundle, built by
 * the {@code :launcher:jpackage} Gradle task with a fixed {@code --win-upgrade-uuid}), not a bare
 * jar - WiX Burn bundles universally support {@code -q} for a fully silent, unattended install/
 * upgrade. Because every release shares the same upgrade UUID and is a per-user install (no
 * elevation prompt), running the new installer silently replaces the old version without the user
 * seeing any wizard.
 */
public class UpdateInstaller {
    private UpdateInstaller() {
    }

    /**
     * Blocks on network I/O - call off the Swing event thread. On success this method does not
     * return: it calls {@link System#exit} once the installer has been launched, since the running
     * launcher's own files are about to be replaced out from under it.
     */
    public static void install(UpdateInfo info) throws IOException, InterruptedException {
        Path updateDir = OperatingSystem.instanceRoot().resolve("update");
        Files.createDirectories(updateDir);
        Path installer = updateDir.resolve("cat-client-2-installer.exe");

        DownloadUtil.download(info.downloadUrl(), installer, null);

        ProcessBuilder builder = new ProcessBuilder(List.of(installer.toString(), "-q"));
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.start();

        System.exit(0);
    }
}
