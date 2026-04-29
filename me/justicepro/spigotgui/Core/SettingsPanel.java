package me.justicepro.spigotgui.Core;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.io.IOException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import me.justicepro.spigotgui.ServerSettings;
import me.justicepro.spigotgui.Settings;
import me.justicepro.spigotgui.Theme;
import me.justicepro.spigotgui.FileExplorer.FileEditor;
import me.justicepro.spigotgui.Utils.ConsoleStyleHelper;

/**
 * Settings tab: Server (JAR path, countdown, server.properties), JVM (args, switches, min/max RAM),
 * Files (open in system default), Appearance (theme, accent, file editor theme, font size, console options).
 */
public class SettingsPanel extends JPanel {

	private final Settings settings;

	private JSpinner minRam;
	private JSpinner maxRam;
	private JTextField customJvmArgsField;
	private JTextField customJvmSwitchesField;
	private JSpinner fontSpinner;
	private JLabel lblTheme;
	private JComboBox<String> themeBox;
	private JCheckBox consoleDarkModeCheckBox;
	private JCheckBox consoleAdaptiveColorsCheckBox;
	private JCheckBox disableConsoleColorsCheckBox;
	private JCheckBox consoleWrapWordBreakOnlyCheckBox;
	private JCheckBox openFilesInSystemDefaultCheckBox;
	private JCheckBox manualConsoleScrollStickyCheckBox;
	private JCheckBox serverButtonsUseTextCheckBox;
	private JCheckBox displayKillButtonCheckBox;
	private JSpinner shutdownCountdownSpinner;
	private AccentColorPanel accentColorPanel;
	private JLabel accentThemeControlledLabel;
	private JPanel lblAccentWrap;
	private JPanel accentRow;
	private JTextField serverFileField;
	private JTextField customJvmPathField;

	public SettingsPanel(SpigotGUI gui, Settings settings, ServerSettings serverSettings) {
		super(new BorderLayout());
		this.settings = settings;

		minRam = new JSpinner();
		minRam.setModel(new SpinnerNumberModel(Integer.valueOf(1024), null, null, Integer.valueOf(1)));
		maxRam = new JSpinner();
		maxRam.setModel(new SpinnerNumberModel(Integer.valueOf(1024), null, null, Integer.valueOf(1)));

		JLabel lblMinRam = new JLabel("Min RAM");
		lblMinRam.setHorizontalAlignment(SwingConstants.LEFT);
		JLabel lblMaxRam = new JLabel("Max RAM");
		lblMaxRam.setHorizontalAlignment(SwingConstants.LEFT);

		customJvmArgsField = new JTextField();
		customJvmArgsField.setColumns(10);
		customJvmSwitchesField = new JTextField();
		customJvmSwitchesField.setColumns(10);

		fontSpinner = new JSpinner();
		fontSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent arg0) {
				gui.onConsoleFontSizeChanged((int) fontSpinner.getValue());
			}
		});
		fontSpinner.setModel(new SpinnerNumberModel(Integer.valueOf(13), null, null, Integer.valueOf(1)));

		minRam.setValue(serverSettings.getMinRam());
		maxRam.setValue(serverSettings.getMaxRam());
		fontSpinner.setValue(settings.getFontSize());
		customJvmArgsField.setText(serverSettings.getCustomArgs());
		customJvmSwitchesField.setText(serverSettings.getCustomSwitches());

		customJvmPathField = new JTextField();
		customJvmPathField.setColumns(1);
		customJvmPathField.setEditable(false);
		String savedJvmPath = serverSettings.getCustomJvmPath();
		customJvmPathField.setText(savedJvmPath != null && !savedJvmPath.isEmpty() ? savedJvmPath : "<default>");

		JLabel lblCustomArgs = new JLabel("Custom Arguments");
		JLabel lblCustomSwitches = new JLabel("Custom Switches");

		serverFileField = new JTextField();
		serverFileField.setColumns(1);
		serverFileField.setEditable(false);
		serverFileField.setToolTipText("Path to the server JAR file. Use \"Set Server File\" to change.");
		File jarFile = gui.getJarFile();
		serverFileField.setText(jarFile != null ? jarFile.getAbsolutePath() : new File("server.jar").getAbsolutePath());

		JButton btnSetJarFile = new JButton("Set Server File");
		btnSetJarFile.setToolTipText("Choose the server JAR file to run (e.g. paper.jar, spigot.jar).");
		btnSetJarFile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				File jarDir = gui.getDefaultDirectory();
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setCurrentDirectory(jarDir);
				fileChooser.setFileFilter(new FileNameExtensionFilter("Jar file (*.jar)", "jar"));
				if (jarFile != null && jarFile.getParentFile() != null) fileChooser.setSelectedFile(jarFile);
				if (fileChooser.showOpenDialog(SettingsPanel.this) == JFileChooser.APPROVE_OPTION) {
					File selected = fileChooser.getSelectedFile();
					gui.setJarFile(selected);
					serverFileField.setText(selected.getAbsolutePath());
				}
			}
		});

		JLabel lblFontSize = new JLabel("Console font size");

		lblTheme = new JLabel("Theme (may require restart)");
		themeBox = new JComboBox<>();
		String[] availableThemeNames = new String[Theme.getAvailableThemes().length];
		int i = 0;
		for (Theme t : Theme.getAvailableThemes()) availableThemeNames[i++] = t.getName();
		themeBox.setModel(new DefaultComboBoxModel<>(availableThemeNames));
		themeBox.setSelectedItem(settings.getTheme().getName());
		gui.updateThemeLabelFor(settings.getTheme(), lblTheme);
		themeBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String themeName = themeBox.getSelectedItem() + "";
				if (themeName.isEmpty()) return;
				Theme theme = Theme.fromDisplayName(themeName);
				if (theme == null) return;
				boolean sameFamily = (gui.getInitialThemeForSession() != null && gui.getInitialThemeForSession().getFamily() == theme.getFamily());
				if (sameFamily) {
					try {
						UIManager.setLookAndFeel(theme.getLookAndFeel());
						SpigotGUI.updateAllWindowsUI();
						FileEditor.applyCurrentScheme(theme);
					} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
							| UnsupportedLookAndFeelException e) {
						e.printStackTrace();
					}
				}
				gui.updateThemeLabelFor(theme, lblTheme);
				updateAccentPanelForTheme(theme);
			}
		});

		consoleDarkModeCheckBox = new JCheckBox("Console dark mode");
		consoleDarkModeCheckBox.setToolTipText("Use a dark background in the console tab. When unchecked, the console uses a light background.");
		consoleDarkModeCheckBox.setSelected(settings.isConsoleDarkMode());
		consoleDarkModeCheckBox.addActionListener(e -> gui.onConsoleDarkModeChanged(consoleDarkModeCheckBox.isSelected()));

		consoleAdaptiveColorsCheckBox = new JCheckBox("Adapt console colors for background");
		consoleAdaptiveColorsCheckBox.setToolTipText("<html>When enabled, console colors are automatically adjusted to remain readable on the current background.<br>"
				+ "Bright colors are lightened on dark backgrounds; dark colors are darkened on light backgrounds.<br>"
				+ "When disabled, colors are displayed exactly as received from the server output.<br>"
				+ "<i>Note: if console colors are disabled, this setting has no effect.</i></html>");
		consoleAdaptiveColorsCheckBox.setSelected(settings.isConsoleAdaptiveColors());
		consoleAdaptiveColorsCheckBox.addActionListener(e -> gui.onConsoleAdaptiveColorsChanged(consoleAdaptiveColorsCheckBox.isSelected()));

		disableConsoleColorsCheckBox = new JCheckBox("Disable console colors");
		disableConsoleColorsCheckBox.setToolTipText("When checked, console text is shown in the default color only (no ANSI or § colors).");
		disableConsoleColorsCheckBox.setSelected(!settings.isConsoleColorsEnabled());
		disableConsoleColorsCheckBox.addActionListener(e -> gui.onConsoleColorsEnabledChanged(!disableConsoleColorsCheckBox.isSelected()));

		consoleWrapWordBreakOnlyCheckBox = new JCheckBox("Console text wrap on word-break only");
		consoleWrapWordBreakOnlyCheckBox.setToolTipText("When unchecked (default), long lines wrap at any character so everything stays visible without a horizontal scrollbar. When checked, lines wrap only at spaces, so very long words or tokens may extend off-screen.");
		consoleWrapWordBreakOnlyCheckBox.setSelected(settings.isConsoleWrapWordBreakOnly());
		consoleWrapWordBreakOnlyCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ConsoleStyleHelper.setConsoleWrapWordBreakOnly(consoleWrapWordBreakOnlyCheckBox.isSelected());
				gui.onConsoleWrapWordBreakOnlyChanged(consoleWrapWordBreakOnlyCheckBox.isSelected());
			}
		});

		openFilesInSystemDefaultCheckBox = new JCheckBox("Open files in system default application");
		openFilesInSystemDefaultCheckBox.setToolTipText("When checked, double-clicking a file in the Files tab opens it in your system's default application instead of the built-in editor.");
		openFilesInSystemDefaultCheckBox.setSelected(settings.isOpenFilesInSystemDefault());
		openFilesInSystemDefaultCheckBox.addActionListener(e -> gui.onOpenFilesInSystemDefaultChanged(openFilesInSystemDefaultCheckBox.isSelected()));

		JButton btnEditServerproperties = new JButton("Edit Server.Properties");
		btnEditServerproperties.setToolTipText("Edit the server.properties file.");
		btnEditServerproperties.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				File serverProps = new File("server.properties");
				if (openFilesInSystemDefaultCheckBox.isSelected()) {
					try {
						java.awt.Desktop.getDesktop().open(serverProps);
					} catch (IOException ex) {
						javax.swing.JOptionPane.showMessageDialog(gui, "Could not open: " + ex.getMessage());
					}
				} else {
					FileEditor fileEditor = new FileEditor();
					fileEditor.setLocationRelativeTo(gui);
					try {
						fileEditor.openFile(serverProps);
					} catch (IOException ex) {
						ex.printStackTrace();
					}
					fileEditor.setVisible(true);
				}
			}
		});

		int pad = 6;
		JPanel settingsContentInner = new JPanel();
		settingsContentInner.setLayout(new BoxLayout(settingsContentInner, BoxLayout.Y_AXIS));
		settingsContentInner.setBorder(new EmptyBorder(10, 10, 10, 10));

		// --- Section: Server ---
		JPanel serverSection = new JPanel();
		serverSection.setBorder(new TitledBorder(null, "Server", TitledBorder.LEADING, TitledBorder.TOP));
		serverSection.setLayout(new GridBagLayout());
		GridBagConstraints rc = new GridBagConstraints();
		rc.fill = GridBagConstraints.HORIZONTAL;
		rc.weightx = 1;
		rc.gridwidth = 1;
		rc.anchor = GridBagConstraints.WEST;
		rc.insets = new Insets(pad, pad, pad, pad);
		int serverBtnW = 120;
		btnSetJarFile.setPreferredSize(new Dimension(serverBtnW, btnSetJarFile.getPreferredSize().height));
		JPanel row1 = new JPanel(new BorderLayout(8, 0));
		row1.add(serverFileField, BorderLayout.CENTER);
		row1.add(btnSetJarFile, BorderLayout.EAST);
		rc.gridx = 0; rc.gridy = 0; serverSection.add(row1, rc);
		JLabel lblShutdownCountdown = new JLabel("Shutdown/restart countdown (seconds)");
		lblShutdownCountdown.setToolTipText("When you click Stop or Restart, wait this many seconds before actually stopping (announces in chat). 0 = immediate.");
		shutdownCountdownSpinner = new JSpinner(new SpinnerNumberModel(Math.max(0, settings.getShutdownCountdownSeconds()), 0, 86400, 1));
		shutdownCountdownSpinner.setToolTipText(lblShutdownCountdown.getToolTipText());
		shutdownCountdownSpinner.setPreferredSize(new Dimension(90, shutdownCountdownSpinner.getPreferredSize().height));
		JPanel countdownEditPanel = new JPanel(new GridBagLayout());
		GridBagConstraints ce = new GridBagConstraints();
		ce.insets = new Insets(0, 0, 0, 0);
		ce.anchor = GridBagConstraints.WEST;
		ce.fill = GridBagConstraints.NONE;
		ce.gridx = 0; ce.gridy = 0; ce.weightx = 0; countdownEditPanel.add(lblShutdownCountdown, ce);
		ce.insets = new Insets(0, 8, 0, 0);
		ce.gridx = 1; ce.weightx = 0; countdownEditPanel.add(shutdownCountdownSpinner, ce);
		ce.insets = new Insets(0, 0, 0, 0);
		ce.gridx = 2; ce.weightx = 1; ce.fill = GridBagConstraints.HORIZONTAL; countdownEditPanel.add(new JPanel(), ce);
		JPanel spacerRow = new JPanel();
		int rowGap = pad * 2;
		spacerRow.setPreferredSize(new Dimension(0, rowGap));
		spacerRow.setMinimumSize(new Dimension(0, rowGap));
		ce.gridy = 1; ce.gridx = 0; ce.gridwidth = 3; ce.weightx = 1; ce.fill = GridBagConstraints.HORIZONTAL; countdownEditPanel.add(spacerRow, ce);
		ce.gridwidth = 1;
		ce.gridy = 2; ce.gridx = 0; ce.weightx = 0; ce.fill = GridBagConstraints.HORIZONTAL; countdownEditPanel.add(btnEditServerproperties, ce);
		ce.gridx = 1; ce.weightx = 0; ce.fill = GridBagConstraints.NONE; countdownEditPanel.add(new JPanel(), ce);
		ce.gridx = 2; ce.weightx = 1; ce.fill = GridBagConstraints.HORIZONTAL; countdownEditPanel.add(new JPanel(), ce);
		rc.gridy = 1; rc.gridheight = 2; serverSection.add(countdownEditPanel, rc);
		rc.gridheight = 1;
		settingsContentInner.add(serverSection);

		// --- Section: JVM ---
		JPanel jvmSection = new JPanel();
		jvmSection.setBorder(new TitledBorder(null, "JVM / Run options", TitledBorder.LEADING, TitledBorder.TOP));
		jvmSection.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(pad, pad, pad, pad);
		c.anchor = GridBagConstraints.WEST;
		lblCustomArgs.setToolTipText("<html>Passed to the JVM as program arguments (e.g. <code>-Dproperty=value</code> for system properties).<br>These appear after the class/jar and before any application arguments. Use for JVM tuning and -D flags. Switches (above) are for launcher options.</html>");
		customJvmArgsField.setToolTipText(lblCustomArgs.getToolTipText());
		lblCustomSwitches.setToolTipText("<html>Passed to the java launcher as command-line switches (e.g. <code>-Xmx2G</code>, <code>-XX:+UseG1GC</code>).<br>These appear before the class/jar. Use for memory, GC, and other JVM options. Arguments (below) are for -D and app-level; switches are for launcher options.</html>");
		customJvmSwitchesField.setToolTipText(lblCustomSwitches.getToolTipText());
		JScrollPane argsScroll = new JScrollPane(customJvmArgsField);
		argsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		argsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		argsScroll.setBorder(null);
		customJvmArgsField.addMouseWheelListener(e -> {
			Component p = customJvmArgsField.getParent();
			if (p instanceof JViewport) {
				JScrollPane sp = (JScrollPane) ((JViewport) p).getParent();
				javax.swing.JScrollBar bar = sp.getHorizontalScrollBar();
				if (bar != null && bar.isEnabled()) {
					int step = e.getUnitsToScroll() * 24;
					bar.setValue(Math.max(bar.getMinimum(), Math.min(bar.getMaximum() - bar.getVisibleAmount(), bar.getValue() + step)));
				}
			}
		});
		JScrollPane switchesScroll = new JScrollPane(customJvmSwitchesField);
		switchesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		switchesScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		switchesScroll.setBorder(null);
		customJvmSwitchesField.addMouseWheelListener(e -> {
			Component p = customJvmSwitchesField.getParent();
			if (p instanceof JViewport) {
				JScrollPane sp = (JScrollPane) ((JViewport) p).getParent();
				javax.swing.JScrollBar bar = sp.getHorizontalScrollBar();
				if (bar != null && bar.isEnabled()) {
					int step = e.getUnitsToScroll() * 24;
					bar.setValue(Math.max(bar.getMinimum(), Math.min(bar.getMaximum() - bar.getVisibleAmount(), bar.getValue() + step)));
				}
			}
		});
		// --- Custom JVM row (row 0) ---
		JLabel lblCustomJvm = new JLabel("Custom JVM");
		String jvmTip = "<html>Path to the java or javaw executable used to launch the server.<br>"
				+ "Usually not needed unless you are running a version of Minecraft that<br>"
				+ "requires a specific Java version. When not set, the same JVM that<br>"
				+ "launched this application will be used.</html>";
		lblCustomJvm.setToolTipText(jvmTip);
		customJvmPathField.setToolTipText(jvmTip);
		JButton btnBrowseJvm = new JButton("Browse...");
		btnBrowseJvm.setToolTipText(jvmTip);
		JButton btnResetJvm = new JButton("Reset");
		btnResetJvm.setToolTipText("Clear the custom JVM path and use the default (same JVM as this app).");
		btnBrowseJvm.setMargin(new Insets(2, 12, 2, 12));
		btnBrowseJvm.setMinimumSize(new Dimension(40, btnBrowseJvm.getPreferredSize().height));
		btnResetJvm.setMinimumSize(new Dimension(36, btnResetJvm.getPreferredSize().height));
		btnBrowseJvm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setDialogTitle("Select Java Executable");
				fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
				String current = customJvmPathField.getText();
				if (!current.isEmpty() && !current.equals("<default>")) {
					File cur = new File(current);
					if (cur.getParentFile() != null) fileChooser.setCurrentDirectory(cur.getParentFile());
				}
				if (fileChooser.showOpenDialog(SettingsPanel.this) == JFileChooser.APPROVE_OPTION) {
					customJvmPathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
				}
			}
		});
		btnResetJvm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				customJvmPathField.setText("<default>");
			}
		});
		JPanel jvmBtnPanel = new JPanel();
		jvmBtnPanel.setLayout(new BoxLayout(jvmBtnPanel, BoxLayout.LINE_AXIS));
		jvmBtnPanel.add(btnBrowseJvm);
		jvmBtnPanel.add(Box.createHorizontalStrut(4));
		jvmBtnPanel.add(btnResetJvm);
		JPanel jvmFieldRow = new JPanel(new BorderLayout(6, 0));
		jvmFieldRow.add(customJvmPathField, BorderLayout.CENTER);
		jvmFieldRow.add(jvmBtnPanel, BorderLayout.EAST);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0; c.gridy = 0; c.gridwidth = 1; c.weightx = 0; jvmSection.add(lblCustomJvm, c);
		c.gridx = 1; c.gridwidth = 2; c.weightx = 1; jvmSection.add(jvmFieldRow, c);
		c.gridwidth = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridy = 1; c.gridx = 0; c.weightx = 0; jvmSection.add(lblCustomSwitches, c);
		c.gridx = 1; c.gridwidth = 2; c.weightx = 1; jvmSection.add(switchesScroll, c);
		c.gridwidth = 1;
		c.gridy = 2; c.gridx = 0; c.weightx = 0; jvmSection.add(lblCustomArgs, c);
		c.gridx = 1; c.gridwidth = 2; c.weightx = 1; jvmSection.add(argsScroll, c);
		c.gridwidth = 1;
		int spinnerW = 90;
		minRam.setPreferredSize(new Dimension(spinnerW, minRam.getPreferredSize().height));
		maxRam.setPreferredSize(new Dimension(spinnerW, maxRam.getPreferredSize().height));
		lblMinRam.setToolTipText("Minimum heap size in MB allocated to the server JVM.");
		minRam.setToolTipText(lblMinRam.getToolTipText());
		lblMaxRam.setToolTipText("Maximum heap size in MB allocated to the server JVM.");
		maxRam.setToolTipText(lblMaxRam.getToolTipText());
		c.fill = GridBagConstraints.NONE;
		c.gridy = 3; c.gridx = 0; c.weightx = 0; jvmSection.add(lblMinRam, c);
		c.gridx = 1; c.weightx = 0; jvmSection.add(minRam, c);
		c.gridx = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; jvmSection.add(new JPanel(), c);
		c.fill = GridBagConstraints.NONE;
		c.gridy = 4; c.gridx = 0; c.weightx = 0; jvmSection.add(lblMaxRam, c);
		c.gridx = 1; c.weightx = 0; jvmSection.add(maxRam, c);
		c.gridx = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; jvmSection.add(new JPanel(), c);
		customJvmArgsField.setMinimumSize(new Dimension(60, 20));
		customJvmSwitchesField.setMinimumSize(new Dimension(60, 20));
		customJvmPathField.setMinimumSize(new Dimension(60, 20));
		settingsContentInner.add(jvmSection);

		// --- Section: Files ---
		JPanel filesSection = new JPanel();
		filesSection.setBorder(new TitledBorder(null, "Files", TitledBorder.LEADING, TitledBorder.TOP));
		filesSection.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		c.insets = new Insets(pad, pad, pad, pad);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.gridx = 0; c.gridy = 0; c.weightx = 0; filesSection.add(openFilesInSystemDefaultCheckBox, c);
		c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; filesSection.add(new JPanel(), c);
		settingsContentInner.add(filesSection);

		// --- Section: Appearance ---
		JPanel appearanceSection = new JPanel();
		appearanceSection.setBorder(new TitledBorder(null, "Appearance", TitledBorder.LEADING, TitledBorder.TOP));
		appearanceSection.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		c.insets = new Insets(pad, pad, pad, pad);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		lblTheme.setToolTipText("Look and feel for the application. May require an application restart to take effect.");
		themeBox.setToolTipText(lblTheme.getToolTipText());
		c.gridx = 0; c.gridy = 0; c.weightx = 0; c.gridwidth = 1; appearanceSection.add(lblTheme, c);
		c.gridx = 1; c.weightx = 1; appearanceSection.add(themeBox, c);
		JLabel lblAccentColor = new JLabel("Theme accent color");
		lblAccentColor.setVerticalAlignment(SwingConstants.CENTER);
		lblAccentColor.setToolTipText("<html>Accent color used for: highlighted buttons and controls in themes, the<br>Restart button icon, and focus indicators. Some themes use their own colors.</html>");
		lblAccentWrap = new JPanel(new BorderLayout(0, 0)) {
			@Override public int getBaseline(int w, int h) { return -1; }
			@Override public Component.BaselineResizeBehavior getBaselineResizeBehavior() { return Component.BaselineResizeBehavior.OTHER; }
		};
		lblAccentWrap.add(lblAccentColor, BorderLayout.CENTER);
		accentColorPanel = new AccentColorPanel(settings.getAccentColorRgb());
		accentColorPanel.setToolTipText(lblAccentColor.getToolTipText());
		accentColorPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
		accentThemeControlledLabel = new JLabel("(partially theme-controlled)");
		accentThemeControlledLabel.setVerticalAlignment(SwingConstants.CENTER);
		accentThemeControlledLabel.setForeground(java.awt.Color.GRAY);
		accentThemeControlledLabel.setToolTipText("The selected theme uses custom colors; accent is not fully applied to the theme (some icons and controls may still use the accent color).");
		accentColorPanel.setAccentChangeListener(() -> gui.applyAccentColorLive());
		accentRow = new JPanel() {
			@Override public int getBaseline(int w, int h) { return -1; }
			@Override public Component.BaselineResizeBehavior getBaselineResizeBehavior() { return Component.BaselineResizeBehavior.OTHER; }
		};
		accentRow.setLayout(new BoxLayout(accentRow, BoxLayout.LINE_AXIS));
		int swatchH = accentColorPanel.getPreferredSize().height;
		lblAccentWrap.setPreferredSize(new Dimension(lblAccentColor.getPreferredSize().width, swatchH));
		lblAccentWrap.setMinimumSize(new Dimension(0, swatchH));
		lblAccentWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, swatchH));
		accentRow.setMinimumSize(new Dimension(0, swatchH));
		accentRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, swatchH));
		accentColorPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
		accentRow.add(accentColorPanel);
		accentRow.add(Box.createHorizontalStrut(8));
		JPanel themeControlledWrap = new JPanel(new BorderLayout(0, 0)) {
			@Override public int getBaseline(int w, int h) { return -1; }
			@Override public Component.BaselineResizeBehavior getBaselineResizeBehavior() { return Component.BaselineResizeBehavior.OTHER; }
		};
		themeControlledWrap.setPreferredSize(new Dimension(120, swatchH));
		themeControlledWrap.setMinimumSize(new Dimension(0, swatchH));
		themeControlledWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, swatchH));
		themeControlledWrap.setAlignmentY(Component.CENTER_ALIGNMENT);
		themeControlledWrap.add(accentThemeControlledLabel, BorderLayout.CENTER);
		accentRow.add(themeControlledWrap);
		c.gridy = 1; c.gridx = 0; c.weightx = 0; c.anchor = GridBagConstraints.CENTER;
		appearanceSection.add(lblAccentWrap, c);
		c.anchor = GridBagConstraints.WEST;
		GridBagConstraints cAccent = (GridBagConstraints) c.clone();
		cAccent.gridx = 1; cAccent.weightx = 1; cAccent.fill = GridBagConstraints.HORIZONTAL;
		cAccent.anchor = GridBagConstraints.CENTER;
		cAccent.insets = new Insets(pad, pad, pad, pad);
		appearanceSection.add(accentRow, cAccent);
		updateAccentPanelForTheme(settings.getTheme());
		lblFontSize.setToolTipText("Font size (in points) for the console text.");
		fontSpinner.setPreferredSize(new Dimension(90, fontSpinner.getPreferredSize().height));
		fontSpinner.setToolTipText(lblFontSize.getToolTipText());
		c.gridy = 2; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; appearanceSection.add(lblFontSize, c);
		c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; appearanceSection.add(fontSpinner, c);
		c.gridx = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; appearanceSection.add(new JPanel(), c);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridy = 3; c.gridx = 0; c.gridwidth = 2; appearanceSection.add(consoleDarkModeCheckBox, c);
		c.gridwidth = 1;
		c.gridy = 4; c.gridx = 0; c.gridwidth = 2; appearanceSection.add(consoleAdaptiveColorsCheckBox, c);
		c.gridy = 5; c.gridx = 0; c.gridwidth = 2; appearanceSection.add(disableConsoleColorsCheckBox, c);
		c.gridy = 6; c.gridx = 0; c.gridwidth = 2; appearanceSection.add(consoleWrapWordBreakOnlyCheckBox, c);
		manualConsoleScrollStickyCheckBox = new JCheckBox("Manual console scroll sticky");
		manualConsoleScrollStickyCheckBox.setToolTipText("<html>When checked, a \"Console scroll sticky\" checkbox appears on the Console tab.<br>You control whether the console auto-scrolls to the bottom by toggling that checkbox.<br>When unchecked, sticky is automatic: scroll to bottom to stick, scroll up to unstick.</html>");
		manualConsoleScrollStickyCheckBox.setSelected(settings.isManualConsoleScrollSticky());
		manualConsoleScrollStickyCheckBox.addActionListener(e -> gui.onManualConsoleScrollStickyChanged(manualConsoleScrollStickyCheckBox.isSelected()));
		c.gridy = 7; c.gridx = 0; c.gridwidth = 2; appearanceSection.add(manualConsoleScrollStickyCheckBox, c);
		displayKillButtonCheckBox = new JCheckBox("Display Kill Button");
		displayKillButtonCheckBox.setToolTipText("Display the Kill Button, which forcefully terminates the server. This should only ever be used if the server is hung as it can cause damage to your Minecraft server. Enable and use with caution!");
		displayKillButtonCheckBox.setSelected(settings.isDisplayKillButton());
		displayKillButtonCheckBox.addActionListener(e -> gui.onDisplayKillButtonChanged(displayKillButtonCheckBox.isSelected()));
		c.gridy = 8; c.gridx = 0; c.gridwidth = 2; appearanceSection.add(displayKillButtonCheckBox, c);
		serverButtonsUseTextCheckBox = new JCheckBox("Use text for server control buttons");
		serverButtonsUseTextCheckBox.setToolTipText("When checked, Start/Stop/Kill/Restart show text. When unchecked, they show only icons (play, stop, warning, refresh) with tooltips.");
		serverButtonsUseTextCheckBox.setSelected(settings.isServerButtonsUseText());
		serverButtonsUseTextCheckBox.addActionListener(e -> gui.applyServerButtonStyle(!serverButtonsUseTextCheckBox.isSelected()));
		c.gridy = 9; c.gridx = 0; c.gridwidth = 2; appearanceSection.add(serverButtonsUseTextCheckBox, c);
		minRam.setMinimumSize(new Dimension(50, minRam.getPreferredSize().height));
		maxRam.setMinimumSize(new Dimension(50, maxRam.getPreferredSize().height));
		fontSpinner.setMinimumSize(new Dimension(50, fontSpinner.getPreferredSize().height));
		themeBox.setMinimumSize(new Dimension(80, themeBox.getPreferredSize().height));
		settingsContentInner.add(appearanceSection);
		// Set the initial editor color scheme to match the loaded theme.
		FileEditor.setAppTheme(settings.getTheme());

		JPanel settingsContent = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				return settingsContentInner.getPreferredSize();
			}
		};
		settingsContent.add(settingsContentInner, BorderLayout.NORTH);

		JScrollPane settingsScroll = new JScrollPane(settingsContent);
		settingsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		settingsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		settingsScroll.getVerticalScrollBar().setUnitIncrement(24);
		settingsScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
		add(settingsScroll, BorderLayout.CENTER);

		// HiDPI startup fix: at construction time, font metrics and preferred sizes are measured
		// against an off-screen default graphics context that may not reflect the actual display
		// DPI. This is the same root cause as the "toggle theme to fix layout" workaround.
		// Calling updateComponentTreeUI once after the panel is first shown reinstalls all UI
		// delegates using the real screen graphics context — identical to what theme-switching does.
		addHierarchyListener(new HierarchyListener() {
			private boolean corrected = false;
			@Override
			public void hierarchyChanged(HierarchyEvent e) {
				if (!corrected && isShowing()
						&& (e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
					corrected = true;
					SwingUtilities.invokeLater(() -> {
						SwingUtilities.updateComponentTreeUI(SettingsPanel.this);
						revalidate();
					});
				}
			}
		});
	}

	void updateAccentPanelForTheme(Theme theme) {
		if (accentColorPanel == null || accentThemeControlledLabel == null) return;

		if (theme != null && theme.honorsAccentColor()) {
			lblAccentWrap.setVisible(true);
			accentRow.setVisible(true);
			accentThemeControlledLabel.setVisible(false);
		} else if (theme != null && theme.partiallyHonorsAccentColor()) {
			lblAccentWrap.setVisible(true);
			accentRow.setVisible(true);
			accentThemeControlledLabel.setVisible(true);
		} else {
			lblAccentWrap.setVisible(false);
			accentRow.setVisible(false);
			accentThemeControlledLabel.setVisible(false);
		}
	}

	// --- Getters for SpigotGUI (save, start server, apply accent) ---

	public JSpinner getMinRam() { return minRam; }
	public JSpinner getMaxRam() { return maxRam; }
	public JTextField getCustomJvmArgsField() { return customJvmArgsField; }
	public JTextField getCustomJvmSwitchesField() { return customJvmSwitchesField; }
	public JSpinner getShutdownCountdownSpinner() { return shutdownCountdownSpinner; }
	public JComboBox<String> getThemeBox() { return themeBox; }
	public JSpinner getFontSpinner() { return fontSpinner; }
	public JLabel getLblTheme() { return lblTheme; }
	public JCheckBox getConsoleDarkModeCheckBox() { return consoleDarkModeCheckBox; }
	public JCheckBox getConsoleAdaptiveColorsCheckBox() { return consoleAdaptiveColorsCheckBox; }
	public JCheckBox getDisableConsoleColorsCheckBox() { return disableConsoleColorsCheckBox; }
	public JCheckBox getOpenFilesInSystemDefaultCheckBox() { return openFilesInSystemDefaultCheckBox; }
	public JCheckBox getManualConsoleScrollStickyCheckBox() { return manualConsoleScrollStickyCheckBox; }
	public JCheckBox getServerButtonsUseTextCheckBox() { return serverButtonsUseTextCheckBox; }
	public JCheckBox getDisplayKillButtonCheckBox() { return displayKillButtonCheckBox; }
	public JCheckBox getConsoleWrapWordBreakOnlyCheckBox() { return consoleWrapWordBreakOnlyCheckBox; }
	public AccentColorPanel getAccentColorPanel() { return accentColorPanel; }

	/** Returns the custom JVM path entered by the user, or null if set to the default. */
	public String getCustomJvmPath() {
		if (customJvmPathField == null) return null;
		String text = customJvmPathField.getText();
		if (text == null || text.isEmpty() || text.equals("<default>")) return null;
		return text;
	}

	public int getShutdownCountdownSeconds() {
		if (shutdownCountdownSpinner != null) {
			Object v = shutdownCountdownSpinner.getValue();
			if (v instanceof Number) return Math.max(0, ((Number) v).intValue());
		}
		return 0;
	}

	public int getAccentColorRgbFromUI() {
		if (accentColorPanel != null && accentColorPanel.getSelectedRgb() != 0)
			return accentColorPanel.getSelectedRgb();
		return settings != null ? settings.getAccentColorRgb() : 0x0096E6;
	}

	public Theme getSelectedTheme() {
		Object sel = themeBox != null ? themeBox.getSelectedItem() : null;
		if (sel == null || sel.toString().isEmpty()) return settings.getTheme();
		Theme t = Theme.fromDisplayName(sel.toString());
		return t != null ? t : settings.getTheme();
	}

	/** Builds a Settings instance from current UI values; used when saving on close or Apply. */
	public Settings buildSettingsFromUI(File jarFile) {
		return new Settings.Builder()
			.serverSettings(new ServerSettings(
				((Number) minRam.getValue()).intValue(),
				((Number) maxRam.getValue()).intValue(),
				customJvmArgsField.getText(),
				customJvmSwitchesField.getText(),
				jarFile,
				getCustomJvmPath()
			))
			.theme(getSelectedTheme())
			.fontSize(((Number) fontSpinner.getValue()).intValue())
			.consoleDarkMode(consoleDarkModeCheckBox.isSelected())
			.consoleAdaptiveColors(consoleAdaptiveColorsCheckBox.isSelected())
			.consoleColorsEnabled(!disableConsoleColorsCheckBox.isSelected())
			.openFilesInSystemDefault(openFilesInSystemDefaultCheckBox.isSelected())
			.fileEditorTheme(settings.getFileEditorTheme())
			.manualConsoleScrollSticky(manualConsoleScrollStickyCheckBox.isSelected())
			.serverButtonsUseText(serverButtonsUseTextCheckBox.isSelected())
			.shutdownCountdownSeconds(getShutdownCountdownSeconds())
			.consoleWrapWordBreakOnly(consoleWrapWordBreakOnlyCheckBox.isSelected())
			.accentColorRgb(getAccentColorRgbFromUI())
			.displayKillButton(displayKillButtonCheckBox.isSelected())
			.build();
	}
}
