package dev.catclient2.launcher.ui;

import dev.catclient2.launcher.ui.skin.SkinPreviewPanel;
import dev.catclient2.launcher.ui.theme.CatClientTheme;
import dev.catclient2.launcher.ui.theme.Icons;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * Lets the player change their real Minecraft skin through Mojang's API - either by picking one
 * from a bundled list of premade skins, or by uploading their own PNG. Changes apply everywhere,
 * not just in Cat Client 2.
 */
public class CosmeticsScreen extends JPanel {
    private final SkinPreviewPanel currentSkinPreview = new SkinPreviewPanel();

    private final JPanel skinGallery = new JPanel();

    private final JLabel chosenFileLabel = new JLabel("No file chosen");
    private final JButton chooseFileButton = new JButton("Choose skin PNG...");
    private final JRadioButton classicVariant = new JRadioButton("Classic (Steve)", true);
    private final JRadioButton slimVariant = new JRadioButton("Slim (Alex)");
    private final JButton uploadButton = new JButton("Upload skin");
    private final JLabel skinStatusLabel = new JLabel(" ");

    private final JButton backButton = new JButton("Back");

    private Path chosenFile;
    private Listener listener;

    public interface Listener {
        void selectPresetSkin(String skinId, boolean slim);
        void chooseSkinFile();
        void uploadSkin(Path file, boolean slim);
    }

    public CosmeticsScreen() {
        setLayout(new BorderLayout());
        setBackground(CatClientTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(28, 36, 20, 36));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(new JLabel(Icons.cosmetics(CatClientTheme.ACCENT, 22)));
        titleRow.add(CatClientTheme.pageTitle("Cosmetics"));
        content.add(titleRow);
        content.add(Box.createVerticalStrut(6));
        content.add(CatClientTheme.hint("Changes your real Minecraft skin everywhere - not just in Cat Client 2."));
        content.add(Box.createVerticalStrut(16));

        currentSkinPreview.setPreferredSize(new Dimension(180, 220));
        currentSkinPreview.setMaximumSize(new Dimension(180, 220));
        currentSkinPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(currentSkinPreview);
        content.add(Box.createVerticalStrut(4));
        content.add(CatClientTheme.hint("drag to rotate"));
        content.add(Box.createVerticalStrut(16));

        content.add(gallerySection());
        content.add(Box.createVerticalStrut(16));
        content.add(uploadSection());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        backButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 14));
        footer.setOpaque(false);
        footer.add(backButton);
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel gallerySection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setBorder(CatClientTheme.cardBorder());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        section.add(CatClientTheme.sectionHeading("Pick a skin"));
        section.add(Box.createVerticalStrut(12));

        skinGallery.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 12));
        skinGallery.setOpaque(false);
        skinGallery.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(skinGallery);
        section.add(Box.createVerticalStrut(4));

        skinStatusLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        skinStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(skinStatusLabel);

        return section;
    }

    private JPanel uploadSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setBorder(CatClientTheme.cardBorder());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        section.add(CatClientTheme.sectionHeading("Or upload your own"));
        section.add(Box.createVerticalStrut(12));

        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fileRow.setOpaque(false);
        fileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        chosenFileLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        fileRow.add(chooseFileButton);
        fileRow.add(chosenFileLabel);
        section.add(fileRow);
        section.add(Box.createVerticalStrut(6));

        JPanel variantRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        variantRow.setOpaque(false);
        variantRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup group = new ButtonGroup();
        group.add(classicVariant);
        group.add(slimVariant);
        variantRow.add(classicVariant);
        variantRow.add(slimVariant);
        section.add(variantRow);
        section.add(Box.createVerticalStrut(6));

        uploadButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        uploadButton.setEnabled(false);
        section.add(uploadButton);

        chooseFileButton.addActionListener(e -> {
            if (listener != null) listener.chooseSkinFile();
        });
        uploadButton.addActionListener(e -> {
            if (listener != null && chosenFile != null) listener.uploadSkin(chosenFile, slimVariant.isSelected());
        });

        return section;
    }

    /** Fills the gallery with one button per bundled skin, showing its face as an icon. */
    public void setPresetSkins(List<PresetSkin> skins) {
        skinGallery.removeAll();

        for (PresetSkin skin : skins) {
            JButton button = new JButton(skin.displayName(), skin.icon());
            button.putClientProperty("JButton.buttonType", "roundRect");
            button.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            button.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            button.setIconTextGap(8);
            button.setMargin(new java.awt.Insets(10, 12, 10, 12));
            button.addActionListener(e -> {
                if (listener != null) listener.selectPresetSkin(skin.id(), skin.slim());
            });
            skinGallery.add(button);
        }

        if (skins.isEmpty()) {
            JLabel empty = new JLabel("No preset skins bundled yet.");
            empty.setForeground(CatClientTheme.TEXT_SECONDARY);
            skinGallery.add(empty);
        }

        skinGallery.revalidate();
        skinGallery.repaint();
    }

    public record PresetSkin(String id, String displayName, boolean slim, Icon icon) {
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setChosenFile(Path file) {
        this.chosenFile = file;
        chosenFileLabel.setText(file == null ? "No file chosen" : file.getFileName().toString());
        uploadButton.setEnabled(file != null);
    }

    public void setCurrentSkin(BufferedImage skin, boolean slim) {
        currentSkinPreview.setSkin(skin, slim);
    }

    public void setSkinStatus(String text) {
        skinStatusLabel.setText(text);
    }

    public void setUploadEnabled(boolean enabled) {
        uploadButton.setEnabled(enabled && chosenFile != null);
    }

    public JButton getBackButton() {
        return backButton;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(720, 520);
    }
}
