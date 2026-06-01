package me.justicepro.spigotgui;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

public class Settings implements Serializable {

	/** Pin UID so old settings files still deserialize. */
	private static final long serialVersionUID = -2270671006336242076L;

	// Fields kept as Object/named exactly as before so legacy binary deserialization still works.
	private ServerSettings serverSettings;
	private Theme theme;
	private Object fontSize; // Object for legacy binary compat; always Integer in practice
	private boolean consoleDarkMode = true;
	private Boolean consoleAdaptiveColors; // boxed: null in old files -> default true applied in readObject
	private Boolean consoleColorsEnabled;  // boxed: null in old files -> default true applied in readObject
	private boolean openFilesInSystemDefault = false;
	private String fileEditorTheme = "default";
	private boolean manualConsoleScrollSticky = false;
	private boolean serverButtonsUseText = false;
	private int shutdownCountdownSeconds = 0;
	private boolean consoleWrapWordBreakOnly = false;
	private int accentColorRgb = 0x0096E6;
	private boolean displayKillButton = false;

	private Settings(Builder b) {
		this.serverSettings            = b.serverSettings;
		this.theme                     = b.theme;
		this.fontSize                  = b.fontSize;
		this.consoleDarkMode          = b.consoleDarkMode;
		this.consoleAdaptiveColors    = b.consoleAdaptiveColors;
		this.consoleColorsEnabled     = b.consoleColorsEnabled;
		this.openFilesInSystemDefault  = b.openFilesInSystemDefault;
		this.fileEditorTheme           = b.fileEditorTheme;
		this.manualConsoleScrollSticky = b.manualConsoleScrollSticky;
		this.serverButtonsUseText      = b.serverButtonsUseText;
		this.shutdownCountdownSeconds  = b.shutdownCountdownSeconds;
		this.consoleWrapWordBreakOnly  = b.consoleWrapWordBreakOnly;
		this.accentColorRgb            = b.accentColorRgb;
		this.displayKillButton         = b.displayKillButton;
	}

	/**
	 * Returns a builder pre-populated with every value from this instance.
	 * Use it to create a modified copy without touching unrelated fields:
	 * <pre>Settings updated = settings.toBuilder().theme(newTheme).build();</pre>
	 */
	public Builder toBuilder() {
		return new Builder()
				.serverSettings(serverSettings)
				.theme(theme)
				.fontSize(getFontSize())
				.consoleDarkMode(consoleDarkMode)
				.consoleAdaptiveColors(isConsoleAdaptiveColors())
				.consoleColorsEnabled(isConsoleColorsEnabled())
				.openFilesInSystemDefault(openFilesInSystemDefault)
				.fileEditorTheme(fileEditorTheme)
				.manualConsoleScrollSticky(manualConsoleScrollSticky)
				.serverButtonsUseText(serverButtonsUseText)
				.shutdownCountdownSeconds(shutdownCountdownSeconds)
				.consoleWrapWordBreakOnly(consoleWrapWordBreakOnly)
				.accentColorRgb(accentColorRgb)
				.displayKillButton(displayKillButton);
	}

	/**
	 * Builder for {@link Settings}. All fields default to the same values the app
	 * uses for a fresh install, so only the fields being changed need to be set.
	 */
	public static final class Builder {
		private ServerSettings serverSettings            = ServerSettings.getDefault();
		private Theme          theme                     = Theme.getDefaultForPlatform();
		private int            fontSize                  = 13;
		private boolean        consoleDarkMode           = true;
		private boolean        consoleAdaptiveColors     = true;
		private boolean        consoleColorsEnabled      = true;
		private boolean        openFilesInSystemDefault  = false;
		private String         fileEditorTheme           = "default";
		private boolean        manualConsoleScrollSticky = false;
		private boolean        serverButtonsUseText      = false;
		private int            shutdownCountdownSeconds  = 0;
		private boolean        consoleWrapWordBreakOnly  = false;
		private int            accentColorRgb            = 0x0096E6;
		private boolean        displayKillButton         = false;

		public Builder serverSettings(ServerSettings v)      { serverSettings = v != null ? v : ServerSettings.getDefault(); return this; }
		public Builder theme(Theme v)                        { theme = v != null ? v : Theme.getDefaultForPlatform(); return this; }
		public Builder fontSize(int v)                       { fontSize = v > 0 ? v : 13; return this; }
		public Builder consoleDarkMode(boolean v)            { consoleDarkMode = v; return this; }
		public Builder consoleAdaptiveColors(boolean v)      { consoleAdaptiveColors = v; return this; }
		public Builder consoleColorsEnabled(boolean v)       { consoleColorsEnabled = v; return this; }
		public Builder openFilesInSystemDefault(boolean v)   { openFilesInSystemDefault = v; return this; }
		public Builder fileEditorTheme(String v)             { fileEditorTheme = v != null && !v.isEmpty() ? v : "default"; return this; }
		public Builder manualConsoleScrollSticky(boolean v)  { manualConsoleScrollSticky = v; return this; }
		public Builder serverButtonsUseText(boolean v)       { serverButtonsUseText = v; return this; }
		public Builder shutdownCountdownSeconds(int v)       { shutdownCountdownSeconds = Math.max(0, v); return this; }
		public Builder consoleWrapWordBreakOnly(boolean v)   { consoleWrapWordBreakOnly = v; return this; }
		public Builder accentColorRgb(int v)                 { accentColorRgb = v != 0 ? v : 0x0096E6; return this; }
		public Builder displayKillButton(boolean v)          { displayKillButton = v; return this; }

		public Settings build() { return new Settings(this); }
	}

	// --- Getters and setters ---

	public ServerSettings getServerSettings() { return serverSettings; }

	/** Font size in points. The backing field is {@code Object} for legacy binary compat; always an integer in practice. */
	public int getFontSize() { return fontSize instanceof Number ? ((Number) fontSize).intValue() : 13; }

	public Theme getTheme() { return theme; }
	public void setTheme(Theme theme) { this.theme = theme; }

	public boolean isConsoleDarkMode() { return consoleDarkMode; }
	public void setConsoleDarkMode(boolean consoleDarkMode) { this.consoleDarkMode = consoleDarkMode; }

	public boolean isConsoleAdaptiveColors() { return consoleAdaptiveColors != null ? consoleAdaptiveColors : true; }
	public void setConsoleAdaptiveColors(boolean v) { this.consoleAdaptiveColors = v; }

	public boolean isConsoleColorsEnabled() { return consoleColorsEnabled != null ? consoleColorsEnabled : true; }
	public void setConsoleColorsEnabled(boolean v) { this.consoleColorsEnabled = v; }

	public boolean isOpenFilesInSystemDefault() { return openFilesInSystemDefault; }
	public void setOpenFilesInSystemDefault(boolean openFilesInSystemDefault) { this.openFilesInSystemDefault = openFilesInSystemDefault; }

	public String getFileEditorTheme() { return (fileEditorTheme != null && !fileEditorTheme.isEmpty()) ? fileEditorTheme : "default"; }
	public void setFileEditorTheme(String fileEditorTheme) { this.fileEditorTheme = fileEditorTheme != null ? fileEditorTheme : "default"; }

	public boolean isManualConsoleScrollSticky() { return manualConsoleScrollSticky; }
	public void setManualConsoleScrollSticky(boolean manualConsoleScrollSticky) { this.manualConsoleScrollSticky = manualConsoleScrollSticky; }

	public boolean isServerButtonsUseText() { return serverButtonsUseText; }
	public void setServerButtonsUseText(boolean serverButtonsUseText) { this.serverButtonsUseText = serverButtonsUseText; }

	public int getShutdownCountdownSeconds() { return shutdownCountdownSeconds; }
	public void setShutdownCountdownSeconds(int shutdownCountdownSeconds) { this.shutdownCountdownSeconds = Math.max(0, shutdownCountdownSeconds); }

	public boolean isConsoleWrapWordBreakOnly() { return consoleWrapWordBreakOnly; }
	public void setConsoleWrapWordBreakOnly(boolean consoleWrapWordBreakOnly) { this.consoleWrapWordBreakOnly = consoleWrapWordBreakOnly; }

	public int getAccentColorRgb() { return accentColorRgb != 0 ? accentColorRgb : 0x0096E6; }
	public void setAccentColorRgb(int accentColorRgb) { this.accentColorRgb = accentColorRgb != 0 ? accentColorRgb : 0x0096E6; }

	public boolean isDisplayKillButton() { return displayKillButton; }
	public void setDisplayKillButton(boolean displayKillButton) { this.displayKillButton = displayKillButton; }

	/** Normalize fields that may be absent or invalid in old binary-serialized files. */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		if (shutdownCountdownSeconds < 0) shutdownCountdownSeconds = 0;
		if (accentColorRgb == 0) accentColorRgb = 0x0096E6;
		if (consoleAdaptiveColors == null) consoleAdaptiveColors = true;
		if (consoleColorsEnabled == null) consoleColorsEnabled = true;
	}
}