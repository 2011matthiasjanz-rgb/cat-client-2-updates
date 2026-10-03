package dev.catclient2.friends.protocol;

/**
 * Wire format constants shared by the launcher and the friend service so both sides agree on
 * route names, header names and the JSON envelope.
 */
public final class Api {
    public static final String HEADER_TOKEN = "X-Cat-Token";
    public static final String PARAM_TOKEN = "token";

    public static final String PATH_AUTH = "/api/auth";
    public static final String PATH_STATE = "/api/state";
    public static final String PATH_EVENTS = "/api/events";
    public static final String PATH_REQUESTS = "/api/requests";
    public static final String PATH_REQUEST_RESPOND = "/api/requests/respond";
    public static final String PATH_REQUEST_CANCEL = "/api/requests/cancel";
    public static final String PATH_SESSION = "/api/session";
    public static final String PATH_HEALTH = "/api/health";

    /** Sessions older than this are treated as "host is no longer in game". */
    public static final long SESSION_STALE_MILLIS = 120_000L;

    private Api() {
    }
}
