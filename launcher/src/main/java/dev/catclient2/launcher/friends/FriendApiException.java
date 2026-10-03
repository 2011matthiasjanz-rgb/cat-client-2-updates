package dev.catclient2.launcher.friends;

import java.io.IOException;

/**
 * An error the friend service reported, including its HTTP status so the launcher can tell an
 * expired session from a real failure.
 */
public class FriendApiException extends IOException {
    private final int status;

    public FriendApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public boolean isAuthError() {
        return status == 401;
    }
}
