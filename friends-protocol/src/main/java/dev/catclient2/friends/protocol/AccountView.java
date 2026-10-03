package dev.catclient2.friends.protocol;

/**
 * A Minecraft account as seen by the friend service. The uuid is the authoritative identity,
 * the name is only cosmetic (it gets refreshed whenever the account authenticates).
 */
public record AccountView(String uuid, String name) {
    public boolean isSelf(String otherUuid) {
        return uuid != null && uuid.equalsIgnoreCase(otherUuid);
    }
}
