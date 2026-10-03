package dev.catclient2.launcher.update;

/**
 * Compares dot-separated version strings like "1.2.0" numerically component by component, so "1.9.0"
 * correctly counts as older than "1.10.0" (a plain string comparison would get that backwards).
 */
final class VersionComparator {
    private VersionComparator() {
    }

    /** True if {@code remote} is a newer version than {@code local}. Unparsable parts count as 0. */
    static boolean isNewer(String local, String remote) {
        int[] a = parse(local);
        int[] b = parse(remote);

        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int partA = i < a.length ? a[i] : 0;
            int partB = i < b.length ? b[i] : 0;
            if (partB != partA) return partB > partA;
        }
        return false;
    }

    private static int[] parse(String version) {
        String cleaned = version == null ? "" : version.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) cleaned = cleaned.substring(1);

        String[] parts = cleaned.split("[.\\-+]");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }
}
