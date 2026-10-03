package dev.catclient2.launcher.launch;

import dev.catclient2.launcher.auth.AccountSession;
import dev.catclient2.launcher.install.FabricVersionMeta;
import dev.catclient2.launcher.install.VersionMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GameLauncher {
    private final Path instanceDir;

    public GameLauncher(Path instanceDir) {
        this.instanceDir = instanceDir;
    }

    public Process launch(String vanillaVersion, VersionMeta vanillaMeta, String fabricVersionId,
                           AccountSession session, int memoryGb) throws IOException {
        return launch(vanillaVersion, vanillaMeta, fabricVersionId, session, memoryGb, List.of());
    }

    /**
     * @param extraGameArgs appended to the vanilla argument list, used to auto-connect to a friend's
     *                      world (see {@code JoinInstaller#quickPlayArgs}).
     */
    public Process launch(String vanillaVersion, VersionMeta vanillaMeta, String fabricVersionId,
                           AccountSession session, int memoryGb, List<String> extraGameArgs) throws IOException {
        Path fabricJsonFile = instanceDir.resolve("versions").resolve(fabricVersionId).resolve(fabricVersionId + ".json");
        FabricVersionMeta fabricMeta = FabricVersionMeta.readFrom(fabricJsonFile);

        List<Path> classpath = ClasspathBuilder.build(instanceDir, vanillaVersion, vanillaMeta, fabricMeta);

        // LWJGL/JNA extract their natives here; the vanilla jvm args point at this directory.
        Files.createDirectories(ArgumentBuilder.nativesDir(instanceDir, vanillaVersion));

        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.addAll(ArgumentBuilder.buildJvmArgs(instanceDir, vanillaVersion, classpath, vanillaMeta, fabricMeta, memoryGb));
        command.add(fabricMeta.mainClass);
        command.addAll(ArgumentBuilder.buildGameArgs(vanillaMeta, fabricMeta, session, instanceDir, vanillaVersion));
        command.addAll(extraGameArgs);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(instanceDir.toFile());
        builder.inheritIO();

        return builder.start();
    }

    private static String javaBinary() {
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", exe).toString();
    }
}
