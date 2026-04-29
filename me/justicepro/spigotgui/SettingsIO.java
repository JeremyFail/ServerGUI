package me.justicepro.spigotgui;

import mjson.Json;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * JSON-based persistence for {@link Settings} and {@link ServerSettings}.
 * <p>
 * Settings are stored as a human-readable {@code servergui.settings.json} file.
 * Fields missing from the JSON (e.g. added in a newer version) are silently
 * filled with their defaults, so upgrading never wipes existing settings and no
 * backup/fallback file is needed.
 * <p>
 * On first launch after migrating from the old binary format, the legacy
 * {@code servergui.settings} file is read once and immediately re-saved as JSON.
 */
public class SettingsIO {

    private static final String JSON_FILE = "servergui.settings.json";

    /** Legacy binary settings file names tried in order when no JSON file exists. Deleted after successful migration. */
    private static final String[] LEGACY_FILES = { "servergui.settings", "spigotgui.settings" };

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns the JSON settings file ({@code servergui.settings.json}). */
    public static File getFile() { return new File(JSON_FILE); }

    /**
     * Save {@code settings} to {@code servergui.settings.json}.
     *
     * @throws IOException if the file cannot be written
     */
    public static void save(Settings settings) throws IOException {
        String json = prettyJson(toJson(settings).toString());
        Files.write(getFile().toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Load settings from {@code servergui.settings.json}, with graceful fallback:
     * <ol>
     *   <li>Parse JSON, using defaults for any absent or unrecognized fields.</li>
     *   <li>If the JSON file does not exist, attempt a one-time migration from the
     *       legacy binary {@code servergui.settings}; migrate, then save as JSON.</li>
     *   <li>If the JSON file exists but is corrupt, log a warning and start fresh.</li>
     *   <li>If nothing works, create and persist factory defaults.</li>
     * </ol>
     *
     * @throws IOException only if writing the initial/fresh defaults fails
     */
    public static Settings load() throws IOException {
        File jsonFile = getFile();

        if (jsonFile.exists()) {
            Settings s = tryLoadJson(jsonFile);
            if (s != null) return s;
            // File exists but is corrupt — start fresh and overwrite
            System.err.println("[ServerGUI] servergui.settings.json could not be parsed; using defaults.");
            Settings defaults = defaultSettings();
            save(defaults);
            return defaults;
        }

        // No JSON yet — attempt one-time migration from the first readable legacy binary file
        for (String name : LEGACY_FILES) {
            File legacy = new File(name);
            if (!legacy.exists()) continue;
            Settings migrated = tryLoadLegacy(legacy);
            if (migrated != null) {
                Theme resolved = Theme.resolveForCurrentPlatform(migrated.getTheme());
                if (resolved != migrated.getTheme()) {
                    migrated = migrated.toBuilder().theme(resolved).build();
                }
                save(migrated);
                legacy.delete();
                return migrated;
            }
        }

        // Nothing found — write and return factory defaults
        Settings defaults = defaultSettings();
        save(defaults);
        return defaults;
    }

    // -------------------------------------------------------------------------
    // Serialization helpers (package-visible for testing)
    // -------------------------------------------------------------------------

    static Json toJson(Settings s) {
        return Json.object()
                .set("theme",                     s.getTheme().name())
                .set("fontSize",                  s.getFontSize())
                .set("consoleDarkMode",           s.isConsoleDarkMode())
                .set("consoleColorsEnabled",      s.isConsoleColorsEnabled())
                .set("openFilesInSystemDefault",  s.isOpenFilesInSystemDefault())
                .set("fileEditorTheme",           s.getFileEditorTheme())
                .set("manualConsoleScrollSticky", s.isManualConsoleScrollSticky())
                .set("serverButtonsUseText",      s.isServerButtonsUseText())
                .set("shutdownCountdownSeconds",  s.getShutdownCountdownSeconds())
                .set("consoleWrapWordBreakOnly",  s.isConsoleWrapWordBreakOnly())
                .set("accentColorRgb",            s.getAccentColorRgb())
                .set("displayKillButton",         s.isDisplayKillButton())
                .set("serverSettings",            toJsonSS(s.getServerSettings()));
    }

    static Json toJsonSS(ServerSettings ss) {
        if (ss == null) ss = ServerSettings.getDefault();
        return Json.object()
                .set("minRam",         toInt(ss.getMinRam(), 1024))
                .set("maxRam",         toInt(ss.getMaxRam(), 1024))
                .set("customArgs",     ss.getCustomArgs()    != null ? ss.getCustomArgs()    : "")
                .set("customSwitches", ss.getCustomSwitches()!= null ? ss.getCustomSwitches(): "")
                .set("jarFile",        ss.getJarFile()       != null ? ss.getJarFile().getPath() : "server.jar")
                .set("customJvmPath",  ss.getCustomJvmPath() != null ? ss.getCustomJvmPath() : "");
    }

    static Settings fromJson(Json root) {
        if (root == null || root.isNull()) return defaultSettings();

        Theme theme = Theme.getDefaultForPlatform();
        String themeStr = str(root, "theme", null);
        if (themeStr != null) {
            try { theme = Theme.valueOf(themeStr); } catch (IllegalArgumentException ignored) { }
        }
        theme = Theme.resolveForCurrentPlatform(theme);

        int accentColorRgb = num(root, "accentColorRgb", 0x0096E6);
        if (accentColorRgb == 0) accentColorRgb = 0x0096E6;

        return new Settings.Builder()
                .serverSettings(parseServerSettings(root.at("serverSettings")))
                .theme(theme)
                .fontSize(num(root, "fontSize", 13))
                .consoleDarkMode(bool(root, "consoleDarkMode", false))
                .consoleColorsEnabled(bool(root, "consoleColorsEnabled", true))
                .openFilesInSystemDefault(bool(root, "openFilesInSystemDefault", false))
                .fileEditorTheme(str(root,   "fileEditorTheme", "default"))
                .manualConsoleScrollSticky(bool(root, "manualConsoleScrollSticky", false))
                .serverButtonsUseText(bool(root, "serverButtonsUseText", false))
                .shutdownCountdownSeconds(num(root,  "shutdownCountdownSeconds", 0))
                .consoleWrapWordBreakOnly(bool(root, "consoleWrapWordBreakOnly", false))
                .accentColorRgb(accentColorRgb)
                .displayKillButton(bool(root, "displayKillButton", false))
                .build();
    }

    static ServerSettings parseServerSettings(Json obj) {
        if (obj == null || obj.isNull()) return ServerSettings.getDefault();
        int    minRam        = num(obj, "minRam",         1024);
        int    maxRam        = num(obj, "maxRam",         1024);
        String customArgs    = str(obj, "customArgs",     "");
        String customSwitches= str(obj, "customSwitches", "");
        String jarPath       = str(obj, "jarFile",        "server.jar");
        File   jarFile       = new File(jarPath != null && !jarPath.isEmpty() ? jarPath : "server.jar");
        String jvmPath       = str(obj, "customJvmPath", null);
        if (jvmPath != null && jvmPath.isEmpty()) jvmPath = null;
        return new ServerSettings(minRam, maxRam, customArgs, customSwitches, jarFile, jvmPath);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static Settings tryLoadJson(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return fromJson(Json.read(content));
        } catch (Exception e) {
            return null;
        }
    }

    private static Settings tryLoadLegacy(File file) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (Settings) in.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static Settings defaultSettings() {
        return new Settings.Builder().build();
    }

    private static int toInt(Object val, int def) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String)  { try { return Integer.parseInt((String) val); } catch (Exception ignored) { } }
        return def;
    }

    private static int num(Json obj, String key, int def) {
        try { Json v = obj.at(key); return (v == null || v.isNull()) ? def : v.asInteger(); }
        catch (Exception e) { return def; }
    }

    private static boolean bool(Json obj, String key, boolean def) {
        try { Json v = obj.at(key); return (v == null || v.isNull()) ? def : v.asBoolean(); }
        catch (Exception e) { return def; }
    }

    private static String str(Json obj, String key, String def) {
        try { Json v = obj.at(key); return (v == null || v.isNull()) ? def : v.asString(); }
        catch (Exception e) { return def; }
    }

    /**
     * Simple JSON pretty-printer. Handles string literals (including escape
     * sequences) so that structural characters inside strings are left untouched.
     */
    private static String prettyJson(String compact) {
        StringBuilder sb      = new StringBuilder(compact.length() + 256);
        int           indent  = 0;
        boolean       inStr   = false;
        boolean       escaped = false;

        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (inStr) {
                if (c == '\\') escaped = true;
                else if (c == '"') inStr = false;
                sb.append(c);
                continue;
            }

            switch (c) {
                case '"':
                    inStr = true;
                    sb.append(c);
                    break;
                case '{': case '[':
                    sb.append(c).append('\n');
                    indent(sb, ++indent);
                    break;
                case '}': case ']':
                    sb.append('\n');
                    indent(sb, --indent);
                    sb.append(c);
                    break;
                case ',':
                    sb.append(c).append('\n');
                    indent(sb, indent);
                    break;
                case ':':
                    sb.append(": ");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth * 2; i++) sb.append(' ');
    }
}
