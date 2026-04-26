package me.justicepro.spigotgui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum Theme {
	// Legacy (kept so old saved configs still deserialize; not shown in UI)
	Graphite("Graphite", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Aluminium("Aluminium", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	HiFi("HiFi", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Noire("Noire", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Texture("Texture", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Acryl("Acryl", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Aero("Aero", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Bernstein("Bernstein", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Fast("Fast", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Luna("Luna", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	McWin("McWin", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Metal("Metal", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Mint("Mint", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Motif("Motif", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),
	Smart("Smart", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, false),

	// System themes (shown only on their OS)
	Windows("Windows", "com.sun.java.swing.plaf.windows.WindowsLookAndFeel", Platform.WINDOWS),
	GTK("GTK", "com.sun.java.swing.plaf.gtk.GTKLookAndFeel", Platform.LINUX),
	Mac("Mac", "com.apple.laf.AquaLookAndFeel", Platform.MAC),

	// FlatLaf core
	FlatLight("Flat Light", "com.formdev.flatlaf.FlatLightLaf", Platform.ALL, "intellij-light"),
	FlatDark("Flat Dark", "com.formdev.flatlaf.FlatDarkLaf", Platform.ALL, "darcula"),
	FlatIntelliJ("Flat IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf", Platform.ALL, "intellij-light"),
	FlatDarcula("Flat Darcula", "com.formdev.flatlaf.FlatDarculaLaf", Platform.ALL, "darcula"),
	FlatMacLight("Flat macOS Light", "com.formdev.flatlaf.themes.FlatMacLightLaf", Platform.ALL, "intellij-light"),
	FlatMacDark("Flat macOS Dark", "com.formdev.flatlaf.themes.FlatMacDarkLaf", Platform.ALL, "darcula"),

	// FlatLaf IntelliJ Themes Pack
	Arc("Arc", "com.formdev.flatlaf.intellijthemes.FlatArcIJTheme", Platform.ALL),
	ArcOrange("Arc - Orange", "com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme", Platform.ALL),
	ArcDark("Arc Dark", "com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme", Platform.ALL),
	ArcDarkOrange("Arc Dark - Orange", "com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme", Platform.ALL),
	Carbon("Carbon", "com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme", Platform.ALL),
	Cobalt2("Cobalt 2", "com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme", Platform.ALL, "cobalt2"),
	CyanLight("Cyan light", "com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme", Platform.ALL),
	DarkFlat("Dark Flat", "com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme", Platform.ALL),
	DarkPurple("Dark purple", "com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme", Platform.ALL),
	Dracula("Dracula", "com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme", Platform.ALL, "dracula"),
	GradiantoDarkFuchsia("Gradianto Dark Fuchsia", "com.formdev.flatlaf.intellijthemes.FlatGradiantoDarkFuchsiaIJTheme", Platform.ALL),
	GradiantoDeepOcean("Gradianto Deep Ocean", "com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme", Platform.ALL),
	GradiantoMidnightBlue("Gradianto Midnight Blue", "com.formdev.flatlaf.intellijthemes.FlatGradiantoMidnightBlueIJTheme", Platform.ALL),
	GradiantoNatureGreen("Gradianto Nature Green", "com.formdev.flatlaf.intellijthemes.FlatGradiantoNatureGreenIJTheme", Platform.ALL),
	Gray("Gray", "com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme", Platform.ALL),
	GruvboxDarkHard("Gruvbox Dark Hard", "com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme", Platform.ALL, "gruvbox-dark-hard"),
	HiberbeeDark("Hiberbee Dark", "com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme", Platform.ALL),
	HighContrast("High Contrast", "com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme", Platform.ALL, "high-contrast"),
	LightFlat("Light Flat", "com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme", Platform.ALL),
	MaterialDesignDark("Material Design Dark", "com.formdev.flatlaf.intellijthemes.FlatMaterialDesignDarkIJTheme", Platform.ALL),
	Monocai("Monocai", "com.formdev.flatlaf.intellijthemes.FlatMonocaiIJTheme", Platform.ALL),
	MonokaiPro("Monokai Pro", "com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme", Platform.ALL),
	Nord("Nord", "com.formdev.flatlaf.intellijthemes.FlatNordIJTheme", Platform.ALL, "nord"),
	OneDark("One Dark", "com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme", Platform.ALL, "one-dark"),
	SolarizedDark("Solarized Dark", "com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme", Platform.ALL, "solarized-dark"),
	SolarizedLight("Solarized Light", "com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme", Platform.ALL, "solarized-light"),
	Spacegray("Spacegray", "com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme", Platform.ALL),
	Vuesion("Vuesion", "com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme", Platform.ALL),
	XcodeDark("Xcode-Dark", "com.formdev.flatlaf.intellijthemes.FlatXcodeDarkIJTheme", Platform.ALL),

	// Material Theme UI Lite
	MTArcDark("Arc Dark (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTArcDarkIJTheme", Platform.ALL),
	MTAtomOneDark("Atom One Dark (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneDarkIJTheme", Platform.ALL),
	MTAtomOneLight("Atom One Light (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneLightIJTheme", Platform.ALL),
	MTDracula("Dracula (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTDraculaIJTheme", Platform.ALL),
	MTGitHub("GitHub (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubIJTheme", Platform.ALL),
	MTGitHubDark("GitHub Dark (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubDarkIJTheme", Platform.ALL),
	MTLightOwl("Light Owl (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTLightOwlIJTheme", Platform.ALL),
	MTMaterialDarker("Material Darker (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme", Platform.ALL),
	MTMaterialDeepOcean("Material Deep Ocean (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDeepOceanIJTheme", Platform.ALL),
	MTMaterialLighter("Material Lighter (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialLighterIJTheme", Platform.ALL),
	MTMaterialOceanic("Material Oceanic (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialOceanicIJTheme", Platform.ALL),
	MTMaterialPalenight("Material Palenight (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialPalenightIJTheme", Platform.ALL),
	MTMonokaiPro("Monokai Pro (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMonokaiProIJTheme", Platform.ALL),
	MTMoonlight("Moonlight (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMoonlightIJTheme", Platform.ALL),
	MTNightOwl("Night Owl (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTNightOwlIJTheme", Platform.ALL),
	MTSolarizedDark("Solarized Dark (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedDarkIJTheme", Platform.ALL),
	MTSolarizedLight("Solarized Light (Material)", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedLightIJTheme", Platform.ALL),
	;

	public enum Platform { ALL, WINDOWS, LINUX, MAC }

	private static final Set<Theme> LEGACY = Collections.unmodifiableSet(EnumSet.range(Graphite, Smart));

	private final String name;
	private final String lookAndFeel;
	private final Platform platform;
	private final boolean shownInUI;
	/**
	 * Resource name of the bundled editor color scheme (under editor-schemes/*.xml), or
	 * {@code null} if no specific scheme is available (falls back to a generic dark/light palette).
	 */
	private final String editorScheme;

	/** Used for shown entries with no specific editor scheme. */
	private Theme(String name, String lookAndFeel, Platform platform) {
		this(name, lookAndFeel, platform, true, null);
	}

	/** Used for legacy (hidden) entries — shownInUI=false, editorScheme=null. */
	private Theme(String name, String lookAndFeel, Platform platform, boolean shownInUI) {
		this(name, lookAndFeel, platform, shownInUI, null);
	}

	/** Used for shown entries that have a bundled editor color scheme. */
	private Theme(String name, String lookAndFeel, Platform platform, String editorScheme) {
		this(name, lookAndFeel, platform, true, editorScheme);
	}

	private Theme(String name, String lookAndFeel, Platform platform, boolean shownInUI, String editorScheme) {
		this.name = name;
		this.lookAndFeel = lookAndFeel;
		this.platform = platform;
		this.shownInUI = shownInUI;
		this.editorScheme = editorScheme;
	}

	public String getName() {
		return name;
	}

	public String getLookAndFeel() {
		return lookAndFeel;
	}

	/**
	 * Returns the resource name of the bundled editor color scheme for this theme
	 * (e.g. {@code "dracula"} → {@code /editor-schemes/dracula.xml}), or {@code null}
	 * if no specific scheme is available.  {@code null} causes {@link
	 * me.justicepro.spigotgui.FileExplorer.EditorSchemeApplier} to auto-select a
	 * generic dark or light palette based on {@code FlatLaf.isLafDark()}.
	 */
	public String getEditorScheme() {
		return editorScheme;
	}

	public boolean isShownInUI() {
		return shownInUI;
	}

	public boolean isAvailableOnCurrentPlatform() {
		if (platform == Platform.ALL) return true;
		String os = System.getProperty("os.name", "").toLowerCase();
		switch (platform) {
			case WINDOWS: return os.contains("win");
			case LINUX:   return os.contains("linux") || os.contains("nux");
			case MAC:     return os.contains("mac");
			default:      return true;
		}
	}

	public static Theme[] getAvailableThemes() {
		List<Theme> list = new ArrayList<>();
		for (Theme t : values()) {
			if (t.shownInUI && t.isAvailableOnCurrentPlatform()) list.add(t);
		}
		return list.toArray(new Theme[0]);
	}

	public static Theme fromDisplayName(String displayName) {
		if (displayName == null || displayName.isEmpty()) return null;
		for (Theme t : values()) {
			if (t.getName().equals(displayName)) return t;
		}
		return null;
	}

	public static Theme getFallbackTheme() {
		return FlatLight;
	}

	/** Resolve: if theme is null, not available on this OS, or legacy (not shown), return Flat Light. */
	public static Theme resolveForCurrentPlatform(Theme theme) {
		if (theme == null || !theme.isAvailableOnCurrentPlatform() || !theme.shownInUI)
			return getFallbackTheme();
		return theme;
	}

	/** Default theme on all systems: Flat Light. */
	public static Theme getDefaultForPlatform() {
		return FlatLight;
	}

	/** 
	 * True for the 6 core FlatLaf themes and several others that honor @accentColor.
	 * False for most IntelliJ pack and system themes (they use their own colors).
	 * 
	 * Some themes honor the accent color in some areas but not others - they are not included here.
	 */
	public boolean honorsAccentColor() {
		return this == FlatLight || this == FlatDark || 
				this == FlatIntelliJ || this == FlatDarcula || 
				this == FlatMacLight || this == FlatMacDark ||
				this == Cobalt2 || this == DarkFlat ||
				this == GruvboxDarkHard;
	}

	public ThemeFamily getFamily() {
		if (this == Windows) return ThemeFamily.WINDOWS;
		if (this == Motif) return ThemeFamily.MOTIF;
		if (this == GTK) return ThemeFamily.GTK;
		if (this == Mac) return ThemeFamily.MAC;
		if (LEGACY.contains(this)) return ThemeFamily.SWING;
		return ThemeFamily.FLATLAF;
	}

	public enum ThemeFamily { SWING, FLATLAF, WINDOWS, MOTIF, GTK, MAC }
}
