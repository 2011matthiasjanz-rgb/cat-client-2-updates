package dev.catclient2.friends.server;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short lived bearer tokens handed out after a successful Mojang check. The Minecraft access token
 * itself is never stored, so a leaked database cannot be replayed against Mojang.
 */
public class SessionTokens {
    private static final long VALID_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    public String issue(String uuid) {
        purge();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Entry(uuid, System.currentTimeMillis() + VALID_MILLIS));
        return token;
    }

    /** @return the account uuid the token belongs to, or null when unknown or expired. */
    public String resolve(String token) {
        if (token == null || token.isBlank()) return null;

        Entry entry = tokens.get(token);
        if (entry == null) return null;
        if (entry.expiresAt <= System.currentTimeMillis()) {
            tokens.remove(token);
            return null;
        }
        return entry.uuid;
    }

    public void revokeAll(String uuid) {
        tokens.entrySet().removeIf(entry -> entry.getValue().uuid.equalsIgnoreCase(uuid));
    }

    private void purge() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> it = tokens.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt <= now) it.remove();
        }
    }

    private record Entry(String uuid, long expiresAt) {
    }
}
