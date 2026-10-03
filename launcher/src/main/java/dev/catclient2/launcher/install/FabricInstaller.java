package dev.catclient2.launcher.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class FabricInstaller {
    // Verify at implementation time whether a newer stable installer version is available
    // (check https://meta.fabricmc.net/v2/versions/installer) before relying on this indefinitely.
    private static final String INSTALLER_VERSION = "1.1.2";
    private static final String INSTALLER_URL =
        "https://maven.fabricmc.net/net/fabricmc/fabric-installer/" + INSTALLER_VERSION + "/fabric-installer-" + INSTALLER_VERSION + ".jar";

    private final Path instanceDir;

    public FabricInstaller(Path instanceDir) {
        this.instanceDir = instanceDir;
    }

    /**
     * Downloads the official Fabric installer and runs it in CLI "client" mode to install
     * the given loader version for the given Minecraft version directly into this instance.
     *
     * @return the generated version id (e.g. "fabric-loader-0.18.2-1.21.11") that should be
     *         used as the launch target, matching what the installer wrote under versions/.
     */
    public String install(String minecraftVersion, String loaderVersion, Consumer<String> onStatus) throws IOException, InterruptedException {
        onStatus.accept("Downloading Fabric installer...");

        Path installerJar = instanceDir.resolve("fabric-installer-" + INSTALLER_VERSION + ".jar");
        DownloadUtil.download(INSTALLER_URL, installerJar, null);

        onStatus.accept("Installing Fabric Loader...");

        Path javaBinary = Path.of(System.getProperty("java.home"), "bin", javaExecutableName());

        ProcessBuilder builder = new ProcessBuilder(
            javaBinary.toString(),
            "-jar", installerJar.toString(),
            "client",
            "-dir", instanceDir.toString(),
            "-mcversion", minecraftVersion,
            "-loader", loaderVersion,
            "-noprofile"
        );
        builder.redirectErrorStream(true);

        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (var reader = process.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Fabric installer exited with code " + exitCode + ":\n" + output);
        }

        return "fabric-loader-" + loaderVersion + "-" + minecraftVersion;
    }

    private static String javaExecutableName() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
    }
}
