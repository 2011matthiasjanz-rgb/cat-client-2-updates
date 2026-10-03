package dev.catclient2.launcher.ui.theme;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

/**
 * Small vector icons drawn directly with Java2D instead of shipped as image files - crisp at any
 * size and themeable with a single color, which bitmap icons in a fixed accent color are not.
 */
public final class Icons {
    private Icons() {
    }

    public static Icon play(Color color, int size) {
        return icon(size, color, (g, s) -> {
            Path2D path = new Path2D.Float();
            float m = s * 0.22f;
            path.moveTo(m, s * 0.12f);
            path.lineTo(s - m, s * 0.5f);
            path.lineTo(m, s * 0.88f);
            path.closePath();
            g.fill(path);
        });
    }

    public static Icon friends(Color color, int size) {
        return icon(size, color, (g, s) -> {
            drawPerson(g, s * 0.28f, s * 0.2f, s * 0.34f);
            drawPerson(g, s * 0.58f, s * 0.32f, s * 0.30f);
        });
    }

    public static Icon cosmetics(Color color, int size) {
        return icon(size, color, (g, s) -> {
            Path2D shirt = new Path2D.Float();
            float cx = s * 0.5f;
            shirt.moveTo(cx - s * 0.28f, s * 0.22f);
            shirt.lineTo(cx - s * 0.12f, s * 0.14f);
            shirt.curveTo(cx - s * 0.06f, s * 0.22f, cx + s * 0.06f, s * 0.22f, cx + s * 0.12f, s * 0.14f);
            shirt.lineTo(cx + s * 0.28f, s * 0.22f);
            shirt.lineTo(cx + s * 0.20f, s * 0.36f);
            shirt.lineTo(cx + s * 0.14f, s * 0.32f);
            shirt.lineTo(cx + s * 0.14f, s * 0.82f);
            shirt.lineTo(cx - s * 0.14f, s * 0.82f);
            shirt.lineTo(cx - s * 0.14f, s * 0.32f);
            shirt.lineTo(cx - s * 0.20f, s * 0.36f);
            shirt.closePath();
            g.fill(shirt);
        });
    }

    public static Icon settings(Color color, int size) {
        return icon(size, color, (g, s) -> {
            float cx = s * 0.5f, cy = s * 0.5f;
            float outer = s * 0.36f, inner = s * 0.16f;
            int teeth = 8;
            Path2D gear = new Path2D.Float();
            for (int i = 0; i < teeth * 2; i++) {
                double angle = Math.PI * i / teeth;
                float r = (i % 2 == 0) ? outer : outer * 0.72f;
                float x = cx + (float) (Math.cos(angle) * r);
                float y = cy + (float) (Math.sin(angle) * r);
                if (i == 0) gear.moveTo(x, y);
                else gear.lineTo(x, y);
            }
            gear.closePath();
            g.fill(gear);
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fill(new Ellipse2D.Float(cx - inner, cy - inner, inner * 2, inner * 2));
        });
    }

    public static Icon logout(Color color, int size) {
        return icon(size, color, (g, s) -> {
            g.setStroke(new java.awt.BasicStroke(Math.max(1.5f, s * 0.09f), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            RoundRectangle2D door = new RoundRectangle2D.Float(s * 0.18f, s * 0.16f, s * 0.34f, s * 0.68f, s * 0.06f, s * 0.06f);
            g.draw(door);

            Path2D arrow = new Path2D.Float();
            float y = s * 0.5f;
            arrow.moveTo(s * 0.38f, y);
            arrow.lineTo(s * 0.86f, y);
            arrow.moveTo(s * 0.68f, y - s * 0.16f);
            arrow.lineTo(s * 0.86f, y);
            arrow.lineTo(s * 0.68f, y + s * 0.16f);
            g.draw(arrow);
        });
    }

    public static Icon bell(Color color, int size) {
        return icon(size, color, (g, s) -> {
            Path2D bell = new Path2D.Float();
            float cx = s * 0.5f;
            bell.moveTo(cx, s * 0.12f);
            bell.curveTo(cx - s * 0.22f, s * 0.16f, cx - s * 0.26f, s * 0.34f, cx - s * 0.26f, s * 0.48f);
            bell.curveTo(cx - s * 0.26f, s * 0.62f, cx - s * 0.32f, s * 0.68f, cx - s * 0.38f, s * 0.74f);
            bell.lineTo(cx + s * 0.38f, s * 0.74f);
            bell.curveTo(cx + s * 0.32f, s * 0.68f, cx + s * 0.26f, s * 0.62f, cx + s * 0.26f, s * 0.48f);
            bell.curveTo(cx + s * 0.26f, s * 0.34f, cx + s * 0.22f, s * 0.16f, cx, s * 0.12f);
            bell.closePath();
            g.fill(bell);
            g.fill(new Ellipse2D.Float(cx - s * 0.08f, s * 0.80f, s * 0.16f, s * 0.14f));
        });
    }

    public static Icon server(Color color, int size) {
        return icon(size, color, (g, s) -> {
            for (int i = 0; i < 3; i++) {
                float y = s * (0.18f + i * 0.28f);
                g.fill(new RoundRectangle2D.Float(s * 0.14f, y, s * 0.72f, s * 0.20f, s * 0.05f, s * 0.05f));
                g.setComposite(java.awt.AlphaComposite.Clear);
                g.fill(new Ellipse2D.Float(s * 0.68f, y + s * 0.07f, s * 0.06f, s * 0.06f));
                g.setComposite(java.awt.AlphaComposite.SrcOver);
            }
        });
    }

    private static void drawPerson(Graphics2D g, float cx, float headY, float scale) {
        g.fill(new Ellipse2D.Float(cx - scale * 0.32f, headY, scale * 0.64f, scale * 0.64f));
        Path2D body = new Path2D.Float();
        body.moveTo(cx - scale * 0.5f, headY + scale * 1.5f);
        body.curveTo(cx - scale * 0.5f, headY + scale * 0.8f, cx + scale * 0.5f, headY + scale * 0.8f, cx + scale * 0.5f, headY + scale * 1.5f);
        body.closePath();
        g.fill(body);
    }

    /**
     * Painted onto its own ARGB buffer first, not straight onto the component's graphics - some
     * icons punch transparent holes (e.g. the gear's center) via {@code AlphaComposite.Clear},
     * which only behaves correctly against a dedicated alpha layer, not a component's live surface.
     */
    private static Icon icon(int size, Color color, BiConsumer<Graphics2D, Float> painter) {
        BufferedImage buffer = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = buffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setColor(color);
        painter.accept(bg, (float) size);
        bg.dispose();

        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.drawImage(buffer, x, y, null);
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }
}
