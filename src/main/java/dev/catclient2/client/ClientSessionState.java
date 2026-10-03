/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package dev.catclient2.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Publishes whether the client is actually inside a world, so the launcher never has to guess.
 *
 * <p>The launcher runs in its own JVM and cannot see {@code MinecraftClient}, and the only signal
 * it used to scrape out of the game was the "Local game hosted on port" line in {@code latest.log}
 * - which is address information, not a state signal, and is absent for a singleplayer world that
 * was never opened to LAN. This class writes the authoritative state to a small JSON file in the
 * game directory instead, derived from the real client fields:
 * {@code world != null && player != null && getNetworkHandler() != null}.
 *
 * <p>The file holds primitives only - no Minecraft objects and deliberately no {@link Path}, so it
 * can never trip Gson's reflective access on a {@code sun.nio.fs} type.
 */
public final class ClientSessionState {
    /** Name of the state file inside the game directory. Mirrored by the launcher's reader. */
    public static final String FILE_NAME = "catclient2-session.json";

    /** How often the file is rewritten while nothing changes, so the launcher can spot a dead game. */
    private static final long HEARTBEAT_MILLIS = 2_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Mode {
        NOT_IN_GAME,
        SINGLEPLAYER,
        MULTIPLAYER
    }

    /** On-disk shape. Intentionally primitive-only; keep it in sync with the launcher's reader. */
    public record State(boolean inGame, String mode, long timestamp) {
    }

    private static volatile Mode lastMode;
    private static volatile long lastWrite;

    private ClientSessionState() {
    }

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(ClientSessionState.class);

        // A crash or a closed window must not leave a "still in a world" file behind.
        Runtime.getRuntime().addShutdownHook(new Thread(ClientSessionState::writeNotInGame, "catclient2-state-exit"));
    }

    public static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve(FILE_NAME);
    }

    // ------------------------------------------------------------------- events

    @EventHandler
    private static void onGameJoined(GameJoinedEvent event) {
        write(detect());
    }

    @EventHandler
    private static void onGameLeft(GameLeftEvent event) {
        writeNotInGame();
    }

    @EventHandler
    private static void onTick(TickEvent.Post event) {
        Mode mode = detect();
        long now = System.currentTimeMillis();

        // Rewrite immediately on a state change, otherwise just often enough to stay fresh.
        if (mode == lastMode && now - lastWrite < HEARTBEAT_MILLIS) return;
        write(mode);
    }

    // ------------------------------------------------------------------ private

    /**
     * The single source of truth for "is the player in a world". Deliberately ignores the window
     * title and the open-to-LAN port: neither says anything about being in a world, and the LAN
     * port is address information the launcher derives from the game log on its own.
     */
    private static Mode detect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return Mode.NOT_IN_GAME;
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return Mode.NOT_IN_GAME;

        return mc.isIntegratedServerRunning() ? Mode.SINGLEPLAYER : Mode.MULTIPLAYER;
    }

    private static void writeNotInGame() {
        write(Mode.NOT_IN_GAME);
    }

    private static synchronized void write(Mode mode) {
        State state = new State(mode != Mode.NOT_IN_GAME, mode.name(), System.currentTimeMillis());

        try {
            Path target = file();
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

            Files.createDirectories(target.getParent());
            // Write-then-move so the launcher never reads a half-written file.
            Files.writeString(tmp, GSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);

            lastMode = mode;
            lastWrite = state.timestamp();
        } catch (IOException | RuntimeException e) {
            System.err.println("[catclient2] Could not write the session state file: " + e);
        }
    }
}