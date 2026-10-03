package dev.catclient2.launcher.ui.skin;

/** A point or direction in the model's local space, before rotation/projection. */
record Vec3(double x, double y, double z) {
    Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    Vec3 rotateY(double radians) {
        double cos = Math.cos(radians), sin = Math.sin(radians);
        return new Vec3(x * cos + z * sin, y, -x * sin + z * cos);
    }

    Vec3 rotateX(double radians) {
        double cos = Math.cos(radians), sin = Math.sin(radians);
        return new Vec3(x, y * cos - z * sin, y * sin + z * cos);
    }
}
