package dev.catclient2.launcher.ui.skin;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

/**
 * A drag-to-rotate 3D preview of a Minecraft skin. Spins slowly on its own until the player drags
 * it, matching the idle-then-interactive behavior of skin viewers like NameMC's.
 */
public class SkinPreviewPanel extends JPanel {
    private final SkinRenderer renderer = new SkinRenderer();
    private final Timer idleSpin;

    private int lastX, lastY;
    private boolean dragging;
    private boolean userControlled;

    public SkinPreviewPanel() {
        setOpaque(false);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragging = true;
                userControlled = true;
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging) return;
                int dx = e.getX() - lastX;
                int dy = e.getY() - lastY;
                lastX = e.getX();
                lastY = e.getY();
                renderer.rotate(dx * 0.6, -dy * 0.4);
                repaint();
            }
        });

        idleSpin = new Timer(40, e -> {
            if (!userControlled && !dragging) {
                renderer.rotate(0.6, 0);
                repaint();
            }
        });
        idleSpin.start();
    }

    public void setSkin(BufferedImage skin, boolean slim) {
        renderer.setSkin(skin, slim);
        repaint();
    }

    /** Stops the idle spin and drops any manual rotation, so a freshly shown preview starts calm. */
    public void resetView() {
        userControlled = false;
        renderer.setYawPitch(-35, 15);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        renderer.paint(g2, getWidth(), getHeight());
        g2.dispose();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        idleSpin.stop();
    }
}
