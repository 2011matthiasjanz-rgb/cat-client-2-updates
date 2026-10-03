package dev.catclient2.launcher.install;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.catclient2.launcher.util.OperatingSystem;

/**
 * Evaluates Mojang version-manifest "rules" arrays (used by both libraries and arguments)
 * against the current OS. Only the "os.name" condition is handled - the "features" condition
 * (demo user / resolution / quick-play) is treated as never-present, since this launcher never
 * sets those features, matching how a normal (non-demo) launch behaves.
 */
public class RuleEvaluator {
    private RuleEvaluator() {
    }

    public static boolean rulesAllow(JsonArray rules) {
        if (rules == null || rules.isEmpty()) return true;

        boolean allowed = false;

        for (var element : rules) {
            JsonObject rule = element.getAsJsonObject();
            String action = rule.get("action").getAsString();

            boolean matches = true;
            if (rule.has("os")) {
                matches = osMatches(rule.getAsJsonObject("os"));
            }
            if (rule.has("features")) {
                // We never enable any optional feature (demo/resolution/quick-play).
                matches = false;
            }

            if (matches) {
                allowed = action.equals("allow");
            }
        }

        return allowed;
    }

    private static boolean osMatches(JsonObject os) {
        if (os.has("name")) {
            String name = os.get("name").getAsString();
            OperatingSystem current = OperatingSystem.get();
            boolean nameMatches = switch (name) {
                case "windows" -> current == OperatingSystem.WINDOWS;
                case "osx" -> current == OperatingSystem.OSX;
                case "linux" -> current == OperatingSystem.LINUX;
                default -> false;
            };
            if (!nameMatches) return false;
        }

        return true;
    }
}
