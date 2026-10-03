package dev.catclient2.launcher.launch;

import dev.catclient2.launcher.auth.AccountSession;
import dev.catclient2.launcher.install.FabricVersionMeta;
import dev.catclient2.launcher.install.VersionMeta;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ArgumentBuilder {
    public static final String LAUNCHER_NAME = "cat-client-2-launcher";
    public static final String LAUNCHER_VERSION = "1.0.0";

    public static List<String> buildJvmArgs(Path instanceDir, String vanillaVersion, List<Path> classpath,
                                             VersionMeta vanillaMeta, FabricVersionMeta fabricMeta, int memoryGb) {
        List<String> args = new ArrayList<>();

        args.add("-Xmx" + memoryGb + "G");
        args.add("-Dminecraft.launcher.brand=" + LAUNCHER_NAME);
        args.add("-Dminecraft.launcher.version=" + LAUNCHER_VERSION);
        args.add("-cp");
        args.add(ClasspathBuilder.toClasspathString(classpath));

        // The vanilla jvm argument block ends with "-cp ${classpath}" and references
        // ${natives_directory}/${launcher_name}/${launcher_version}. A later "-cp" wins in the
        // JVM command line, so leaving ${classpath} unsubstituted silently replaces the real
        // classpath with a non-existent one and the game dies with ClassNotFoundException.
        Map<String, String> substitutions = Map.ofEntries(
            Map.entry("${classpath}", ClasspathBuilder.toClasspathString(classpath)),
            Map.entry("${natives_directory}", nativesDir(instanceDir, vanillaVersion).toAbsolutePath().toString()),
            Map.entry("${launcher_name}", LAUNCHER_NAME),
            Map.entry("${launcher_version}", LAUNCHER_VERSION)
        );

        if (vanillaMeta.arguments != null && vanillaMeta.arguments.jvm != null) {
            for (var raw : vanillaMeta.arguments.jvm) {
                args.addAll(substituteAll(VersionMeta.resolveArgument(raw), substitutions));
            }
        }
        if (fabricMeta.arguments != null && fabricMeta.arguments.jvm != null) {
            for (var raw : fabricMeta.arguments.jvm) {
                args.addAll(substituteAll(VersionMeta.resolveArgument(raw), substitutions));
            }
        }

        return args;
    }

    public static Path nativesDir(Path instanceDir, String vanillaVersion) {
        return instanceDir.resolve("versions").resolve(vanillaVersion).resolve("natives");
    }

    public static List<String> buildGameArgs(VersionMeta vanillaMeta, FabricVersionMeta fabricMeta,
                                              AccountSession session, Path instanceDir, String vanillaVersion) {
        Map<String, String> substitutions = Map.ofEntries(
            Map.entry("${auth_player_name}", session.username()),
            Map.entry("${auth_uuid}", session.uuid().toString()),
            Map.entry("${auth_access_token}", session.accessToken()),
            Map.entry("${auth_xuid}", session.uuid().toString().replace("-", "")),
            Map.entry("${user_type}", "msa"),
            Map.entry("${version_name}", fabricMeta.id),
            Map.entry("${game_directory}", instanceDir.toAbsolutePath().toString()),
            Map.entry("${assets_root}", instanceDir.resolve("assets").toAbsolutePath().toString()),
            Map.entry("${assets_index_name}", vanillaMeta.assetIndex.id),
            Map.entry("${version_type}", vanillaMeta.type != null ? vanillaMeta.type : "release"),
            Map.entry("${clientid}", ""),
            Map.entry("${resolution_width}", "925"),
            Map.entry("${resolution_height}", "530")
        );

        List<String> args = new ArrayList<>();

        if (vanillaMeta.arguments != null && vanillaMeta.arguments.game != null) {
            for (var raw : vanillaMeta.arguments.game) {
                for (String resolved : VersionMeta.resolveArgument(raw)) {
                    args.add(substitute(resolved, substitutions));
                }
            }
        }
        if (fabricMeta.arguments != null && fabricMeta.arguments.game != null) {
            for (var raw : fabricMeta.arguments.game) {
                for (String resolved : VersionMeta.resolveArgument(raw)) {
                    args.add(substitute(resolved, substitutions));
                }
            }
        }

        return args;
    }

    private static List<String> substituteAll(List<String> values, Map<String, String> substitutions) {
        List<String> out = new ArrayList<>(values.size());
        for (String value : values) {
            String resolved = substitute(value, substitutions);
            int leftover = resolved.indexOf("${");
            if (leftover >= 0) {
                int end = resolved.indexOf('}', leftover);
                String placeholder = end < 0 ? resolved.substring(leftover) : resolved.substring(leftover, end + 1);
                throw new IllegalStateException("Unresolved placeholder " + placeholder + " in argument: " + resolved);
            }
            out.add(resolved);
        }
        return out;
    }

    private static String substitute(String value, Map<String, String> substitutions) {
        String result = value;
        for (var entry : substitutions.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
