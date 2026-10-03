package dev.catclient2.launcher.ui.skin;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** Throwaway: renders a few fixed angles of a test skin to PNG files for visual verification. */
public final class SkinRenderSmokeTest {
    public static void main(String[] args) throws Exception {
        BufferedImage skin = ImageIO.read(new File(System.getenv("TEMP") + "\\test-skin-debug.png"));

        double[][] angles = {{0, -20}, {90, -20}, {180, -20}, {270, -20}, {0, -80}};
        for (int i = 0; i < angles.length; i++) {
            SkinRenderer renderer = new SkinRenderer();
            renderer.setSkin(skin, false);
            renderer.setYawPitch(angles[i][0], angles[i][1]);

            BufferedImage out = new BufferedImage(300, 380, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setColor(new Color(0x1A1714));
            g.fillRect(0, 0, 300, 380);
            renderer.paint(g, 300, 380);
            g.dispose();

            File file = new File(System.getenv("TEMP") + "\\skin-debug-" + i + ".png");
            ImageIO.write(out, "png", file);
            System.out.println("wrote " + file);
        }
    }
}
