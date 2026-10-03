package dev.catclient2.launcher.update;

import dev.catclient2.launcher.install.DownloadUtil;
import dev.catclient2.launcher.util.OperatingSystem;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

/**
 * Downloads a new launcher jar and replaces the one currently running with it.
 *
 * <p>A running JVM holds its own jar file open, and on Windows an open file cannot be overwritten
 * or deleted - so the replace has to happen from a separate process, after this one has exited. The
 * approach: download the new jar, write a tiny OS-native script that waits for this process to die,
 * moves the new jar into place, and relaunches it, then start that script detached and exit.
 */
public class UpdateInstaller {
    private UpdateInstaller() {
    }

    /**
     * Blocks on network I/O - call off the Swing event thread. On success this method does not
     * return: it calls {@link System#exit} once the relauncher is safely started. Throws if the
     * download fails or the running jar's own location cannot be determined, leaving the current
     * launcher untouched and still running.
     */
    public static void install(UpdateInfo info) throws IOException, InterruptedException {
        Path currentJar = currentJarPath();
        Path updateDir = OperatingSystem.instanceRoot().resolve("update");
        Path newJar = updateDir.resolve("cat-client-2-launcher-new.jar");

        DownloadUtil.download(info.downloadUrl(), newJar, null);

        long pid = ProcessHandle.current().pid();
        Path script = writeRelaunchScript(updateDir, pid, currentJar, newJar);
        launchDetached(script);

        System.exit(0);
    }

    private static Path currentJarPath() throws IOException {
        try {
            Path path = Path.of(UpdateInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(path) || !path.toString().endsWith(".jar")) {
                throw new IOException("Not running from a jar (got " + path + ") - cannot self-update in this environment (e.g. an IDE run configuration)");
            }
            return path;
        } catch (URISyntaxException e) {
            throw new IOException("Could not determine the running jar's own location", e);
        }
    }

    private static Path writeRelaunchScript(Path updateDir, long pid, Path currentJar, Path newJar) throws IOException {
        Files.createDirectories(updateDir);

        return switch (OperatingSystem.get()) {
            case WINDOWS -> writeWindowsScript(updateDir, pid, currentJar, newJar);
            default -> writeUnixScript(updateDir, pid, currentJar, newJar);
        };
    }

    private static Path writeWindowsScript(Path updateDir, long pid, Path currentJar, Path newJar) throws IOException {
        Path script = updateDir.resolve("apply-update.bat");
        // Values are embedded directly rather than passed as script arguments: simpler to get right
        // than correctly quoting paths-with-spaces (e.g. "Cat client 2") through cmd.exe's %1/%2/%3.
        String content = """
            @echo off
            :wait
            tasklist /fi "PID eq %d" | find "%d" >nul
            if not errorlevel 1 (
                timeout /t 1 /nobreak >nul
                goto wait
            )
            move /y "%s" "%s" >nul
            start "" javaw -jar "%s"
            del "%%~f0"
            """.formatted(pid, pid, newJar, currentJar, currentJar);
        Files.writeString(script, content, StandardCharsets.UTF_8);
        return script;
    }

    private static Path writeUnixScript(Path updateDir, long pid, Path currentJar, Path newJar) throws IOException {
        Path script = updateDir.resolve("apply-update.sh");
        String content = """
            #!/bin/sh
            while kill -0 %d 2>/dev/null; do
                sleep 1
            done
            mv -f "%s" "%s"
            java -jar "%s" &
            rm -- "$0"
            """.formatted(pid, newJar, currentJar, currentJar);
        Files.writeString(script, content, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(script, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Not a POSIX filesystem - the OS default permissions are used instead.
        }
        return script;
    }

    private static void launchDetached(Path script) throws IOException {
        // "start" (Windows) / a plain background shell invocation detaches the script from this
        // JVM's process tree, so it keeps running after System.exit() below ends this process.
        List<String> command = OperatingSystem.get() == OperatingSystem.WINDOWS
            ? List.of("cmd", "/c", "start", "\"\"", "/min", script.toString())
            : List.of("/bin/sh", script.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.start();
    }
}
