package dev.catclient2.launcher.home;

/** One entry in the quick-connect server list - name plus the address Minecraft should join. */
public record QuickServer(String name, String address) {
}
