package dev.catclient2.updatechecker;

import dev.catclient2.launcher.ui.notification.ToastNotification;
import dev.catclient2.launcher.ui.theme.CatClientTheme;
import dev.catclient2.launcher.update.UpdateChecker;
import dev.catclient2.launcher.update.UpdateInfo;
import dev.catclient2.launcher.update.UpdateInstaller;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A tiny, invisible, one-shot background process: started by the Windows startup entry the installer
 * creates, it checks for a launcher update and - only if one exists - shows a notification with an
 * Update button, then exits. If there's no update it exits immediately with no visible window at all.
 *
 * <p>This is deliberately a separate process from the full launcher rather than, say, the launcher
 * itself running minimized at startup: the launcher is a much heavier app (friend service, Minecraft
 * launching, skin rendering) that nobody wants silently resident just to poll a version number.
 */
public final class UpdateCheckerMain {
    private UpdateCheckerMain() {
    }

    public static void main(String[] args) {
        CatClientTheme.install();

        UpdateChecker.checkForUpdate().ifPresentOrElse(
            UpdateCheckerMain::notifyUpdateAvailable,
            () -> System.exit(0)
        );
    }

    private static void notifyUpdateAvailable(UpdateInfo info) {
        SwingUtilities.invokeLater(() -> ToastNotification.show(
            "Cat Client 2",
            "Version " + info.version() + " is available.",
            "Update",
            () -> installUpdate(info)
        ));
    }

    private static void installUpdate(UpdateInfo info) {
        ToastNotification.showLater("Cat Client 2", "Installing the update...", null, null);

        new Thread(() -> {
            try {
                Process installerProcess = UpdateInstaller.install(info);
                // Unlike the full launcher (which exits immediately since its own files are being
                // replaced anyway), this process has nothing else to lose by waiting - so it stays
                // alive just long enough to report that the install actually finished.
                int exitCode = installerProcess.waitFor();

                if (exitCode == 0) {
                    ToastNotification.showLater("Cat Client 2",
                        "Updated to version " + info.version() + " - ready to play.", null, null);
                } else {
                    ToastNotification.showLater("Cat Client 2",
                        "Update may not have completed (installer exit code " + exitCode + ").", null, null);
                }
            } catch (Exception e) {
                e.printStackTrace();
                ToastNotification.showLater("Cat Client 2", "Update failed: " + e.getMessage(), null, null);
            } finally {
                // Give the final notification a moment to actually appear before the process ends.
                Timer exitTimer = new Timer(5_000, ev -> System.exit(0));
                exitTimer.setRepeats(false);
                exitTimer.start();
            }
        }, "update-install").start();
    }
}
