package dev.catclient2.friends.protocol;

/**
 * What a pending request is asking for.
 *
 * <ul>
 *   <li>{@link #FRIEND} - "let's be friends", grants access to each other's published sessions.</li>
 *   <li>{@link #JOIN} - "may I join your game", also grants friendship when accepted so the
 *       acceptor's session can be used to install and launch the joiner's copy of the modpack.</li>
 * </ul>
 */
public enum RequestKind {
    FRIEND,
    JOIN
}
