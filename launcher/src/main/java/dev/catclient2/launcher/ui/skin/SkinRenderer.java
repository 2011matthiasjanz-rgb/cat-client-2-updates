package dev.catclient2.launcher.ui.skin;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders the {@link PlayerModel} as an actual 3D-looking, rotatable preview using only Java2D: an
 * orthographic projection plus the painter's algorithm (sort faces back-to-front, skip the ones
 * facing away from the camera), texturing each face by affine-warping the two triangles it is split
 * into from the skin sheet. No OpenGL/native dependency, so it runs anywhere Swing does.
 */
public class SkinRenderer {
    private static final double SCALE = 4.2;

    private double yaw = -35;
    private double pitch = 15;
    private boolean slim;
    private BufferedImage skin;

    public void setSkin(BufferedImage skin, boolean slim) {
        this.skin = normalize(skin);
        this.slim = slim;
    }

    public void rotate(double deltaYaw, double deltaPitch) {
        yaw += deltaYaw;
        pitch = Math.max(-80, Math.min(80, pitch + deltaPitch));
    }

    public void setYawPitch(double yaw, double pitch) {
        this.yaw = yaw;
        this.pitch = Math.max(-80, Math.min(80, pitch));
    }

    /** Legacy 64x32 skins have no limb-overlay/back layer rows; pad them to the modern 64x64 sheet. */
    private static BufferedImage normalize(BufferedImage source) {
        if (source == null) return null;
        if (source.getHeight() >= 64) return source;

        BufferedImage padded = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = padded.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return padded;
    }

    public void paint(Graphics2D g, int width, int height) {
        if (skin == null) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        List<Projected> drawList = new ArrayList<>();
        for (SkinBox box : PlayerModel.parts(slim)) {
            for (SkinBox.Face face : box.faces()) {
                Vec3 a = rotate(face.a(), yawRad, pitchRad);
                Vec3 b = rotate(face.b(), yawRad, pitchRad);
                Vec3 c = rotate(face.c(), yawRad, pitchRad);
                Vec3 d = rotate(face.d(), yawRad, pitchRad);

                // Backface cull in true 3D (not on the projected 2D winding, which flips depending
                // on screen-axis conventions and was silently dropping the wrong faces): an
                // orthographic camera looking down -z sees a face only if its normal has z > 0.
                Vec3 ab = new Vec3(b.x() - a.x(), b.y() - a.y(), b.z() - a.z());
                Vec3 ac = new Vec3(c.x() - a.x(), c.y() - a.y(), c.z() - a.z());
                double normalZ = ab.x() * ac.y() - ab.y() * ac.x();
                if (normalZ <= 0) continue;

                double depth = (a.z() + b.z() + c.z() + d.z()) / 4.0;
                drawList.add(new Projected(a, b, c, d, face, depth));
            }
        }

        drawList.sort(Comparator.comparingDouble(p -> p.depth));

        double cx = width / 2.0;
        double cy = height / 2.0 + 20;

        for (Projected p : drawList) {
            drawFace(g, p, cx, cy);
        }
    }

    private Vec3 rotate(Vec3 v, double yawRad, double pitchRad) {
        return v.rotateY(yawRad).rotateX(pitchRad);
    }

    /** Splits the quad into two triangles and affine-warps the matching skin triangle into each. */
    private void drawFace(Graphics2D g, Projected p, double cx, double cy) {
        double[] sa = screen(p.a, cx, cy);
        double[] sb = screen(p.b, cx, cy);
        double[] sc = screen(p.c, cx, cy);
        double[] sd = screen(p.d, cx, cy);

        SkinBox.Face f = p.face;
        int u = f.texU(), v = f.texV(), w = f.texW(), h = f.texH();
        boolean flip = f.flipU();

        double[] ta = texCoord(0, 0, u, v, w, h, flip);
        double[] tb = texCoord(1, 0, u, v, w, h, flip);
        double[] tc = texCoord(1, 1, u, v, w, h, flip);
        double[] td = texCoord(0, 1, u, v, w, h, flip);

        drawTriangle(g, sa, sb, sc, ta, tb, tc);
        drawTriangle(g, sa, sc, sd, ta, tc, td);
    }

    private double[] texCoord(double fu, double fv, int u, int v, int w, int h, boolean flip) {
        double tu = flip ? (1 - fu) : fu;
        return new double[]{u + tu * w, v + fv * h};
    }

    private double[] screen(Vec3 v, double cx, double cy) {
        return new double[]{cx + v.x() * SCALE, cy - v.y() * SCALE};
    }

    /**
     * Draws one skin-texture triangle warped onto one screen triangle, by solving the affine
     * transform that maps the texture triangle's three points onto the screen triangle's three
     * points, then clipping the whole skin image to the destination via that transform.
     */
    private void drawTriangle(Graphics2D g, double[] s0, double[] s1, double[] s2, double[] t0, double[] t1, double[] t2) {
        AffineTransform transform = triangleTransform(t0, t1, t2, s0, s1, s2);
        if (transform == null) return;

        var clip = g.getClip();
        var path = new java.awt.geom.Path2D.Double();
        path.moveTo(s0[0], s0[1]);
        path.lineTo(s1[0], s1[1]);
        path.lineTo(s2[0], s2[1]);
        path.closePath();

        g.clip(path);
        g.drawImage(skin, transform, null);
        g.setClip(clip);
    }

    /** The affine transform mapping triangle (src0,src1,src2) onto (dst0,dst1,dst2). */
    private AffineTransform triangleTransform(double[] src0, double[] src1, double[] src2,
                                               double[] dst0, double[] dst1, double[] dst2) {
        double x0 = src0[0], y0 = src0[1];
        double x1 = src1[0], y1 = src1[1];
        double x2 = src2[0], y2 = src2[1];

        AffineTransform srcToUnit = new AffineTransform(x1 - x0, y1 - y0, x2 - x0, y2 - y0, x0, y0);
        AffineTransform dstFromUnit = new AffineTransform(
            dst1[0] - dst0[0], dst1[1] - dst0[1],
            dst2[0] - dst0[0], dst2[1] - dst0[1],
            dst0[0], dst0[1]);

        try {
            AffineTransform result = new AffineTransform(dstFromUnit);
            result.concatenate(srcToUnit.createInverse());
            return result;
        } catch (NoninvertibleTransformException e) {
            return null;
        }
    }

    private record Projected(Vec3 a, Vec3 b, Vec3 c, Vec3 d, SkinBox.Face face, double depth) {
    }
}
