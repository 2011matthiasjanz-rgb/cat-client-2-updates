package dev.catclient2.launcher.instance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ModJarProvisioner {
    private static final String MOD_JAR_NAME = "cat-client-2.jar";
    private static final String MOD_JAR_PREFIX = "cat-client-2";

    /**
     * Copies the launcher's bundled mod jar (packaged as a resource by the build's copyModJar
     * task) into the instance's mods/ folder, replacing any previous cat-client-2*.jar found there.
     */
    public static void provision(Path instanceDir) throws IOException {
        Path modsDir = InstanceManager.modsDir(instanceDir);
        Files.createDirectories(modsDir);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, MOD_JAR_PREFIX + "*.jar")) {
            for (Path old : stream) {
                Files.delete(old);
            }
        }

        try (InputStream in = ModJarProvisioner.class.getResourceAsStream("/mod/" + MOD_JAR_NAME)) {
            if (in == null) {
                throw new IOException("Bundled mod jar resource /mod/" + MOD_JAR_NAME + " not found - was the launcher built with :launcher:copyModJar?");
            }
            Files.copy(in, modsDir.resolve(MOD_JAR_NAME), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
