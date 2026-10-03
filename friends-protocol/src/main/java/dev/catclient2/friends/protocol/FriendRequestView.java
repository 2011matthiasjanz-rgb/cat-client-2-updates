package dev.catclient2.friends.protocol;

/**
 * One friend / join request. The uuid form is stored without dashes so it can be compared
 * against the values Mojang returns without worrying about formatting.
 */
public record FriendRequestView(
    String id,
    AccountView sender,
    AccountView receiver,
    RequestKind kind,
    RequestState state,
    String message,
    /** Session the sender wanted to join, empty for plain friend requests. */
    String targetSessionId,
    long createdAt,
    long updatedAt
) {
    public boolean isPending() {
        return state == RequestState.PENDING;
    }
}
