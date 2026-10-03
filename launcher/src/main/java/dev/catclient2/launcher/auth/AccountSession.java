package dev.catclient2.launcher.auth;

import java.util.UUID;

public record AccountSession(String username, UUID uuid, String accessToken) {
}
