package dev.catclient2.launcher.ui.skin;

import java.util.ArrayList;
import java.util.List;

/**
 * The classic 8-part Minecraft player model (head, torso, 2 arms, 2 legs) with both the base and
 * overlay (hat/jacket/sleeves/pants) layer of each part, positioned and UV-mapped per Mojang's
 * standard 64x64 skin layout. Slim ("Alex") arms are 3px wide instead of 4px.
 */
final class PlayerModel {
    private static final double INFLATE = 0.5;

    private PlayerModel() {
    }

    static List<SkinBox> parts(boolean slim) {
        List<SkinBox> parts = new ArrayList<>();
        double armWidth = slim ? 3 : 4;

        // Origin (0,0,0) is between the feet, +y up, +x right, +z forward - a torso-centered rig,
        // not literal Minecraft world space, chosen purely so the model sits centered for rendering.
        SkinBox head = new SkinBox(new Vec3(-4, 24, -4), new Vec3(8, 8, 8), 0, 0, false);
        SkinBox torso = new SkinBox(new Vec3(-4, 12, -2), new Vec3(8, 12, 4), 16, 16, false);
        SkinBox rightArm = new SkinBox(new Vec3(-4 - armWidth, 12, -2), new Vec3(armWidth, 12, 4), 40, 16, false);
        SkinBox leftArm = new SkinBox(new Vec3(4, 12, -2), new Vec3(armWidth, 12, 4), 32, 48, true);
        SkinBox rightLeg = new SkinBox(new Vec3(-4, 0, -2), new Vec3(4, 12, 4), 0, 16, false);
        SkinBox leftLeg = new SkinBox(new Vec3(0, 0, -2), new Vec3(4, 12, 4), 16, 48, true);

        parts.add(head);
        parts.add(torso);
        parts.add(rightArm);
        parts.add(leftArm);
        parts.add(rightLeg);
        parts.add(leftLeg);

        parts.add(head.asOverlay(32, 0, INFLATE));
        parts.add(torso.asOverlay(16, 32, INFLATE));
        parts.add(rightArm.asOverlay(40, 32, INFLATE));
        parts.add(leftArm.asOverlay(48, 48, INFLATE));
        parts.add(rightLeg.asOverlay(0, 32, INFLATE));
        parts.add(leftLeg.asOverlay(0, 48, INFLATE));

        return parts;
    }
}
