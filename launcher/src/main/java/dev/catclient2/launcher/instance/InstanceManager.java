package dev.catclient2.launcher.instance;

import dev.catclient2.launcher.util.OperatingSystem;

import java.nio.file.Path;
import java.util.Locale;

public class InstanceManager {
    public static Path defaultInstanceDir() {
        return OperatingSystem.instanceRoot().resolve("instances").resolve("default");
    }

    public static Path modsDir(Path instanceDir) {
        return instanceDir.resolve("mods");
    }

    /**
     * A separate instance per friend. Joining often means a different Minecraft version and a
     * different mod set, and the player still wants their own instance untouched.
     */
    public static Path friendInstanceDir(String hostUuid, String hostName) {
        String name = hostName == null ? "friend" : hostName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (name.isBlank()) name = "friend";

        String suffix = "";
        if (hostUuid != null && hostUuid.length() >= 8) suffix = "-" + hostUuid.substring(0, 8).toLowerCase(Locale.ROOT);

        return OperatingSystem.instanceRoot().resolve("instances").resolve("friend-" + name + suffix);
    }
}
