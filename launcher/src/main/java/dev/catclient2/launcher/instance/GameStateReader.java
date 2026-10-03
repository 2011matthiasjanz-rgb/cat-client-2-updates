package dev.catclient2.launcher.instance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the state file the Fabric mod writes into the game directory ({@code
 * dev.catclient2.client.ClientSessionState}), which reports whether the client is really inside a
 * world.
 *
 * <p>This is the only trustworthy "is the player in game" signal available to the launcher: it runs
 * in a separate JVM and cannot touch {@code MinecraftClient}. The LAN port scraped from
 * {@code latest.log} is deliberately not used for this - that is address information, and it says
 * nothing about being in a world (a singleplayer world that was never opened to LAN has no port at
 * all).
 *
 * <p>Only primitives are read; nothing here puts a {@link Path} into a Gson-serialised type.
 */
public final class GameStateReader {
    /** Must match {@code ClientSessionState.FILE_NAME} on the mod side. */
    public static final String FILE_NAME = "catclient2-session.json";

    /**
     * A heartbeat older than this means the game is gone or frozen. Deliberately generous: a
     * paused singleplayer world stops ticking the client, so the mod's heartbeat does not advance
     * while the pause menu is open and a tighter window would wrongly unpublish a live session.
     */
    private static final long STALE_MILLIS = 120_000;

    /** Tolerance for a state file written "in the future" by a clock that is slightly ahead. */
    private static final long CLOCK_SKEW_MILLIS = 10_000;

    public enum Mode {
        NOT_IN_GAME,
        SINGLEPLAYER,
        MULTIPLAYER
    }

    /**
     * @param inGame    the effective state (already staleness-checked)
     * @param mode      the raw reported mode, {@link Mode#NOT_IN_GAME} when stale or unreadable
     * @param timestamp the mod's last heartbeat
     */
    public record State(boolean inGame, Mode mode, long timestamp) {
        /** A world the launcher can actually hand to a friend is a local one opened to LAN. */
        public boolean lanHost() {
            return inGame && mode == Mode.SINGLEPLAYER;
        }
    }

    private static final State NOT_IN_GAME = new State(false, Mode.NOT_IN_GAME, 0);

    private final Path file;

    public GameStateReader(Path instanceDir) {
        this.file = instanceDir.resolve(FILE_NAME);
    }

    public State read() {
        if (!Files.isRegularFile(file)) return NOT_IN_GAME;

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            JsonElement raw = json.get("mode");
            Mode mode = parseMode(raw == null || raw.isJsonNull() ? null : raw.getAsString());
            long timestamp = json.has("timestamp") ? json.get("timestamp").getAsLong() : 0;

            if (mode == Mode.NOT_IN_GAME) return new State(false, mode, timestamp);

            // inGame is taken from the mode, so a hand-edited file cannot claim a world that the
            // mod never reported.
            if (isStale(timestamp)) return NOT_IN_GAME;
            return new State(true, mode, timestamp);
        } catch (IOException | RuntimeException e) {
            // A half-written or corrupt file means "no information", never "in game".
            return NOT_IN_GAME;
        }
    }

    private static Mode parseMode(String raw) {
        if (raw == null) return Mode.NOT_IN_GAME;

        try {
            return Mode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mode.NOT_IN_GAME;
        }
    }

    private static boolean isStale(long timestamp) {
        if (timestamp <= 0) return true;

        long age = System.currentTimeMillis() - timestamp;
        return age > STALE_MILLIS || age < -CLOCK_SKEW_MILLIS;
    }
}