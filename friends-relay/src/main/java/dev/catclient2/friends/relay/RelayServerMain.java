package dev.catclient2.friends.relay;

import java.io.IOException;

/**
 * Entry point for the relay process. Run with e.g.
 * {@code java -jar cat-friends-relay.jar --port=8766 --pool-size=10 --pool-start-port=8800}.
 */
public final class RelayServerMain {
    private RelayServerMain() {
    }

    public static void main(String[] args) throws IOException {
        int controlPort = 8766;
        int poolSize = 10;
        int poolStartPort = 8800;

        for (String arg : args) {
            if (arg.startsWith("--port=")) controlPort = Integer.parseInt(arg.substring(7));
            else if (arg.startsWith("--pool-size=")) poolSize = Integer.parseInt(arg.substring(12));
            else if (arg.startsWith("--pool-start-port=")) poolStartPort = Integer.parseInt(arg.substring(18));
            else {
                System.err.println("Unknown argument: " + arg);
                System.err.println("Usage: java -jar cat-friends-relay.jar [--port=8766] [--pool-size=10] [--pool-start-port=8800]");
                return;
            }
        }

        new RelayServer(controlPort, poolSize, poolStartPort).start();
    }
}
