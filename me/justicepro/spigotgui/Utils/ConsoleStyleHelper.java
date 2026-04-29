package me.justicepro.spigotgui.Utils;

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Appends console lines to a JTextPane with support for ANSI escape codes
 * and Minecraft § color codes. Uses a monospace Unicode-friendly font.
 */
public final class ConsoleStyleHelper {

    /** Custom document attribute: Integer (original RGB) for this run, so we can restore color when "disable colors" is toggled off. */
    public static final Object CANONICAL_ORIGINAL_RGB = new String("ConsoleStyleHelper.canonicalOriginalRGB");
    /** Attribute key for clickable links: value is the URL String. When present, text is underlined and click opens the URL. */
    public static final Object LINK_URL = new String("ConsoleStyleHelper.linkUrl");

    /** When true, console text wraps only at word boundaries (spaces); when false (default), wraps at any character so long lines never need horizontal scroll. */
    private static volatile boolean consoleWrapWordBreakOnly = false;

    public static void setConsoleWrapWordBreakOnly(boolean wordBreakOnly) {
        consoleWrapWordBreakOnly = wordBreakOnly;
    }

    public static boolean isConsoleWrapWordBreakOnly() {
        return consoleWrapWordBreakOnly;
    }

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+");

    /**
     * Matches a log-level token that is preceded by '/', '[', or ' ' and followed by ']' or ':'.
     * Only applied to the first {@link #LOG_LEVEL_SCAN_LIMIT} characters of each line.
     */
    private static final Pattern LOG_LEVEL_PATTERN = Pattern.compile(
        "(?<=[/\\[ ])(WARN(?:ING)?|ERROR|SEVERE|FATAL)(?=[\\]:])"
    );
    /** How far into a line to scan for a level token (covers even long thread names). */
    private static final int LOG_LEVEL_SCAN_LIMIT = 50;

    /** Default text color for light background - black so it's readable on white */
    private static final Color DEFAULT_FG_LIGHT = Color.BLACK;
    /** Default text color for dark background */
    private static final Color DEFAULT_FG_DARK = new Color(0xe0, 0xe0, 0xe0);

    /** Luminance above this is "light" - will be darkened when background is light */
    private static final double LIGHT_THRESHOLD = 0.55;
    /** Luminance below this is "dark" - will be lightened when background is dark */
    private static final double DARK_THRESHOLD = 0.45;

    /** ANSI base colors (index 30–37 are foreground, 90–97 are bright) */
    private static final Color[] ANSI_COLORS = {
        new Color(0x00, 0x00, 0x00), // 30 black
        new Color(0xcd, 0x00, 0x00), // 31 red
        new Color(0x00, 0xcd, 0x00), // 32 green
        new Color(0xcd, 0xcd, 0x00), // 33 yellow
        new Color(0x00, 0x00, 0xee), // 34 blue
        new Color(0xcd, 0x00, 0xcd), // 35 magenta
        new Color(0x00, 0xcd, 0xcd), // 36 cyan
        new Color(0xe5, 0xe5, 0xe5), // 37 white
        new Color(0x4d, 0x4d, 0x4d), // 90 bright black
        new Color(0xff, 0x00, 0x00), // 91 bright red
        new Color(0x00, 0xff, 0x00), // 92 bright green
        new Color(0xff, 0xff, 0x00), // 93 bright yellow
        new Color(0x00, 0x00, 0xff), // 94 bright blue
        new Color(0xff, 0x00, 0xff), // 95 bright magenta
        new Color(0x00, 0xff, 0xff), // 96 bright cyan
        new Color(0xff, 0xff, 0xff), // 97 bright white
    };

    /** Minecraft § color codes 0-9, a-f mapped to colors (dark then light) */
    private static final Color[] MC_COLORS = {
        new Color(0x00, 0x00, 0x00), // §0 black
        new Color(0x00, 0x00, 0xaa), // §1 dark_blue
        new Color(0x00, 0xaa, 0x00), // §2 dark_green
        new Color(0x00, 0xaa, 0xaa), // §3 dark_aqua
        new Color(0xaa, 0x00, 0x00), // §4 dark_red
        new Color(0xaa, 0x00, 0xaa), // §5 dark_purple
        new Color(0xff, 0xaa, 0x00), // §6 gold
        new Color(0xaa, 0xaa, 0xaa), // §7 gray
        new Color(0x55, 0x55, 0x55), // §8 dark_gray
        new Color(0x55, 0x55, 0xff), // §9 blue
        new Color(0x55, 0xff, 0x55), // §a green
        new Color(0x55, 0xff, 0xff), // §b aqua
        new Color(0xff, 0x55, 0x55), // §c red
        new Color(0xff, 0x55, 0xff), // §d light_purple
        new Color(0xff, 0xff, 0x55), // §e yellow
        new Color(0xff, 0xff, 0xff), // §f white
    };

    private final JTextPane textPane;
    private Font baseFont;
    private final int maxDocLength;

    private Color defaultFg;
    private Color defaultBg;
    private Color currentFg;
    private boolean currentBold;
    private boolean currentItalic;
    private boolean currentUnderline;
    private boolean currentStrikethrough;
    /** When set, next flush will store this original RGB as CANONICAL_ORIGINAL_RGB so we can restore color when re-enabling. */
    private Integer currentCanonicalRGB;
    private boolean colorsEnabled = true;
    private boolean adaptiveColors = true;

    /** Original color RGB -> (dark-bg version, light-bg version). New colors are added as we see them. */
    private final Map<Integer, ColorPair> canonicalMap = new HashMap<>();
    /** Display color RGB -> (original RGB, true if this is the dark version). Used to swap on toggle. */
    private final Map<Integer, DisplayEntry> displayToCanonical = new HashMap<>();

    private static final class ColorPair {
        final Color darkColor;
        final Color lightColor;
        ColorPair(Color darkColor, Color lightColor) {
            this.darkColor = darkColor;
            this.lightColor = lightColor;
        }
    }

    private static final class DisplayEntry {
        final int originalRGB;
        final boolean isDarkVersion;
        DisplayEntry(int originalRGB, boolean isDarkVersion) {
            this.originalRGB = originalRGB;
            this.isDarkVersion = isDarkVersion;
        }
    }

    public ConsoleStyleHelper(JTextPane textPane, Font baseFont, boolean darkBackground, int maxDocLength) {
        this.textPane = textPane;
        this.baseFont = baseFont;
        this.maxDocLength = maxDocLength > 0 ? maxDocLength : 1_000_000;
        setDarkMode(darkBackground);
        this.currentFg = defaultFg;
        this.currentBold = false;
    }

    /** Switch console between dark and light background; swap all colors to the other version via the canonical map. */
    public void setDarkMode(boolean darkBackground) {
        Color oldDefaultFg = this.defaultFg;
        this.defaultFg = darkBackground ? DEFAULT_FG_DARK : DEFAULT_FG_LIGHT;
        this.defaultBg = darkBackground ? new Color(0x1e, 0x1e, 0x1e) : Color.WHITE;
        this.currentFg = defaultFg;
        this.textPane.setBackground(defaultBg);
        this.textPane.setForeground(defaultFg);
        swapAllColorsForNewMode(oldDefaultFg, defaultFg);
    }

    /** For each run: default -> new default; otherwise look up in map and swap to the other (dark/light) version. Preserves CANONICAL_ORIGINAL_RGB so colors survive later color toggle. */
    private void swapAllColorsForNewMode(Color oldDefaultFg, Color newDefaultFg) {
        StyledDocument doc = textPane.getStyledDocument();
        try {
            int len = doc.getLength();
            int offset = 0;
            while (offset < len) {
                Element elem = doc.getCharacterElement(offset);
                int start = elem.getStartOffset();
                int runLen = elem.getEndOffset() - start;
                Object fg = elem.getAttributes().getAttribute(StyleConstants.Foreground);
                if (!(fg instanceof Color)) {
                    offset = elem.getEndOffset();
                    continue;
                }
                Color current = (Color) fg;
                Color replacement = null;
                if (current.equals(oldDefaultFg)) {
                    replacement = newDefaultFg;
                } else {
                    DisplayEntry entry = displayToCanonical.get(current.getRGB() & 0x00FFFFFF);
                    if (entry != null) {
                        ColorPair pair = canonicalMap.get(entry.originalRGB);
                        if (pair != null) {
                            replacement = entry.isDarkVersion ? pair.lightColor : pair.darkColor;
                        }
                    }
                }
                if (replacement != null && !replacement.equals(current)) {
                    SimpleAttributeSet attr = new SimpleAttributeSet();
                    StyleConstants.setForeground(attr, replacement);
                    Object canonicalRGB = elem.getAttributes().getAttribute(CANONICAL_ORIGINAL_RGB);
                    if (canonicalRGB != null) {
                        attr.addAttribute(CANONICAL_ORIGINAL_RGB, canonicalRGB);
                    }
                    doc.setCharacterAttributes(start, runLen, attr, false);
                }
                offset = elem.getEndOffset();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public Color getDefaultBackground() {
        return defaultBg;
    }

    public Color getDefaultForeground() {
        return defaultFg;
    }

    public void setBaseFont(Font font) {
        this.baseFont = font != null ? font : this.baseFont;
    }

    /** Enable or disable console colors. When disabled, all text shows in default fg (black/white); canonical colors are kept so toggling back restores them. */
    public void setColorsEnabled(boolean enabled) {
        if (this.colorsEnabled == enabled) return;
        this.colorsEnabled = enabled;
        refreshDocumentColors();
    }

    /** Enable or disable background-adaptive color adjustment. When enabled, colors are adjusted for readability on the current background. When disabled, colors are displayed exactly as received. */
    public void setAdaptiveColors(boolean enabled) {
        if (this.adaptiveColors == enabled) return;
        this.adaptiveColors = enabled;
        refreshDocumentColors();
    }

    /** Walk the document and set each run's Foreground reflecting both colorsEnabled and adaptiveColors. */
    private void refreshDocumentColors() {
        StyledDocument doc = textPane.getStyledDocument();
        try {
            int len = doc.getLength();
            int offset = 0;
            while (offset < len) {
                Element elem = doc.getCharacterElement(offset);
                int start = elem.getStartOffset();
                int runLen = elem.getEndOffset() - start;
                Object canonObj = elem.getAttributes().getAttribute(CANONICAL_ORIGINAL_RGB);
                Color newFg;
                if (colorsEnabled && canonObj instanceof Integer) {
                    int originalRGB = (Integer) canonObj;
                    if (adaptiveColors) {
                        ColorPair pair = canonicalMap.get(originalRGB);
                        newFg = pair != null ? (isLightBackground() ? pair.lightColor : pair.darkColor) : defaultFg;
                    } else {
                        newFg = new Color(originalRGB);
                    }
                } else {
                    newFg = defaultFg;
                }
                SimpleAttributeSet attr = new SimpleAttributeSet();
                StyleConstants.setForeground(attr, newFg);
                if (canonObj != null) {
                    attr.addAttribute(CANONICAL_ORIGINAL_RGB, canonObj);
                }
                doc.setCharacterAttributes(start, runLen, attr, false);
                offset = elem.getEndOffset();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Append a line of text to the console, parsing ANSI and § codes and applying styles.
     */
    public void appendLine(String line) {
        if (line == null) line = "";
        line = injectLevelColor(line);
        StyledDocument doc = textPane.getStyledDocument();
        try {
            trimDocumentIfNeeded(doc);
            currentFg = defaultFg;
            currentBold = false;
            currentCanonicalRGB = null;

            StringBuilder plain = new StringBuilder();
            int i = 0;
            final int len = line.length();

            while (i < len) {
                // ANSI escape: ESC [
                if (i + 2 <= len && (line.charAt(i) == '\u001B' || line.charAt(i) == '\033') && line.charAt(i + 1) == '[') {
                    int bracket = i + 2; // first char after '['
                    int end = bracket;
                    while (end < len && line.charAt(end) != 'm') end++;
                    if (end < len) {
                        flush(plain, doc);
                        String code = line.substring(bracket, end);
                        applyAnsi(code);
                        i = end + 1;
                        continue;
                    }
                }

                // Minecraft § code or & code (e.g. §c or &c for red)
                if (i + 1 < len) {
                    char codeChar = Character.toLowerCase(line.charAt(i + 1));
                    if (line.charAt(i) == '\u00A7' || (line.charAt(i) == '&' && isMcColorCode(codeChar))) {
                        flush(plain, doc);
                        if (applyMcCode(codeChar)) {
                            i += 2;
                            continue;
                        }
                    }
                }

                plain.append(line.charAt(i));
                i++;
            }

            flush(plain, doc);
            doc.insertString(doc.getLength(), "\n", null);
        } catch (BadLocationException e) {
            // ignore
        }
    }

    private void flush(StringBuilder plain, StyledDocument doc) throws BadLocationException {
        if (plain.length() == 0) return;
        String s = plain.toString();
        plain.setLength(0);
        SimpleAttributeSet baseAttrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(baseAttrs, Font.MONOSPACED);
        StyleConstants.setFontSize(baseAttrs, baseFont.getSize());
        StyleConstants.setBold(baseAttrs, currentBold);
        StyleConstants.setItalic(baseAttrs, currentItalic);
        StyleConstants.setUnderline(baseAttrs, currentUnderline);
        StyleConstants.setStrikeThrough(baseAttrs, currentStrikethrough);
        Color displayColor = colorsEnabled ? currentFg : defaultFg;
        StyleConstants.setForeground(baseAttrs, displayColor);
        if (currentCanonicalRGB != null) {
            baseAttrs.addAttribute(CANONICAL_ORIGINAL_RGB, currentCanonicalRGB);
        }
        Matcher m = URL_PATTERN.matcher(s);
        int lastEnd = 0;
        while (m.find()) {
            if (m.start() > lastEnd) {
                doc.insertString(doc.getLength(), s.substring(lastEnd, m.start()), baseAttrs);
            }
            String url = m.group();
            while (url.length() > 0 && ".,;:)!?'\"]".indexOf(url.charAt(url.length() - 1)) >= 0) {
                url = url.substring(0, url.length() - 1);
            }
            SimpleAttributeSet linkAttrs = new SimpleAttributeSet(baseAttrs);
            StyleConstants.setUnderline(linkAttrs, true);
            linkAttrs.addAttribute(LINK_URL, url);
            doc.insertString(doc.getLength(), s.substring(m.start(), m.start() + url.length()), linkAttrs);
            if (m.end() > m.start() + url.length()) {
                doc.insertString(doc.getLength(), s.substring(m.start() + url.length(), m.end()), baseAttrs);
            }
            lastEnd = m.end();
        }
        if (lastEnd < s.length()) {
            doc.insertString(doc.getLength(), s.substring(lastEnd), baseAttrs);
        }
    }

    private void applyAnsi(String code) {
        if (code.isEmpty()) {
            currentFg = defaultFg;
            currentBold = false;
            return;
        }
        String[] parts = code.split(";");
        for (String part : parts) {
            int n;
            try {
                n = Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (n == 0) {
                currentFg = defaultFg;
                currentBold = false;
                currentCanonicalRGB = null;
            } else if (n == 1) {
                currentBold = true;
            } else if (n == 22) {
                currentBold = false;
            } else if (n >= 30 && n <= 37) {
                int idx = n - 30;
                currentFg = ansiColorForMode(idx, false);
                currentCanonicalRGB = ANSI_COLORS[idx].getRGB() & 0x00FFFFFF;
            } else if (n >= 90 && n <= 97) {
                int idx = n - 90;
                currentFg = ansiColorForMode(idx, true);
                currentCanonicalRGB = ANSI_COLORS[8 + idx].getRGB() & 0x00FFFFFF;
            }
        }
    }

    /** Get the display color for this original: always populate the canonical map, then return adapted or raw color depending on adaptiveColors flag. */
    private Color getDisplayColorForOriginal(Color original) {
        if (original == null) return null;
        ColorPair pair = getOrCreateCanonicalPair(original); // always build map for later use
        if (!adaptiveColors) return original;
        return isLightBackground() ? pair.lightColor : pair.darkColor;
    }

    private ColorPair getOrCreateCanonicalPair(Color original) {
        int key = original.getRGB() & 0x00FFFFFF;
        ColorPair pair = canonicalMap.get(key);
        if (pair == null) {
            Color darkVersion = adaptForDarkBg(original);
            Color lightVersion = adaptForLightBg(original);
            pair = new ColorPair(darkVersion, lightVersion);
            canonicalMap.put(key, pair);
            displayToCanonical.put(darkVersion.getRGB() & 0x00FFFFFF, new DisplayEntry(key, true));
            displayToCanonical.put(lightVersion.getRGB() & 0x00FFFFFF, new DisplayEntry(key, false));
        }
        return pair;
    }

    private static Color adaptForDarkBg(Color c) {
        if (luminance(c) <= DARK_THRESHOLD) return lighten(c);
        return c;
    }

    private static Color adaptForLightBg(Color c) {
        if (luminance(c) >= LIGHT_THRESHOLD) return darken(c);
        return c;
    }

    private boolean isLightBackground() {
        int rgb = defaultBg.getRGB() & 0x00FFFFFF;
        return rgb == 0xFFFFFF || rgb == Color.WHITE.getRGB();
    }

    private static double luminance(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
    }

    private static Color darken(Color c) {
        return new Color(
            Math.max(0, (int) (c.getRed() * 0.5)),
            Math.max(0, (int) (c.getGreen() * 0.5)),
            Math.max(0, (int) (c.getBlue() * 0.5))
        );
    }

    private static Color lighten(Color c) {
        return new Color(
            Math.min(255, c.getRed() + (int) ((255 - c.getRed()) * 0.55)),
            Math.min(255, c.getGreen() + (int) ((255 - c.getGreen()) * 0.55)),
            Math.min(255, c.getBlue() + (int) ((255 - c.getBlue()) * 0.55))
        );
    }

    private Color ansiColorForMode(int index, boolean bright) {
        Color original = ANSI_COLORS[bright ? 8 + index : index];
        return getDisplayColorForOriginal(original);
    }

    private static boolean isMcColorCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == 'r' || c == 'l' || c == 'o' || c == 'n' || c == 'm' || c == 'k';
    }

    private boolean applyMcCode(char c) {
        if (c >= '0' && c <= '9') {
            Color orig = MC_COLORS[c - '0'];
            currentFg = getDisplayColorForOriginal(orig);
            currentCanonicalRGB = orig.getRGB() & 0x00FFFFFF;
            return true;
        }
        if (c >= 'a' && c <= 'f') {
            Color orig = MC_COLORS[10 + (c - 'a')];
            currentFg = getDisplayColorForOriginal(orig);
            currentCanonicalRGB = orig.getRGB() & 0x00FFFFFF;
            return true;
        }
        if (c == 'r') {
            currentFg = defaultFg;
            currentBold = false;
            currentItalic = false;
            currentUnderline = false;
            currentStrikethrough = false;
            currentCanonicalRGB = null;
            return true;
        }
        if (c == 'l') {
            currentBold = true;
            return true;
        }
        if (c == 'o') {
            currentItalic = true;
            return true;
        }
        if (c == 'n') {
            currentUnderline = true;
            return true;
        }
        if (c == 'm') {
            currentStrikethrough = true;
            return true;
        }
        if (c == 'k') {
            // obfuscated - not rendered
            return true;
        }
        return false;
    }

    /**
     * If the line has no existing ANSI codes, scans the first {@link #LOG_LEVEL_SCAN_LIMIT}
     * characters for a level token (WARN, WARNING, ERROR, SEVERE, FATAL) preceded by
     * '/', '[', or ' ' and followed by ']' or ':'. When found, wraps only that token
     * in an ANSI color sequence followed by a reset, leaving all surrounding text unstyled.
     * Lines that already contain ANSI codes are returned unchanged.
     */
    private String injectLevelColor(String line) {
        if (line.indexOf('\u001B') >= 0) return line; // server already sent ANSI, leave it alone
        int scanEnd = Math.min(line.length(), LOG_LEVEL_SCAN_LIMIT);
        Matcher m = LOG_LEVEL_PATTERN.matcher(line);
        m.region(0, scanEnd);
        if (!m.find()) return line;
        String level = m.group();
        // WARN / WARNING -> yellow; ERROR / SEVERE / FATAL -> bright red
        String ansi = (level.charAt(0) == 'W') ? "\u001B[33m" : "\u001B[91m";
        return line.substring(0, m.start()) + ansi + level + "\u001B[0m" + line.substring(m.end());
    }

    private void trimDocumentIfNeeded(StyledDocument doc) throws BadLocationException {
        int len = doc.getLength();
        if (len > maxDocLength) {
            doc.remove(0, len - maxDocLength / 2);
        }
    }
}
