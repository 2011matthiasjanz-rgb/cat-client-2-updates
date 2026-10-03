package dev.catclient2.friends.protocol;

/**
 * A change notification delivered by the long-polling {@code /events} endpoint. Clients only need
 * the cursor plus enough context to know whose data changed, they then re-fetch {@code /state}.
 */
public record EventView(
    long cursor,
    String type,
    String actorUuid,
    String targetUuid
) {
}
