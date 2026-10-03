package dev.catclient2.launcher.update;

import dev.catclient2.launcher.util.OperatingSystem;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Registers the small update-checker program (see the separate {@code update-checker} Gradle module)
 * to run once at Windows login, so a new release can be noticed without the full launcher needing to
 * be open or resident in the background. Idempotent and best-effort: called once at every launcher
 * start, it only writes the shortcut if it is missing or stale, and never fails launcher startup.
 */
public class StartupRegistration {
    private StartupRegistration() {
    }

    public static void ensureRegistered() {
        if (OperatingSystem.get() != OperatingSystem.WINDOWS) return;

        try {
            Path installDir = installDir();
            if (installDir == null) return; // Not running from an installed location (e.g. dev/IDE run).

            Path javaw = installDir.resolve("runtime").resolve("bin").resolve("javaw.exe");
            Path updateCheckerJar = installDir.resolve("app").resolve("cat-client-2-update-checker.jar");
            if (!Files.isRegularFile(javaw) || !Files.isRegularFile(updateCheckerJar)) return;

            Path startupFolder = Path.of(System.getenv("APPDATA"),
                "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
            Path shortcut = startupFolder.resolve("Cat Client 2 Update Checker.lnk");
            if (Files.exists(shortcut)) return;

            createShortcut(shortcut, javaw, updateCheckerJar);
        } catch (Exception e) {
            System.err.println("[cat-client] Could not register the startup update checker: " + e.getMessage());
        }
    }

    /** The jpackage install root (the folder containing app/, runtime/), derived from this jar's own path. */
    private static Path installDir() throws IOException {
        try {
            Path jarPath = Path.of(StartupRegistration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path appDir = jarPath.getParent();
            if (appDir == null || !"app".equals(appDir.getFileName().toString())) return null;
            return appDir.getParent();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static void createShortcut(Path shortcut, Path target, Path jar) throws IOException, InterruptedException {
        Files.createDirectories(shortcut.getParent());

        String script = """
            $shell = New-Object -ComObject WScript.Shell
            $s = $shell.CreateShortcut('%s')
            $s.TargetPath = '%s'
            $s.Arguments = '-jar "%s"'
            $s.WindowStyle = 7
            $s.Description = 'Checks for Cat Client 2 updates'
            $s.Save()
            """.formatted(shortcut, target, jar);

        Path scriptFile = Files.createTempFile("cat-client-2-startup-shortcut", ".ps1");
        try {
            Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", scriptFile.toString()
            ).redirectErrorStream(true).start();
            process.waitFor();
        } finally {
            Files.deleteIfExists(scriptFile);
        }
    }
}
