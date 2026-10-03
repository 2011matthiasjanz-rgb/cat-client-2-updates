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
     * Downloads the installer and starts it silently, returning the running installer process.
     * Blocks on network I/O - call off the Swing event thread. The caller is responsible for exiting
     * its own process once it is done with the returned {@link Process} (e.g. immediately, if it is
     * about to be replaced anyway, or after {@link Process#waitFor()} if it wants to report
     * completion first) - a launcher whose own files are being overwritten should not keep running.
     */
    public static Process install(UpdateInfo info) throws IOException, InterruptedException {
        Path updateDir = OperatingSystem.instanceRoot().resolve("update");
        Files.createDirectories(updateDir);
        Path installer = updateDir.resolve("cat-client-2-installer.exe");

        DownloadUtil.download(info.downloadUrl(), installer, null);

        ProcessBuilder builder = new ProcessBuilder(List.of(installer.toString(), "-q"));
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }
}
