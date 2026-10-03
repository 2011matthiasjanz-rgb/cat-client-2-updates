package dev.catclient2.launcher.auth;

import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.msa.data.MsaConstants;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public class AuthManager {
    private static final MsaApplicationConfig APP_CONFIG = new MsaApplicationConfig(
        MsaConstants.JAVA_TITLE_ID, MsaConstants.SCOPE_OFFLINE_ACCESS
    );

    private final HttpClient httpClient = MinecraftAuth.createHttpClient();
    private final SessionStore sessionStore;

    private JavaAuthManager authManager;

    public AuthManager(Path instanceDir) {
        this.sessionStore = new SessionStore(instanceDir);
    }

    /**
     * Tries to restore a previously saved session. Returns null if none exists or it's unusable.
     */
    public AccountSession tryRestoreSession() {
        JavaAuthManager restored = sessionStore.tryLoad(httpClient);
        if (restored == null) return null;

        try {
            var profile = restored.getMinecraftProfile().getUpToDate();
            var token = restored.getMinecraftToken().getUpToDate();

            this.authManager = restored;
            return new AccountSession(profile.getName(), profile.getId(), token.getToken());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Starts a device-code login flow. Must be called off the Swing EDT (it blocks until
     * the user completes the login in their browser or the flow times out).
     *
     * @param onDeviceCode called (on the calling thread) once the device code is available,
     *                     so the caller can display it to the user.
     */
    public AccountSession login(Consumer<MsaDeviceCode> onDeviceCode) throws IOException, InterruptedException, java.util.concurrent.TimeoutException {
        JavaAuthManager manager = JavaAuthManager.create(httpClient)
            .login((client, config) -> new DeviceCodeMsaAuthService(client, config, onDeviceCode));
        this.authManager = manager;

        var profile = manager.getMinecraftProfile().getUpToDate();
        var token = manager.getMinecraftToken().getUpToDate();

        sessionStore.save(manager);

        return new AccountSession(profile.getName(), profile.getId(), token.getToken());
    }

    public void logOut() {
        authManager = null;
        sessionStore.clear();
    }
}
