package me.justicepro.spigotgui.Utils;

import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Loads the application icon images from the embedded resources so every
 * JFrame can call {@code setIconImages(AppIcons.getIcons())}.
 */
public final class AppIcons {

    private static final String[] ICON_PATHS = {
        "/icons/icon16.png",
        "/icons/icon20.png",
        "/icons/icon26.png",
        "/icons/icon28.png",
        "/icons/icon32.png",
        "/icons/icon40.png",
        "/icons/icon48.png",
        "/icons/icon64.png",
        "/icons/icon128.png",
        "/icons/icon256.png"
    };

    private static List<Image> icons;

    private AppIcons() {}

    /**
     * Returns the cached list of application icons in all available sizes.
     * Safe to call from any thread; the list is lazily initialised once.
     */
    public static synchronized List<Image> getIcons() {
        if (icons == null) {
            List<Image> loaded = new ArrayList<>();
            for (String path : ICON_PATHS) {
                try (InputStream in = AppIcons.class.getResourceAsStream(path)) {
                    if (in != null) {
                        loaded.add(ImageIO.read(in));
                    }
                } catch (IOException ignored) {
                }
            }
            icons = Collections.unmodifiableList(loaded);
        }
        return icons;
    }
}
