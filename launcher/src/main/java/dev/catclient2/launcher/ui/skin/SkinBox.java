package dev.catclient2.launcher.ui.skin;

import java.util.ArrayList;
import java.util.List;

/**
 * One cuboid part of the player model (head, torso, arm, leg, ...), positioned relative to the
 * model's origin (between the feet) and textured from six rectangular regions of the skin sheet,
 * using Minecraft's standard box-UV layout: a texture region {@code (u, v)} of size
 * {@code (w+d)*2 x (h+d)} unwraps a {@code w x h x d} box.
 */
final class SkinBox {
    private final Vec3 origin;
    private final Vec3 size;
    private final int u, v;
    private final boolean mirrored;

    SkinBox(Vec3 origin, Vec3 size, int u, int v, boolean mirrored) {
        this.origin = origin;
        this.size = size;
        this.u = u;
        this.v = v;
        this.mirrored = mirrored;
    }

    /** One planar quad of the box, ready to be rotated, projected and depth-sorted. */
    record Face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int texU, int texV, int texW, int texH, boolean flipU) {
    }

    /**
     * Corner naming: p<xyz> where each digit is 0 (min) or 1 (max) on that axis - x=left/right,
     * y=bottom/top, z=back/front (+z faces the camera at yaw 0). Every face below is built by
     * naming its four corners in a consistent counter-clockwise order as seen from outside the box
     * along that face's outward normal, which is what the renderer's normal-based cull expects.
     */
    List<Face> faces() {
        double w = size.x(), h = size.y(), d = size.z();
        double x0 = origin.x(), y0 = origin.y(), z0 = origin.z();

        Vec3 p000 = new Vec3(x0, y0, z0);
        Vec3 p100 = new Vec3(x0 + w, y0, z0);
        Vec3 p010 = new Vec3(x0, y0 + h, z0);
        Vec3 p110 = new Vec3(x0 + w, y0 + h, z0);
        Vec3 p001 = new Vec3(x0, y0, z0 + d);
        Vec3 p101 = new Vec3(x0 + w, y0, z0 + d);
        Vec3 p011 = new Vec3(x0, y0 + h, z0 + d);
        Vec3 p111 = new Vec3(x0 + w, y0 + h, z0 + d);

        int iw = (int) Math.round(w), ih = (int) Math.round(h), id = (int) Math.round(d);
        boolean flip = mirrored;

        List<Face> faces = new ArrayList<>(6);
        // Box-UV layout (Minecraft standard): top/bottom share the first row, the four sides share the second.
        // Each face lists corners a,b,c,d counter-clockwise as seen from outside along its normal -
        // i.e. cross(b-a, c-a) points along the outward normal - verified by hand for every face.
        faces.add(new Face(p010, p011, p111, p110, u + id, v, iw, id, flip));                              // top (+y)
        faces.add(new Face(p000, p100, p101, p001, u + id + iw, v, iw, id, flip));                         // bottom (-y)
        faces.add(new Face(p100, p110, p111, p101, u, v + id, id, ih, flip));                               // east/right (+x)
        faces.add(new Face(p000, p001, p011, p010, u + id + iw, v + id, id, ih, flip));                     // west/left (-x)
        faces.add(new Face(p001, p101, p111, p011, u + id * 2 + iw, v + id, iw, ih, flip));                 // north/front (+z)
        faces.add(new Face(p000, p010, p110, p100, u + id, v + id, iw, ih, flip));                          // south/back (-z)
        return faces;
    }

    /** A second, slightly larger copy of this box (the overlay layer: hat, jacket, sleeves, pants). */
    SkinBox asOverlay(int overlayU, int overlayV, double inflate) {
        Vec3 grown = new Vec3(size.x() + inflate * 2, size.y() + inflate * 2, size.z() + inflate * 2);
        Vec3 shifted = new Vec3(origin.x() - inflate, origin.y() - inflate, origin.z() - inflate);
        return new SkinBox(shifted, grown, overlayU, overlayV, mirrored);
    }

}
