package me.justicepro.spigotgui.FileExplorer;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.formdev.flatlaf.FlatLaf;

import me.justicepro.spigotgui.Theme;

/**
 * Applies a bundled editor color scheme to an RSyntaxTextArea.
 * Syntax token colors come from a scheme XML file packaged under
 * editor-schemes/ in resources.
 * Chrome colors (background, selection, caret) prefer UIManager values so they
 * always track
 * the active FlatLaf theme; gutter colors use scheme values when the theme has
 * an explicit scheme.
 *
 * <p>
 * XML format (editor-schemes/*.xml):
 * 
 * <pre>{@code
 * <scheme name="..." dark="true|false">
 *   <background>RRGGBB</background>
 *   <foreground>RRGGBB</foreground>
 *   <caret>RRGGBB</caret>
 *   <selection-background>RRGGBB</selection-background>
 *   <selection-foreground>RRGGBB</selection-foreground>  <!-- optional -->
 *   <line-highlight [alpha="HH"]>RRGGBB</line-highlight> <!-- alpha is hex 00-FF -->
 *   <gutter-background>RRGGBB</gutter-background>
 *   <gutter-foreground>RRGGBB</gutter-foreground>
 *   <token type="keyword"  fg="RRGGBB" [bold="true"] [italic="true"]/>
 *   ...
 * </scheme>
 * }</pre>
 *
 * <p>
 * Token types: keyword, keyword2, comment, comment-keyword, string, number,
 * function,
 * variable, type, regex, annotation, operator, preprocessor,
 * markup-tag, markup-attr, markup-attr-value, error.
 */
public class EditorSchemeApplier {

    private static final String SCHEMES_ROOT = "/editor-schemes/";

    // ---- internal data classes ----

    /**
     * Holds every color value parsed from a single scheme XML file.
     * All fields are nullable — callers fall back to UIManager or skip the
     * property entirely when a value is absent.
     */
    private static class SchemeColors {
        Color background, foreground, caret;
        Color selectionBackground, selectionForeground;
        Color lineHighlight;
        Color gutterBackground, gutterForeground;
        Map<String, TokenStyle> tokens = new HashMap<>();
    }

    /**
     * Holds the foreground color and font modifiers for a single token type entry
     * parsed from a {@code <token>} element in the scheme XML.
     */
    private static class TokenStyle {
        final Color fg;
        final boolean bold, italic;

        TokenStyle(Color fg, boolean bold, boolean italic) {
            this.fg = fg;
            this.bold = bold;
            this.italic = italic;
        }
    }

    // ---- public API ----

    /**
     * Apply the editor scheme for the given application theme to the text area.
     * The text area must already be the view of an {@link RTextScrollPane} so that
     * the gutter can be reached via the parent chain.
     *
     * @param textArea the RSyntaxTextArea to style
     * @param appTheme the currently active application theme, or {@code null}
     */
    public static void apply(RSyntaxTextArea textArea, Theme appTheme) {
        String schemeName = (appTheme != null) ? appTheme.getEditorScheme() : null;
        boolean hasExplicitScheme = (schemeName != null);
        if (schemeName == null) {
            schemeName = fallbackScheme();
        }
        SchemeColors colors = loadScheme(schemeName);
        if (colors == null) {
            colors = loadScheme(fallbackScheme());
        }
        if (colors == null)
            return;

        applyChrome(textArea, colors, hasExplicitScheme);
        applySyntaxScheme(textArea, colors);
        textArea.repaint();
    }

    // ---- scheme resolution ----

    /**
     * Returns the resource name of the generic fallback scheme to use when the
     * active application theme has no specific editor scheme assigned.
     * Chooses {@code "darcula"} for dark LaFs and {@code "intellij-light"} for
     * light ones, falling back to {@code "intellij-light"} if FlatLaf is not
     * active (e.g. system or legacy theme).
     *
     * @return scheme resource name, never {@code null}
     */
    private static String fallbackScheme() {
        try {
            return FlatLaf.isLafDark() ? "darcula" : "intellij-light";
        } catch (Throwable ignored) {
            // FlatLaf not active; assume light.
            return "intellij-light";
        }
    }

    // ---- loading and parsing ----

    /**
     * Loads and parses the scheme XML at {@code /editor-schemes/<name>.xml} from
     * the classpath.
     *
     * @param name  the scheme resource name (e.g. {@code "darcula"})
     * @return the parsed {@link SchemeColors}, or {@code null} if the resource
     *         does not exist or cannot be parsed
     */
    private static SchemeColors loadScheme(String name) {
        String path = SCHEMES_ROOT + name + ".xml";
        try (InputStream in = EditorSchemeApplier.class.getResourceAsStream(path)) {
            if (in == null)
                return null;
            return parseScheme(in);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Deserializes a scheme XML stream into a {@link SchemeColors} object.
     * The parser is hardened against XXE injection even though these files are
     * bundled resources.
     *
     * @param in  an open stream positioned at the start of a valid scheme XML
     * @return the populated {@link SchemeColors}
     * @throws Exception if the XML is malformed or the parser cannot be configured
     */
    private static SchemeColors parseScheme(InputStream in) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // Harden against XXE; the files are internal resources but defence-in-depth.
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(in);
        Element root = doc.getDocumentElement();
        root.normalize();

        SchemeColors c = new SchemeColors();
        c.background = parseTextContent(root, "background");
        c.foreground = parseTextContent(root, "foreground");
        c.caret = parseTextContent(root, "caret");
        c.selectionBackground = parseTextContent(root, "selection-background");
        c.selectionForeground = parseTextContent(root, "selection-foreground");
        c.gutterBackground = parseTextContent(root, "gutter-background");
        c.gutterForeground = parseTextContent(root, "gutter-foreground");
        c.lineHighlight = parseLineHighlight(root, "line-highlight");

        NodeList tokens = root.getElementsByTagName("token");
        for (int i = 0; i < tokens.getLength(); i++) {
            Element tok = (Element) tokens.item(i);
            String type = tok.getAttribute("type");
            String fgHex = tok.getAttribute("fg");
            if (type.isEmpty() || fgHex.isEmpty())
                continue;
            Color fg = parseColor(fgHex);
            if (fg == null)
                continue;
            boolean bold = "true".equalsIgnoreCase(tok.getAttribute("bold"));
            boolean italic = "true".equalsIgnoreCase(tok.getAttribute("italic"));
            c.tokens.put(type, new TokenStyle(fg, bold, italic));
        }
        return c;
    }

    /**
     * Finds the first child element with the given tag name and parses its text
     * content as a hex color.
     *
     * @param root  the scheme root element to search within
     * @param tag   the XML element name to look for (e.g. {@code "background"})
     * @return the parsed {@link Color}, or {@code null} if the element is absent
     *         or the value cannot be parsed
     */
    private static Color parseTextContent(Element root, String tag) {
        NodeList nl = root.getElementsByTagName(tag);
        if (nl.getLength() == 0)
            return null;
        return parseColor(nl.item(0).getTextContent().trim());
    }

    /**
     * Parses the {@code <line-highlight>} element, which supports an optional
     * {@code alpha} attribute to make the highlight semi-transparent.
     * This lets themes show a subtle tint over the current line without
     * completely obscuring the editor background.
     *
     * @param root  the scheme root element
     * @param tag   the element name ({@code "line-highlight"})
     * @return the parsed color (possibly with alpha), or {@code null} if absent
     */
    private static Color parseLineHighlight(Element root, String tag) {
        NodeList nl = root.getElementsByTagName(tag);
        if (nl.getLength() == 0)
            return null;
        Element el = (Element) nl.item(0);
        Color base = parseColor(el.getTextContent().trim());
        if (base == null)
            return null;
        String alphaStr = el.getAttribute("alpha");
        if (!alphaStr.isEmpty()) {
            try {
                // alpha is a two-digit hex value, e.g. "50" = 0x50 = 80 out of 255.
                int alpha = Integer.parseInt(alphaStr, 16);
                return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
            } catch (NumberFormatException ignored) {
            }
        }
        return base;
    }

    /**
     * Parses a hex color string into a {@link Color}.
     * Accepts 6-digit ({@code RRGGBB}) and 8-digit ({@code AARRGGBB}) forms,
     * with or without a leading {@code #}.
     *
     * @param hex  the hex color string to parse
     * @return the parsed {@link Color}, or {@code null} if the input is blank or
     *         does not match either expected format
     */
    private static Color parseColor(String hex) {
        if (hex == null || hex.isEmpty())
            return null;
        hex = hex.replace("#", "").trim();
        try {
            if (hex.length() == 6)
                return new Color(Integer.parseInt(hex, 16));
            if (hex.length() == 8)
                return new Color((int) Long.parseLong(hex, 16), true); // AARRGGBB
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    // ---- applying chrome ----

    /**
     * Applies the non-syntax "chrome" colors to the text area: background,
     * foreground, selection, caret, current-line highlight, matched-bracket
     * highlight, and gutter.
     *
     * <p>Background, foreground, selection, and caret are resolved from
     * {@link UIManager} first so they always track the active FlatLaf theme;
     * the scheme values are used only as a fallback when UIManager returns
     * {@code null} (e.g. for non-FlatLaf look-and-feels).
     *
     * <p>Gutter colors follow different logic: when {@code preferSchemeGutter}
     * is {@code true} (i.e. the theme has an explicit scheme whose gutter was
     * hand-tuned against its editor background), the scheme values are used
     * directly.  Otherwise UIManager panel/label colors are preferred so the
     * gutter blends with the surrounding application chrome.
     *
     * @param textArea           the text area to style
     * @param colors             the parsed scheme colors
     * @param preferSchemeGutter {@code true} to use the scheme's own gutter
     *                           colors, {@code false} to prefer UIManager values
     */
    private static void applyChrome(RSyntaxTextArea textArea, SchemeColors colors, boolean preferSchemeGutter) {
        // Background, foreground, selection, caret: prefer UIManager so the editor
        // always matches the active FlatLaf chrome; fall back to scheme values.
        Color bg = firstNonNull(uiColor("EditorPane.background", "TextArea.background"), colors.background);
        if (bg != null)
            textArea.setBackground(bg);

        Color fg = firstNonNull(uiColor("EditorPane.foreground", "TextArea.foreground"), colors.foreground);
        if (fg != null)
            textArea.setForeground(fg);

        Color selBg = firstNonNull(
                uiColor("TextField.selectionBackground", "TextArea.selectionBackground"),
                colors.selectionBackground);
        if (selBg != null)
            textArea.setSelectionColor(selBg);

        if (colors.selectionForeground != null) {
            textArea.setSelectedTextColor(colors.selectionForeground);
        }

        Color caret = firstNonNull(
                uiColor("TextField.caretForeground", "TextArea.caretForeground"),
                colors.caret);
        if (caret != null)
            textArea.setCaretColor(caret);

        // Line highlight: from the scheme (specific to the token colour palette, not
        // generic chrome).
        if (colors.lineHighlight != null) {
            textArea.setHighlightCurrentLine(true);
            textArea.setCurrentLineHighlightColor(colors.lineHighlight);
        }

        // Matched bracket: tinted from the selection background for a consistent look.
        if (selBg != null) {
            textArea.setMatchedBracketBGColor(
                    new Color(selBg.getRed(), selBg.getGreen(), selBg.getBlue(), 100));
            textArea.setMatchedBracketBorderColor(selBg);
        }

        // Gutter colors. When the theme has an explicit scheme, trust that scheme's
        // gutter
        // colors (which are chosen to complement the editor background). For generic
        // fallback
        // schemes, prefer UIManager so the gutter matches the surrounding panel chrome.
        Container viewport = textArea.getParent();
        if (viewport instanceof JViewport) {
            Container sp = viewport.getParent();
            if (sp instanceof RTextScrollPane) {
                Gutter gutter = ((RTextScrollPane) sp).getGutter();
                Color gutterBg, gutterFg;
                if (preferSchemeGutter) {
                    gutterBg = colors.gutterBackground;
                    gutterFg = colors.gutterForeground;
                } else {
                    gutterBg = firstNonNull(uiColor("Panel.background"), colors.gutterBackground);
                    gutterFg = firstNonNull(uiColor("Label.disabledForeground", "Label.foreground"),
                            colors.gutterForeground);
                }
                if (gutterBg != null)
                    gutter.setBackground(gutterBg);
                if (gutterFg != null)
                    gutter.setLineNumberColor(gutterFg);
            }
        }
    }

    // ---- applying syntax scheme ----

    /**
     * Builds and applies a {@link SyntaxScheme} to the text area using the token
     * colors from {@code colors}.
     *
     * <p>A fresh {@link SyntaxScheme} is constructed from the text area's current
     * base font so that any token types not covered by the scheme XML retain
     * reasonable defaults.  Only the token groups defined in the XML are
     * overridden.
     *
     * <p>Plain identifiers, whitespace, and separators are explicitly set to the
     * editor foreground color so they match surrounding plain text rather than
     * inheriting the RSyntaxTextArea default (which can look odd against some
     * backgrounds).
     *
     * @param textArea  the text area to update
     * @param colors    the parsed scheme colors containing the token map
     */
    private static void applySyntaxScheme(RSyntaxTextArea textArea, SchemeColors colors) {
        Font base = textArea.getFont();
        // Pre-derive all three font variants once and reuse them across all token groups.
        Font bold = base.deriveFont(Font.BOLD);
        Font italic = base.deriveFont(Font.ITALIC);
        Font boldItalic = base.deriveFont(Font.BOLD | Font.ITALIC);

        // Start from a fresh scheme initialized with the base font defaults,
        // then override the token types we care about.
        SyntaxScheme scheme = new SyntaxScheme(base);

        // Default foreground, already set by applyChrome.
        Color fg = textArea.getForeground();

        // Plain identifiers, separators (parens/braces), and whitespace use the default
        // foreground so they render identically to surrounding prose text.
        styleTokens(scheme, fg, base,
                TokenTypes.IDENTIFIER,
                TokenTypes.WHITESPACE,
                TokenTypes.SEPARATOR);

        // ---- syntax token groups ----
        style(scheme, colors, "keyword", bold, italic, boldItalic,
                TokenTypes.RESERVED_WORD,
                TokenTypes.LITERAL_BOOLEAN);
        style(scheme, colors, "keyword2", bold, italic, boldItalic,
                TokenTypes.RESERVED_WORD_2,
                TokenTypes.DATA_TYPE);
        style(scheme, colors, "comment", bold, italic, boldItalic,
                TokenTypes.COMMENT_EOL,
                TokenTypes.COMMENT_MULTILINE,
                TokenTypes.COMMENT_DOCUMENTATION,
                TokenTypes.MARKUP_COMMENT,
                TokenTypes.COMMENT_MARKUP);
        style(scheme, colors, "comment-keyword", bold, italic, boldItalic,
                TokenTypes.COMMENT_KEYWORD);
        style(scheme, colors, "string", bold, italic, boldItalic,
                TokenTypes.LITERAL_STRING_DOUBLE_QUOTE,
                TokenTypes.LITERAL_CHAR,
                TokenTypes.LITERAL_BACKQUOTE,
                TokenTypes.MARKUP_CDATA);
        style(scheme, colors, "number", bold, italic, boldItalic,
                TokenTypes.LITERAL_NUMBER_DECIMAL_INT,
                TokenTypes.LITERAL_NUMBER_FLOAT,
                TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
        style(scheme, colors, "function", bold, italic, boldItalic,
                TokenTypes.FUNCTION);
        style(scheme, colors, "variable", bold, italic, boldItalic,
                TokenTypes.VARIABLE);
        style(scheme, colors, "regex", bold, italic, boldItalic,
                TokenTypes.REGEX);
        style(scheme, colors, "annotation", bold, italic, boldItalic,
                TokenTypes.ANNOTATION);
        style(scheme, colors, "operator", bold, italic, boldItalic,
                TokenTypes.OPERATOR);
        style(scheme, colors, "preprocessor", bold, italic, boldItalic,
                TokenTypes.PREPROCESSOR);
        style(scheme, colors, "markup-tag", bold, italic, boldItalic,
                TokenTypes.MARKUP_TAG_DELIMITER,
                TokenTypes.MARKUP_TAG_NAME,
                TokenTypes.MARKUP_DTD,
                TokenTypes.MARKUP_PROCESSING_INSTRUCTION,
                TokenTypes.MARKUP_CDATA_DELIMITER);
        style(scheme, colors, "markup-attr", bold, italic, boldItalic,
                TokenTypes.MARKUP_TAG_ATTRIBUTE,
                TokenTypes.MARKUP_ENTITY_REFERENCE);
        style(scheme, colors, "markup-attr-value", bold, italic, boldItalic,
                TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE);
        style(scheme, colors, "error", bold, italic, boldItalic,
                TokenTypes.ERROR_IDENTIFIER,
                TokenTypes.ERROR_NUMBER_FORMAT,
                TokenTypes.ERROR_STRING_DOUBLE,
                TokenTypes.ERROR_CHAR);

        textArea.setSyntaxScheme(scheme);
    }

    /**
     * Looks up a named token group in {@code colors} and applies its foreground
     * color and optional font modifiers to all of the specified RSyntaxTextArea
     * token type indices.
     * <p>Does nothing if the group name is not present in the scheme XML, allowing
     * partial schemes that only define a subset of token types.
     *
     * @param scheme      the scheme being built
     * @param colors      the parsed scheme data
     * @param typeName    the token group name from the XML (e.g. {@code "keyword"})
     * @param bold        pre-derived bold variant of the base font
     * @param italic      pre-derived italic variant of the base font
     * @param boldItalic  pre-derived bold+italic variant of the base font
     * @param tokenTypes  the RSyntaxTextArea {@link TokenTypes} constants to update
     */
    private static void style(SyntaxScheme scheme, SchemeColors colors, String typeName,
            Font bold, Font italic, Font boldItalic, int... tokenTypes) {
        TokenStyle ts = colors.tokens.get(typeName);
        if (ts == null)
            return;
        // Resolve the font variant; null means leave the base font from SyntaxScheme.
        Font font = ts.bold && ts.italic ? boldItalic
                : ts.bold ? bold
                        : ts.italic ? italic
                                : null;
        styleTokens(scheme, ts.fg, font, tokenTypes);
    }

    /**
     * Sets the foreground color and (when non-null) the font on one or more
     * individual {@link Style} entries in a {@link SyntaxScheme}.
     * <p>Out-of-range token type indices are silently skipped so callers do not
     * need to guard against future RSyntaxTextArea version changes.
     *
     * @param scheme  the scheme whose styles array will be mutated
     * @param fg      foreground color to set, or {@code null} to leave unchanged
     * @param font    font to set, or {@code null} to leave unchanged
     * @param types   {@link TokenTypes} constants identifying the styles to update
     */
    private static void styleTokens(SyntaxScheme scheme, Color fg, Font font, int... types) {
        Style[] styles = scheme.getStyles();
        for (int t : types) {
            if (t < 0 || t >= styles.length)
                continue;
            Style s = styles[t];
            if (s == null) {
                s = new Style();
                styles[t] = s;
            }
            if (fg != null)
                s.foreground = fg;
            if (font != null)
                s.font = font;
        }
    }

    // ---- utilities ----

    /**
     * Returns the first non-null {@link Color} found in {@link UIManager} for any
     * of the given property keys, or {@code null} if none of the keys are set.
     * Keys are tried in order so callers can list more-specific keys first.
     *
     * @param keys  UIManager property keys to try in order
     * @return the first resolved color, or {@code null}
     */
    private static Color uiColor(String... keys) {
        for (String key : keys) {
            Color c = UIManager.getColor(key);
            if (c != null)
                return c;
        }
        return null;
    }

    /**
     * Returns {@code a} if it is non-null, otherwise {@code b}.
     * Used to express "prefer UIManager value, fall back to scheme value" concisely.
     *
     * @param a  preferred color
     * @param b  fallback color
     * @return {@code a} if non-null, else {@code b} (which may itself be null)
     */
    private static Color firstNonNull(Color a, Color b) {
        return a != null ? a : b;
    }
}
