package dev.catclient2.launcher.friends;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The bundled "pick a skin" gallery: PNGs under {@code preset-skins/} in the launcher's own
 * resources, listed in {@code preset-skins/skins.json}. To add a skin, drop a 64x64 skin PNG named
 * {@code <id>.png} next to skins.json and add an entry there.
 */
public class PresetSkins {
    private static final Gson GSON = new Gson();
    private static final String BASE = "/preset-skins/";

    private PresetSkins() {
    }

    public static List<PresetSkin> list() {
        try (InputStream in = PresetSkins.class.getResourceAsStream(BASE + "skins.json")) {
            if (in == null) return List.of();
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                List<PresetSkin> skins = GSON.fromJson(reader, new TypeToken<List<PresetSkin>>() {}.getType());
                return skins == null ? List.of() : skins;
            }
        } catch (IOException e) {
            System.err.println("[cat-client] Could not read bundled skin list: " + e.getMessage());
            return List.of();
        }
    }

    /** A small face-only preview icon cropped from the skin's head layer, for the gallery button. */
    public static ImageIcon faceIcon(PresetSkin skin) {
        try (InputStream in = PresetSkins.class.getResourceAsStream(BASE + skin.id() + ".png")) {
            if (in == null) return null;
            BufferedImage full = ImageIO.read(in);
            if (full == null) return null;

            // Standard Minecraft skin layout: base head at (8,8), 8x8; overlay head at (40,8), 8x8.
            BufferedImage face = full.getSubimage(8, 8, 8, 8);
            Image scaled = face.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            System.err.println("[cat-client] Could not load preset skin '" + skin.id() + "': " + e.getMessage());
            return null;
        }
    }

    /** The full, unscaled skin texture - what {@link SkinModel3D} needs to build a 3D preview. */
    public static BufferedImage fullImage(PresetSkin skin) {
        try (InputStream in = PresetSkins.class.getResourceAsStream(BASE + skin.id() + ".png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Copies a bundled skin to a temp file so it can go through the same upload path as a
     * user-chosen file. Caller is responsible for the temp file's lifetime; it is small and the OS
     * cleans its temp directory eventually either way.
     */
    public static Path extractToTemp(PresetSkin skin) throws IOException {
        try (InputStream in = PresetSkins.class.getResourceAsStream(BASE + skin.id() + ".png")) {
            if (in == null) throw new IOException("Bundled skin '" + skin.id() + "' is missing its PNG");
            Path temp = Files.createTempFile("catclient2-skin-" + skin.id(), ".png");
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return temp;
        }
    }
}
