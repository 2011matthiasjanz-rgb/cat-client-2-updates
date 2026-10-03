package dev.catclient2.friends.protocol;

import java.util.List;

/**
 * Everything a launcher's friends screen needs in one shot, so the UI never has to stitch
 * several responses together.
 */
public record StateResponse(
    AccountView self,
    /** Server event cursor the client should continue long-polling from. */
    long cursor,
    List<AccountView> friends,
    /** Requests waiting for this account to accept or decline. */
    List<FriendRequestView> incoming,
    /** Requests this account sent that are still open. */
    List<FriendRequestView> outgoing,
    /** Settled join requests, so the launcher can finish a join after a restart. */
    List<FriendRequestView> acceptedJoins,
    /** Published sessions of this account's friends (including this account's own). */
    List<JoinSession> sessions
) {
}
