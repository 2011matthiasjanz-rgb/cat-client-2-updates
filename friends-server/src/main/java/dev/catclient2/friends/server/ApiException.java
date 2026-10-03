package dev.catclient2.friends.server;

/**
 * An error that maps directly onto an HTTP status code and a user visible message.
 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
