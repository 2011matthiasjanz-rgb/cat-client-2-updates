package dev.catclient2.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LauncherConfig {
    public static final String MINECRAFT_VERSION;
    public static final String LOADER_VERSION;
    public static final String LAUNCHER_VERSION;

    static {
        Properties props = new Properties();
        try (InputStream in = LauncherConfig.class.getResourceAsStream("/launcher.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }

        MINECRAFT_VERSION = props.getProperty("minecraft_version", "1.21.11");
        LOADER_VERSION = props.getProperty("loader_version", "0.18.2");
        LAUNCHER_VERSION = props.getProperty("launcher_version", "1.0.0");
    }

    private LauncherConfig() {
    }
}
