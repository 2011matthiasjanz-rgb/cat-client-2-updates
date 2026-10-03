package dev.catclient2.launcher.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public enum OperatingSystem {
    WINDOWS, OSX, LINUX, UNKNOWN;

    public static OperatingSystem get() {
        String name = System.getProperty("os.name", "").toLowerCase();
        if (name.contains("win")) return WINDOWS;
        if (name.contains("mac") || name.contains("darwin")) return OSX;
        if (name.contains("nux") || name.contains("nix")) return LINUX;
        return UNKNOWN;
    }

    public static Path instanceRoot() {
        return switch (get()) {
            case WINDOWS -> Paths.get(System.getenv("APPDATA"), "CatClient2");
            case OSX -> Paths.get(System.getProperty("user.home"), "Library", "Application Support", "CatClient2");
            default -> Paths.get(System.getProperty("user.home"), ".catclient2");
        };
    }
}
