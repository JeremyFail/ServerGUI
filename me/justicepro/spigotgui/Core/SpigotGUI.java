package me.justicepro.spigotgui.Core;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.JTextPane;
import javax.swing.ToolTipManager;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.tree.DefaultMutableTreeNode;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import me.justicepro.spigotgui.JModulePanel;
import me.justicepro.spigotgui.SettingsIO;
import me.justicepro.spigotgui.Module;
import me.justicepro.spigotgui.ModuleManager;
import me.justicepro.spigotgui.ProcessException;
import me.justicepro.spigotgui.ReporterWindow;
import me.justicepro.spigotgui.Server;
import me.justicepro.spigotgui.ServerSettings;
import me.justicepro.spigotgui.Settings;
import me.justicepro.spigotgui.Theme;
import me.justicepro.spigotgui.Bridge.BridgeLockFile;
import me.justicepro.spigotgui.FileExplorer.FileModel;
import me.justicepro.spigotgui.RemoteAdmin.CorePermissions;
import me.justicepro.spigotgui.RemoteAdmin.Permission;
import me.justicepro.spigotgui.RemoteAdmin.PacketHandlers.ServerHandler;
import me.justicepro.spigotgui.RemoteAdmin.Server.RServer;
import me.justicepro.spigotgui.Utils.AppIcons;
import me.justicepro.spigotgui.Utils.ConsoleColor;
import me.justicepro.spigotgui.Utils.ConsoleStyleHelper;
import me.justicepro.spigotgui.Utils.Dialogs;
import me.justicepro.spigotgui.Utils.Player;

/**
 * Main application frame for ServerGUI. Builds the tabbed UI (Console, Players,
 * Resources, Settings, Files, Module List, Remote Admin, About/Help) and coordinates server
 * lifecycle, settings persistence, and console output. Tab content is delegated to panel classes
 * in this package (ConsolePanel, PlayersPanel, FilesPanel, etc.); Settings and server top bar
 * remain here for historical coupling with save/load and theme/accent logic.
 */
public class SpigotGUI extends JFrame {

	// --- Window and layout ---
	private static final int MIN_SIZE_BASE_WIDTH = 650;
	private static final int MIN_SIZE_BASE_HEIGHT = 400;
	private JPanel contentPane;

	private JTextPane consoleTextPane;
	private ConsoleStyleHelper consoleStyleHelper;
	/** Scroll pane wrapping the console text pane; used for stick-to-bottom behavior. */
	private JScrollPane consoleScrollPane;
	/** When true, new console output scrolls to the bottom of the console view. */
	private volatile boolean consoleStickToBottom = true;
	/** Ignore scroll bar listener until this time (ms); avoids treating programmatic scroll or append-induced scroll as user scroll. */
	private volatile long ignoreScrollBarUntil = 0;
	/** True while we're scrolling from the command timer so we don't re-enable sticky. */
	private volatile boolean scrollingFromCommand = false;
	/** Drawn green/red circle for server status; replaces previous image-based icon. */
	private StatusCirclePanel serverStatusCircle;

	private JLabel lblServerStatusText;

	/** Theme at app startup; used to decide if we can apply a new theme without restart (same family only). */
	private static Theme initialThemeForSession;
	/** Fixed size for theme label so it never changes when toggling text (avoids layout shift). */
	private static int themeLabelWidth = 0;
	private static int themeLabelHeight = 0;

	public static Server server = null;

	private ResourcesPanel resourcesPanel;
	/** Settings tab; holds all settings controls and buildSettingsFromUI() for save. */
	private SettingsPanel settingsPanel;

	private JButton btnStartServer;
	private JButton btnStopServer;
	private JButton btnKillServer;
	private JButton btnRestartServer;
	private JTable playersTable;
	private JLabel lblPlayersOnlineCount;

	public static SpigotGUI instance;

	public static ArrayList<Player> players = new ArrayList<>();

	private static Module module;

	private boolean restart = false;

	/** Manual control for console scroll sticky; visible only when Settings "Manual console scroll sticky" is on. */
	private JCheckBox chkConsoleScrollSticky;
	/** When true, sticky is controlled only by the manual checkbox; scroll bar does not update it. */
	private boolean manualConsoleScrollStickyMode = false;
	/** Current settings (theme, accent, etc.); set in constructor. */
	private Settings settings;
	private FileModel fileModel;

	public static File jarFile;

	public static ServerHandler serverHandler = new ServerHandler();

	public static final String versionTag = getVersionTag();

	// TODO: Refactor to a helper class?
	/** Messages logged before the console is ready (e.g. from SettingsIO); flushed at end of constructor. */
	private static final java.util.List<String> pendingStartupMessages = new java.util.ArrayList<>();

	/** 
	 * Get the version tag from the pom.xml file.
	 * @return The version tag.
	 */
	private static String getVersionTag() {
		String v = SpigotGUI.class.getPackage().getImplementationVersion();
		return (v != null && !v.isEmpty()) ? v : "dev";
	}

	//public static ServerSettings serverSettings;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		// Delegate to bridge process if launched with --bridge flag.
		if (args.length > 0 && "--bridge".equals(args[0])) {
			me.justicepro.spigotgui.Bridge.ServerBridge.main(args);
			return;
		}

		// Global handler: any thread that throws an uncaught exception gets its stack
		// trace printed to the GUI console (and to stderr as a fallback).
		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			logThrowable("Uncaught exception in thread \"" + thread.getName() + "\"", throwable);
		});

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Settings settings = loadSettings();
					Theme theme = Theme.resolveForCurrentPlatform(settings.getTheme());
					// Apply accent color before LaF so FlatLaf uses it (setGlobalExtraDefaults is required)
					String accentHex = String.format("#%06X", 0xFFFFFF & settings.getAccentColorRgb());
					FlatLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", accentHex));
					UIManager.put("@accentColor", accentHex);
					try {
						UIManager.setLookAndFeel(theme.getLookAndFeel());
					} catch (Throwable t) {
						theme = Theme.getFallbackTheme();
						UIManager.setLookAndFeel(theme.getLookAndFeel());
						if (theme != settings.getTheme()) {
							settings = settings.toBuilder().theme(theme).build();
							try { saveSettings(settings); } catch (IOException e) { }
						}
					}
					if (theme != settings.getTheme()) {
						settings = settings.toBuilder().theme(theme).build();
						try { saveSettings(settings); } catch (IOException e) { }
					}
					instance = new SpigotGUI(settings);
					instance.setVisible(true);
					instance.checkForOrphanedServerOnStartup();
				} catch (Exception e) {
					ReporterWindow reporter = new ReporterWindow(e);
					reporter.setVisible(true);
				}
			}
		});
	}

	/**
	 * Create the frame.
	 * @param settings 
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 * @throws UnsupportedLookAndFeelException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public SpigotGUI(Settings settings) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {
		this.settings = settings;
		ServerSettings serverSettings = settings.getServerSettings();
		if (serverSettings.getJarFile() != null) {
			jarFile = serverSettings.getJarFile();
		}
		if (initialThemeForSession == null) {
			initialThemeForSession = settings.getTheme();
		}
		setIconImages(AppIcons.getIcons());
		setTitle("ServerGUI (" + versionTag + ")");
		module = new ModuleCore();
		module.init();
		ModuleManager.registerModule(module);

		ModuleManager.registerModule(serverHandler);
		serverHandler.init();

		ModuleManager.init();

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {

				boolean close = true;

				if (server!=null) {
					if (server.isRunning()) {
						JOptionPane.showMessageDialog(SpigotGUI.this, "A Server is running, you can't close ServerGUI until you stop it.");
						close = false;
					}
				}

				Settings s = settingsPanel.buildSettingsFromUI(jarFile);
				
				if (resourcesPanel != null) {
					resourcesPanel.stopPolling();
				}
				try {
					saveSettings(s);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if (close) {
					System.exit(0);
				}

			}
		});
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setSize(700, 600);
		centerOnActiveMonitor();
		setMinimumSize(new Dimension(MIN_SIZE_BASE_WIDTH, MIN_SIZE_BASE_HEIGHT));
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));
		ToolTipManager.sharedInstance().setDismissDelay(15000);

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);

		// --- Console tab: built by ConsolePanel; we keep refs for addToConsole and Settings ---
		int fontSize = settings.getFontSize();
		Font consoleFont = getConsoleMonospaceFont(fontSize);
		ConsolePanel consolePanel = new ConsolePanel(this, consoleFont, settings.isConsoleDarkMode(),
				settings.isConsoleColorsEnabled(), settings.isConsoleWrapWordBreakOnly(),
				settings.isManualConsoleScrollSticky(), consoleStickToBottom);
		consoleTextPane = consolePanel.getConsoleTextPane();
		consoleScrollPane = consolePanel.getConsoleScrollPane();
		chkConsoleScrollSticky = consolePanel.getChkConsoleScrollSticky();
		consoleStyleHelper = consolePanel.getConsoleStyleHelper();
		consoleStyleHelper.setAdaptiveColors(settings.isConsoleAdaptiveColors());
		manualConsoleScrollStickyMode = settings.isManualConsoleScrollSticky();

		// --- Top bar: server controls and status (visible in all tabs) ---
		btnStartServer = new JButton("Start Server");
		btnStartServer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				if (server != null) {

					if (server.isRunning()) {
						JOptionPane.showMessageDialog(SpigotGUI.this, "A Server is already running.");
					}else {
						try {
							startServer();
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}

				}else {
					try {
						startServer();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}

			}
		});

		btnStopServer = new JButton("Stop Server");
		btnStopServer.setEnabled(false);
		btnStopServer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				if (server != null) {
					if (server.isRunning()) {
						if (server.isAdopted()) {
							JOptionPane.showMessageDialog(SpigotGUI.this,
									"<html>This server was not started by ServerGUI - graceful stop (stdin) is not available.<br>"
									+ "Use <b>Kill Server</b> to forcefully terminate the process.</html>",
									"Cannot Stop Gracefully",
									JOptionPane.WARNING_MESSAGE);
						} else {
							runShutdownOrRestartCountdown(getShutdownCountdownSeconds(), false);
						}
					} else {
						JOptionPane.showMessageDialog(SpigotGUI.this, "There are no servers running.");
					}
				}
			}
		});

		serverStatusCircle = new StatusCirclePanel();

		String serverIP;
		try {
			serverIP = Inet4Address.getLocalHost().getHostAddress();
		} catch (Exception e) {
			serverIP = "Unknown";
		}
		JLabel lblServerIP = new JLabel("Server IP: " + serverIP);
		lblServerIP.setToolTipText("Local address for this machine. Players can use this to connect (e.g. for LAN).");
		lblServerIP.setMinimumSize(new Dimension(200, 20));
		lblServerIP.setPreferredSize(new Dimension(200, 20));

		btnRestartServer = new JButton("Restart Server");
		btnRestartServer.setEnabled(false);
		btnRestartServer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				if (server != null) {
					if (server.isRunning()) {
						if (server.isAdopted()) {
							JOptionPane.showMessageDialog(SpigotGUI.this,
									"<html>This server was not started by ServerGUI - graceful restart is not available.<br>"
									+ "Use <b>Kill Server</b> to terminate it, then start a new server.</html>",
									"Cannot Restart",
									JOptionPane.WARNING_MESSAGE);
						} else {
							runShutdownOrRestartCountdown(getShutdownCountdownSeconds(), true);
						}
					} else {
						JOptionPane.showMessageDialog(SpigotGUI.this, "There are no servers running.");
					}
				}
			}
		});

		btnKillServer = new JButton("Kill Server");
		btnKillServer.setEnabled(false);
		btnKillServer.setVisible(settings.isDisplayKillButton());
		btnKillServer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (server != null && server.isRunning()) {
					int confirm = JOptionPane.showConfirmDialog(
						SpigotGUI.this,
						"<html>Are you sure you want to forcefully kill the server?<br>"
						+ "This will immediately terminate the server process and may cause data loss or corruption.<br>"
						+ "<b>Only use this if the server is hung and won't stop normally.</b></html>",
						"Kill Server - Confirm",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE);
					if (confirm == JOptionPane.YES_OPTION) {
						server.kill();
					}
				} else {
					JOptionPane.showMessageDialog(SpigotGUI.this, "There are no servers running.");
				}
			}
		});

		lblServerStatusText = new JLabel("Status: Offline");
		lblServerStatusText.setHorizontalAlignment(SwingConstants.RIGHT);
		lblServerStatusText.setMinimumSize(new Dimension(130, 20));
		lblServerStatusText.setPreferredSize(new Dimension(130, 20));

		// Top bar: server controls (left), server IP (center), status (right), visible in all tabs
		JPanel topBar = new JPanel(new BorderLayout(0, 0));
		JPanel topBarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		topBarLeft.add(btnStartServer);
		topBarLeft.add(btnStopServer);
		topBarLeft.add(btnKillServer);
		topBarLeft.add(btnRestartServer);
		topBarLeft.add(Box.createHorizontalStrut(6));
		topBarLeft.add(lblServerIP);
		JPanel topBarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
		topBarRight.add(lblServerStatusText);
		serverStatusCircle.setPreferredSize(new Dimension(26, 26));
		topBarRight.add(serverStatusCircle);
		applyServerButtonStyle(!settings.isServerButtonsUseText());
		topBar.add(topBarLeft, BorderLayout.WEST);
		topBar.add(topBarRight, BorderLayout.EAST);
		contentPane.add(topBar, BorderLayout.NORTH);
		tabbedPane.setBorder(new EmptyBorder(8, 0, 0, 0));
		contentPane.add(tabbedPane, BorderLayout.CENTER);

		tabbedPane.addTab("Console", null, consolePanel, null);

		// --- Players tab ---
		PlayersPanel playersPanel = new PlayersPanel(this);
		playersTable = playersPanel.getPlayersTable();
		lblPlayersOnlineCount = playersPanel.getLblPlayersOnlineCount();
		tabbedPane.addTab("Players", null, playersPanel, null);

		setActive(false);
		updateServerButtonStates(false);
		setTableAsList(playersTable, players);

		// Resources tab: memory/CPU graph and stats (after Players, before Settings)
		resourcesPanel = new ResourcesPanel(serverSettings);
		resourcesPanel.startPolling();
		tabbedPane.addTab("Resources", null, resourcesPanel, null);

		// --- Settings tab (built by SettingsPanel) ---
		settingsPanel = new SettingsPanel(this, settings, serverSettings);

		// --- Files tab ---
		FilesPanel filesPanel = new FilesPanel(this, getDefaultDirectory(), settings.isOpenFilesInSystemDefault());
		fileModel = filesPanel.getFileModel();
		tabbedPane.addTab("Files", null, filesPanel, null);

		// --- Module List tab ---
		tabbedPane.addTab("Module List", null, new ModuleListPanel(), null);

		tabbedPane.addTab("Remote Admin", null, new RemoteAdminPanel(this), null);

		tabbedPane.addTab("Settings", null, settingsPanel, null);

		tabbedPane.addTab("About/Help", null, new AboutPanel(this), null);

		for (Module module : ModuleManager.modules) {

			if (module.getPage() != null) {
				JPanel panel1 = new JPanel();
				JModulePanel p = module.getPage();

				JScrollPane sp = new JScrollPane();
				sp.add(p);

				panel1.setLayout(new BoxLayout(panel1, BoxLayout.X_AXIS));

				panel1.add(p);

				tabbedPane.addTab(p.getTitle(), null, panel1, null);
			}

		}

		// Flush any messages buffered before the console was ready (e.g. from SettingsIO).
		// Note: we call appendToConsolePane directly rather than addToConsole because
		// `instance` is not yet assigned at this point in the constructor (the assignment
		// `instance = new SpigotGUI(settings)` happens after the constructor returns), so
		// addToConsole would see instance == null and silently re-buffer the messages.
		java.util.List<String> startupMessages = new java.util.ArrayList<>(pendingStartupMessages);
		pendingStartupMessages.clear();
		for (String msg : startupMessages) {
			appendToConsolePane(msg);
		}

	}

	/**
	 * Centers this window on whichever monitor the mouse cursor is currently on,
	 * falling back to the primary screen if the pointer cannot be queried.
	 * 
	 * Note that there are edge-cases where this may launch it on the wrong monitor
	 * (e.g. if the mouse is on a secondary monitor but the user launched ServerGUI
	 * from the command-line or via keyboard input).
	 */
	private void centerOnActiveMonitor() {
		GraphicsDevice target = null;
		PointerInfo pi = MouseInfo.getPointerInfo();
		if (pi != null) {
			Point mouse = pi.getLocation();
			for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
				if (gd.getDefaultConfiguration().getBounds().contains(mouse)) {
					target = gd;
					break;
				}
			}
		}
		if (target == null) {
			target = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		}
		Rectangle sb = target.getDefaultConfiguration().getBounds();
		setLocation(sb.x + (sb.width - getWidth()) / 2, sb.y + (sb.height - getHeight()) / 2);
	}

	public static void saveSettings(Settings settings) throws IOException {
		SettingsIO.save(settings);
	}

	public static Settings loadSettings() throws IOException {
		return SettingsIO.load();
	}

	/** Add nodes from under "dir" into curTop. Highly recursive. */
	DefaultMutableTreeNode addNodes(DefaultMutableTreeNode curTop, File dir) {
		String curPath = dir.getPath();
		DefaultMutableTreeNode curDir = new DefaultMutableTreeNode(curPath);
		if (curTop != null) { // should only be null at root
			curTop.add(curDir);
		}
		Vector<String> ol = new Vector<>();
		String[] tmp = dir.list();
		for (int i = 0; i < tmp.length; i++)
			ol.addElement(tmp[i]);
		Collections.sort(ol, String.CASE_INSENSITIVE_ORDER);
		File f;
		Vector<String> files = new Vector<>();
		// Make two passes, one for Dirs and one for Files. This is #1.
		for (int i = 0; i < ol.size(); i++) {
			String thisObject = ol.elementAt(i);
			String newPath;
			if (curPath.equals("."))
				newPath = thisObject;
			else
				newPath = curPath + File.separator + thisObject;
			if ((f = new File(newPath)).isDirectory())
				addNodes(curDir, f);
			else
				files.addElement(thisObject);
		}
		// Pass two: for files.
		for (int fnum = 0; fnum < files.size(); fnum++)
			curDir.add(new DefaultMutableTreeNode(files.elementAt(fnum)));
		return curDir;
	}

	public void setTableAsList(JTable playersTable, List<Player> players) {
		DefaultTableModel model = (DefaultTableModel) playersTable.getModel();
		model.setRowCount(0);
		for (Player p : players) {
			model.addRow(new Object[] { p.username, p.lastIP });
		}
		if (players.isEmpty()) {
			model.addRow(new Object[] { "(No players online)", "" });
		}
		updatePlayersOnlineCountLabel(players.size());
	}

	private void updatePlayersOnlineCountLabel(int count) {
		if (lblPlayersOnlineCount == null) return;
		String maxStr = getServerMaxPlayers();
		lblPlayersOnlineCount.setText(maxStr != null
				? "Players online: " + count + " / " + maxStr
				: "Players online: " + count);
	}

	/** Reads max-players from server.properties if present; returns null if not found or not started. */
	private String getServerMaxPlayers() {
		return getServerMaxPlayersStatic();
	}

	/** Static version for use from ResourcesPanel etc. Reads max-players from server.properties. */
	public static String getServerMaxPlayersStatic() {
		try {
			File f = new File("server.properties");
			if (!f.canRead()) return null;
			for (String line : Files.readAllLines(f.toPath())) {
				line = line.trim();
				if (line.startsWith("max-players=") && !line.startsWith("#")) {
					return line.substring("max-players=".length()).trim();
				}
			}
		} catch (Exception ignored) { }
		return null;
	}

	public static void updatePlayerData(Player player) {

		boolean contains = false;

		for (int i = 0; i < players.size(); i++) {
			Player p = players.get(i);

			if (p.username.equalsIgnoreCase(player.username)) {
				contains = true;
				p.lastIP = player.lastIP;
			}

		}

		if (!contains) {
			players.add(player);
		}

		instance.setTableAsList(instance.playersTable, players);
	}

	public static void removePlayerData(String player) {
		try {
			ArrayList<Player> plys = new ArrayList<>(players);
			for (int i = 0; i < plys.size(); i++) {
				Player p = plys.get(i);
				if (p.username.equalsIgnoreCase(player)) {
					players.remove(i);
					System.out.println("Player Found");
					break;
				}
			}
			instance.setTableAsList(instance.playersTable, players);
		} catch (Exception e) {
			Dialogs.showError("An error occurred while updating player data.");
		}
	}

	private static String noguiArgs(String extraJarArgs) {
		String t = extraJarArgs == null ? "" : extraJarArgs.trim();
		return t.isEmpty() ? "nogui" : "nogui " + t;
	}

	/**
	 * Checks on startup whether a bridge lock file or orphaned server process exists for the
	 * configured JAR, and prompts the user to reconnect or handle accordingly.
	 * Runs the OSHI probe off the EDT to avoid a startup freeze.
	 */
	private void checkForOrphanedServerOnStartup() {
		if (jarFile == null) return;
		if (server != null && server.isRunning()) return;
		final File jar = jarFile;
		new Thread(() -> {
			// Priority 1: a bridge lock file exists - try to reconnect to the bridge.
			BridgeLockFile lock = BridgeLockFile.read(jar);
			if (lock != null) {
				boolean bridgeAlive = Server.isProcessAlive(lock.bridgePid);
				if (bridgeAlive) {
					SwingUtilities.invokeLater(() -> promptToReconnectBridge(lock));
					return;
				}
				// Bridge is gone - delete the stale lock file.
				BridgeLockFile.delete(jar);
			}

			// Priority 2: no bridge, but the server JAR is still running as an orphan.
			long pid = Server.findOrphanedPid(jar);
			if (pid >= 0) {
				SwingUtilities.invokeLater(() -> promptOrphanServerNoBridge(pid));
			}
		}).start();
	}

	/** Prompts the user to reconnect to a running bridge process. */
	private void promptToReconnectBridge(BridgeLockFile lock) {
		if (server != null && server.isRunning()) return;
		int choice = JOptionPane.showConfirmDialog(
				this,
				"<html>ServerGUI found a running server (PID: " + lock.bridgePid + ") "
				+ "with the JAR <b>" + jarFile.getName() + "</b>.<br><br>"
				+ "Do you want to reconnect?<br>"
				+ "Full console interaction and graceful stop will be available.</html>",
				"Reconnect to Running Server?",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) return;

		String args = settingsPanel != null ? settingsPanel.getCustomJvmArgsField().getText().trim() : "";
		String switches = settingsPanel != null
				? Server.makeMemory(settingsPanel.getMinRam().getValue() + "M", settingsPanel.getMaxRam().getValue() + "M")
					+ " " + settingsPanel.getCustomJvmSwitchesField().getText()
				: "";
		String jvmPath = settingsPanel != null ? settingsPanel.getCustomJvmPath() : null;

		Server s = Server.connectViaBridge(lock, jarFile, noguiArgs(args), switches, jvmPath);
		if (s == null) {
			JOptionPane.showMessageDialog(this,
					"<html>Could not connect to the server.<br>"
					+ "It may have already closed.</html>",
					"Reconnect Failed", JOptionPane.WARNING_MESSAGE);
			BridgeLockFile.delete(jarFile);
			return;
		}
		server = s;
		try { setActive(true); } catch (IOException e) { e.printStackTrace(); }
		addToConsole(getPrefix() + ConsoleColor.YELLOW
				+ "Reconnected to server (PID: " + lock.bridgePid + "). Full console interaction and graceful stop are available."
				+ ConsoleColor.RESET);
	}

	/**
	 * Called when a server process is detected without a bridge. Offers only Kill as an option
	 * since we have no way to communicate with the server gracefully.
	 */
	private void promptOrphanServerNoBridge(long pid) {
		if (server != null && server.isRunning()) return;
		Object[] options = {"Kill Server", "Ignore"};
		int choice = JOptionPane.showOptionDialog(
				this,
				"<html>A server process running <b>" + jarFile.getName() + "</b> was detected (PID: " + pid + "),<br>"
				+ "but cannot be fully reconnected.<br><br>"
				+ "<b>Due to this, graceful shutdown is not possible.</b><br>"
				+ "The only available option is to forcefully kill the server,<br>"
				+ "which may cause world data loss or corruption.<br>"
				+ "If possible, you may wish to <code>/stop</code> the server in-game.<br><br>"
				+ "Kill the server now, or ignore?</html>",
				"Orphaned Server - Cannot Reconnect",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null, options, options[1]);
		if (choice == 0) {
			adoptOrphanedServer(pid);
			server.kill();
		}
	}

	/**
	 * Adopts an already-running server process by PID, wires it into the GUI,
	 * starts a monitor thread, and logs a notice to the console.
	 */
	private void adoptOrphanedServer(long pid) {
		String args = settingsPanel != null ? settingsPanel.getCustomJvmArgsField().getText().trim() : "";
		String switches = settingsPanel != null
				? Server.makeMemory(settingsPanel.getMinRam().getValue() + "M", settingsPanel.getMaxRam().getValue() + "M")
					+ " " + settingsPanel.getCustomJvmSwitchesField().getText()
				: "";
		String jvmPath = settingsPanel != null ? settingsPanel.getCustomJvmPath() : null;
		server = Server.adoptOrphanedProcess(pid, jarFile, noguiArgs(args), switches, jvmPath);
		server.startAdoptedMonitorThread();
		try {
			setActive(true);
		} catch (IOException e) {
			e.printStackTrace();
		}
		addToConsole(getPrefix() + ConsoleColor.YELLOW
				+ "Connected to existing server process (PID: " + pid + ")."
				+ ConsoleColor.RESET);
		addToConsole(getPrefix() + ConsoleColor.RED
				+ "Console interaction is not available. Use 'Kill Server' to terminate the server. You may need to enable 'Display Kill Button' in Settings to see the button."
				+ ConsoleColor.RESET);
	}

	public void startServer() throws IOException {
		startServer(settingsPanel.getCustomJvmArgsField().getText().trim(), Server.makeMemory(settingsPanel.getMinRam().getValue() + "M", settingsPanel.getMaxRam().getValue() + "M") + " " + settingsPanel.getCustomJvmSwitchesField().getText());
	}

	public void startServer(String args, String switches) throws IOException {
		try {
			javax.swing.text.Document doc = consoleTextPane.getDocument();
			doc.remove(0, doc.getLength());
		} catch (javax.swing.text.BadLocationException e) {
			// ignore
		}
		File eula = new File("eula.txt");

		if (!eula.exists()) {

			int result = JOptionPane.showOptionDialog(SpigotGUI.this,
					"Do you agree to the Minecraft Eula? (https://account.mojang.com/documents/minecraft_eula)", "Message", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);

			if (result==JOptionPane.YES_OPTION) {
				Files.copy(getClass().getResourceAsStream("/eula.txt"), eula.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}else {
				JOptionPane.showMessageDialog(SpigotGUI.this, "You must agree to the EULA to run a server.");
				return;
			}

		}

		if (jarFile==null) {

			File file = new File("server.jar");

			if (file.exists()) {
				jarFile = file;
			}else {
				JOptionPane.showMessageDialog(SpigotGUI.this, "There is no selected Server JAR file. Check your Settings and try again.");
				return;
			}

		}

		if (!jarFile.exists()) {
			JOptionPane.showMessageDialog(SpigotGUI.this, "The selected Server JAR file does not exist.");
			return;
		}

		// Check if a bridge is already running for this JAR.
		BridgeLockFile existingLock = BridgeLockFile.read(jarFile);
		if (existingLock != null && Server.isProcessAlive(existingLock.bridgePid)) {
			int choice = JOptionPane.showConfirmDialog(
					SpigotGUI.this,
					"<html>A server with process (PID: " + existingLock.bridgePid + ") for the JAR file <b>"
					+ jarFile.getName() + "</b> is already running.<br><br>"
					+ "Do you want to reconnect to it?</html>",
					"Server Already Running - Reconnect?",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.QUESTION_MESSAGE);
			if (choice == JOptionPane.YES_OPTION) {
				promptToReconnectBridge(existingLock);
				return;
			}
			return; // Don't start a second instance
		}
		if (existingLock != null) {
			BridgeLockFile.delete(jarFile); // stale lock
		}

		// Check if this JAR is running without a bridge (orphaned).
		long existingPid = Server.findOrphanedPid(jarFile);
		if (existingPid >= 0) {
			promptOrphanServerNoBridge(existingPid);
			return;
		}

		// Launch via bridge.
		File selfJar = resolveSelfJar();
		if (selfJar == null) {
			// Fallback: no self-JAR found (e.g. running from IDE). Use direct launch.
			addToConsole(getPrefix() + ConsoleColor.YELLOW
					+ "Running from IDE/classpath - Using direct launch."
					+ ConsoleColor.RESET);
			launchServerDirect(args, switches);
			return;
		}

		String javaExe = resolveJavaExeForBridge();
		try {
			server = Server.startViaBridge(javaExe, selfJar, jarFile, noguiArgs(args), switches,
					settingsPanel != null ? settingsPanel.getCustomJvmPath() : null);
			setActive(true);
			addToConsole(getPrefix() + ConsoleColor.YELLOW
					+ "Server started. Console interaction and graceful stop are available."
					+ ConsoleColor.RESET);
		} catch (IOException e) {
			addToConsole(getPrefix() + ConsoleColor.RED
					+ "Failed to start server via bridge: " + e.getMessage()
					+ " - falling back to direct launch." + ConsoleColor.RESET);
			launchServerDirect(args, switches);
		}

	}

	/** Direct (non-bridge) server launch - legacy path used as fallback when running from IDE. */
	private void launchServerDirect(String args, String switches) throws IOException {
		if (server != null) {
			if (!server.isRunning()) {
				server = new Server(jarFile, noguiArgs(args), switches, settingsPanel.getCustomJvmPath());
				try {
					server.start();
				} catch (IOException | ProcessException e) {
					e.printStackTrace();
				}
				setActive(true);
			}
		} else {
			server = new Server(jarFile, noguiArgs(args), switches, settingsPanel.getCustomJvmPath());
			try {
				server.start();
			} catch (IOException | ProcessException e) {
				e.printStackTrace();
			}
			setActive(true);
		}
	}

	/**
	 * Resolves the running ServerGUI JAR file so the bridge process can be launched.
	 * Returns null when running from a classpath (IDE / tests).
	 */
	private File resolveSelfJar() {
		try {
			java.net.URL location = SpigotGUI.class.getProtectionDomain().getCodeSource().getLocation();
			if (location == null) return null;
			File f = new File(location.toURI());
			if (f.isFile() && f.getName().endsWith(".jar")) return f;
		} catch (Exception ignored) { }
		return null;
	}

	/** Resolves the java executable to use for launching the bridge process. */
	private String resolveJavaExeForBridge() {
		String home = System.getProperty("java.home");
		if (home != null) {
			File win = new File(home, "bin/java.exe");
			if (win.isFile()) return win.getAbsolutePath();
			File nix = new File(home, "bin/java");
			if (nix.isFile()) return nix.getAbsolutePath();
		}
		return "java";
	}


	public void setActive(boolean active) throws IOException {

		if (active==true) {
			serverStatusCircle.setOnline(true);
			lblServerStatusText.setText("Status: Online");
		}else {
			serverStatusCircle.setOnline(false);
			lblServerStatusText.setText("Status: Offline");
		}
		updateServerButtonStates(active);
	}

	/** Enable/disable Start, Stop, Kill, Restart based on whether the server is running. */
	private void updateServerButtonStates(boolean serverRunning) {
		Color enabledFg = UIManager.getColor("Button.foreground");
		if (enabledFg == null) enabledFg = Color.BLACK;
		Color disabledFg = UIManager.getColor("Button.disabledText");
		if (disabledFg == null) disabledFg = Color.GRAY;
		for (JButton btn : new JButton[] { btnStartServer, btnStopServer, btnKillServer, btnRestartServer }) {
			if (btn == null) continue;
			boolean enable = (btn == btnStartServer) ? !serverRunning : serverRunning;
			btn.setEnabled(enable);
			btn.setForeground(enable ? enabledFg : disabledFg);
			btn.repaint();
		}
	}

	/** Create play/stop/restart icons with a fixed color so we can set both normal and disabled icon. */
	private static Icon createPlayIcon(int size, final Color color) {
		return new Icon() {
			@Override public int getIconWidth() { return size; }
			@Override public int getIconHeight() { return size; }
			@Override public void paintIcon(Component c, Graphics g, int x, int y) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(color);
				GeneralPath play = new GeneralPath();
				play.moveTo(x + 5, y + 3);
				play.lineTo(x + 5, y + size - 3);
				play.lineTo(x + size - 4, y + size / 2);
				play.closePath();
				g2.fill(play);
				g2.dispose();
			}
		};
	}
	private static Icon createStopIcon(int size, final Color color) {
		return new Icon() {
			@Override public int getIconWidth() { return size; }
			@Override public int getIconHeight() { return size; }
			@Override public void paintIcon(Component c, Graphics g, int x, int y) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setColor(color);
				g2.fillRect(x + 5, y + 5, size - 10, size - 10);
				g2.dispose();
			}
		};
	}
	private static Icon createRestartIcon(int size, final Color color) {
		return new Icon() {
			@Override public int getIconWidth() { return size; }
			@Override public int getIconHeight() { return size; }
			@Override public void paintIcon(Component c, Graphics g, int x, int y) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(color);
				int cx = x + size / 2, cy = y + size / 2;
				int rad = size / 2 - 2;
				if (rad < 4) rad = 4;
				// Arc like Edge: starts upper-right (35°), runs CCW to top-right (315°) - gap at top
				Arc2D arc = new Arc2D.Float(cx - rad, cy - rad, rad * 2, rad * 2, 35, 280, Arc2D.OPEN);
				java.awt.Stroke old = g2.getStroke();
				g2.setStroke(new java.awt.BasicStroke(2f));
				g2.draw(arc);
				g2.setStroke(old);
				// Arrow at end of arc (315° = top-right), tip pointing down-right (tangent direction)
				double endRad = Math.toRadians(315);
				double cos = Math.cos(endRad), sin = Math.sin(endRad);
				double baseX = cx + rad * cos;
				double baseY = cy + rad * sin;
				double tx = -sin, ty = cos;  // tangent for CCW at 315° → down-right in Java
				double tipLen = 5.5, halfBase = 3.5;
				double tipX = baseX + tipLen * tx;
				double tipY = baseY + tipLen * ty;
				double px = cos * halfBase, py = sin * halfBase;  // perpendicular for base
				GeneralPath arrow = new GeneralPath();
				arrow.moveTo((float) tipX, (float) tipY);
				arrow.lineTo((float) (baseX - px), (float) (baseY - py));
				arrow.lineTo((float) (baseX + px), (float) (baseY + py));
				arrow.closePath();
				g2.fill(arrow);
				g2.dispose();
			}
		};
	}

	/** Load an SVG icon from classpath (e.g. /svg/refresh.svg); returns null if not available so caller can use programmatic fallback. */
	private Icon createSvgOrFallbackIcon(String path, int width, int height, Color color) {
		String resPath = path.startsWith("/") ? path.substring(1) : path;
		try {
			FlatSVGIcon icon = new FlatSVGIcon(resPath, width, height);
			if (color != null) {
				icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
			}
			return icon;
		} catch (Throwable t) {
			return null;
		}
	}

	/** Show icons (when useIcons true) or text (when false) on Start/Stop/Restart. Restart icon uses accent color. */
	void applyServerButtonStyle(boolean useIcons) {
		if (btnStartServer == null) return;
		int size = 18;
		Color enabledFg = UIManager.getColor("Button.foreground");
		if (enabledFg == null) enabledFg = Color.BLACK;
		Color disabledFg = UIManager.getColor("Button.disabledText");
		if (disabledFg == null) disabledFg = Color.GRAY;
		Color accentColor = settings != null ? new Color(0xFFFFFF & settings.getAccentColorRgb()) : new Color(0x0096E6);
		if (useIcons) {
			// Play/Stop/Warning: no color filter so SVG keeps its green/red/yellow; Restart: use accent
			Icon playIcon = createSvgOrFallbackIcon("/svg/execute.svg", size, size, null);
			Icon playIconDis = playIcon != null ? createSvgOrFallbackIcon("/svg/execute.svg", size, size, null) : null;
			Icon stopIcon = createSvgOrFallbackIcon("/svg/suspend.svg", size, size, null);
			Icon stopIconDis = stopIcon != null ? createSvgOrFallbackIcon("/svg/suspend.svg", size, size, null) : null;
			Icon killIcon = createSvgOrFallbackIcon("/svg/warning.svg", size, size, null);
			Icon killIconDis = killIcon != null ? createSvgOrFallbackIcon("/svg/warning.svg", size, size, null) : null;
			Icon restartIcon = createSvgOrFallbackIcon("/svg/refresh.svg", size, size, accentColor);
			Icon restartIconDis = restartIcon != null ? createSvgOrFallbackIcon("/svg/refresh.svg", size, size, disabledFg) : null;
			btnStartServer.setText("");
			Icon playDisabled = (playIcon instanceof FlatSVGIcon) ? ((FlatSVGIcon) playIcon).getDisabledIcon() : playIconDis;
			Icon stopDisabled = (stopIcon instanceof FlatSVGIcon) ? ((FlatSVGIcon) stopIcon).getDisabledIcon() : stopIconDis;
			Icon killDisabled = (killIcon instanceof FlatSVGIcon) ? ((FlatSVGIcon) killIcon).getDisabledIcon() : killIconDis;
			Icon restartDisabled = (restartIcon instanceof FlatSVGIcon) ? ((FlatSVGIcon) restartIcon).getDisabledIcon() : restartIconDis;
			btnStartServer.setIcon(playIcon != null ? playIcon : createPlayIcon(size, enabledFg));
			btnStartServer.setDisabledIcon(playIcon != null ? (playDisabled != null ? playDisabled : createPlayIcon(size, disabledFg)) : createPlayIcon(size, disabledFg));
			btnStartServer.setToolTipText("Start Server");
			btnStopServer.setText("");
			btnStopServer.setIcon(stopIcon != null ? stopIcon : createStopIcon(size, enabledFg));
			btnStopServer.setDisabledIcon(stopIcon != null ? (stopDisabled != null ? stopDisabled : createStopIcon(size, disabledFg)) : createStopIcon(size, disabledFg));
			btnStopServer.setToolTipText("Stop Server");
			if (btnKillServer != null) {
				btnKillServer.setText("");
				btnKillServer.setIcon(killIcon != null ? killIcon : null);
				btnKillServer.setDisabledIcon(killIcon != null ? (killDisabled != null ? killDisabled : null) : null);
				btnKillServer.setToolTipText("Kill Server");
			}
			btnRestartServer.setText("");
			btnRestartServer.setIcon(restartIcon != null ? restartIcon : createRestartIcon(size, accentColor));
			btnRestartServer.setDisabledIcon(restartIcon != null ? (restartDisabled != null ? restartDisabled : createRestartIcon(size, disabledFg)) : createRestartIcon(size, disabledFg));
			btnRestartServer.setToolTipText("Restart Server");
		} else {
			btnStartServer.setIcon(null);
			btnStartServer.setDisabledIcon(null);
			btnStartServer.setText("Start Server");
			btnStartServer.setToolTipText(null);
			btnStopServer.setIcon(null);
			btnStopServer.setDisabledIcon(null);
			btnStopServer.setText("Stop Server");
			btnStopServer.setToolTipText(null);
			if (btnKillServer != null) {
				btnKillServer.setIcon(null);
				btnKillServer.setDisabledIcon(null);
				btnKillServer.setText("Kill Server");
				btnKillServer.setToolTipText(null);
			}
			btnRestartServer.setIcon(null);
			btnRestartServer.setDisabledIcon(null);
			btnRestartServer.setText("Restart Server");
			btnRestartServer.setToolTipText(null);
		}
	}

	/** Current shutdown/restart countdown in seconds (from Settings panel or saved settings). */
	private int getShutdownCountdownSeconds() {
		if (settingsPanel != null) return settingsPanel.getShutdownCountdownSeconds();
		return settings != null ? settings.getShutdownCountdownSeconds() : 0;
	}

	/** Runs countdown then stops (and sets restart flag if isRestart). Called from EDT. */
	private void runShutdownOrRestartCountdown(int seconds, final boolean isRestart) {
		String verb = isRestart ? "Restart" : "Shutdown";
		try {
			if (seconds <= 0) {
				module.sendCommand("say Server " + verb + "!");
				stopServer();
				if (isRestart) restart = true;
				return;
			}
			module.sendCommand("say Server " + verb + "!");
			final int secs = seconds;
			new Thread(() -> {
				try {
					for (int i = secs; i > 0; i--) {
						boolean announce = (i == secs) || (i <= 10) || (i == 30) || (i == 60) || (i < secs && i % 60 == 0);
						if (announce)
							module.sendCommand("say " + verb + "ing in " + i + " second" + (i == 1 ? "" : "s") + (i == secs && secs >= 60 ? ". Get to a safe spot." : "."));
						Thread.sleep(1000);
					}
				} catch (InterruptedException | ProcessException e1) {
					Thread.currentThread().interrupt();
					e1.printStackTrace();
				}
				try {
					stopServer();
					if (isRestart) restart = true;
				} catch (ProcessException e) {
					e.printStackTrace();
				}
			}).start();
		} catch (ProcessException e1) {
			e1.printStackTrace();
		}
	}

	public void stopServer() throws ProcessException {

		if (server != null) {
			if (server.isRunning()) {
				server.sendCommand("stop");
			}
		}

	}

	/**
	 * Prints red lines at the top of the console when {@link me.justicepro.spigotgui.Server} rewrites legacy JVM switches.
	 */
	public static void appendJvmNormalizationWarnings(java.util.List<String> warnings) {
		if (instance == null || warnings == null || warnings.isEmpty()) return;
		addToConsole(getPrefix() + ConsoleColor.RED + "Some JVM switches in Server Settings were adjusted for this Java version:" + ConsoleColor.RESET);
		for (String w : warnings) {
			addToConsole(ConsoleColor.RED + "  - " + w + ConsoleColor.RESET);
		}
		addToConsole(ConsoleColor.RED + "Update JVM switches in Server Settings to modern forms to avoid this message." + ConsoleColor.RESET);
	}

	/**
	 * Logs a throwable (with full cause chain and stack traces) to both stderr and
	 * the GUI console. Safe to call at any time - if the console isn't ready the
	 * output is buffered and shown once the UI initialises.
	 *
	 * @param context  short description of what was happening (e.g. "Uncaught exception in thread \"foo\"")
	 * @param throwable the exception to log
	 */
	public static void logThrowable(String context, Throwable throwable) {
		// Always print to stderr first so nothing is lost if the GUI isn't ready.
		System.err.println("[ServerGUI] " + context + ":");
		throwable.printStackTrace(System.err);

		// Build a multi-line string for the console, walking the full cause chain.
		StringBuilder sb = new StringBuilder();
		sb.append(getPrefix()).append(ConsoleColor.RED).append(context).append(':').append(ConsoleColor.RESET);
		java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		boolean first = true;
		for (Throwable current = throwable; current != null && seen.add(current); current = current.getCause()) {
			if (!first) {
				sb.append('\n').append(ConsoleColor.RED).append("Caused by: ").append(current).append(ConsoleColor.RESET);
			} else {
				sb.append('\n').append(ConsoleColor.RED).append(current).append(ConsoleColor.RESET);
				first = false;
			}
			for (StackTraceElement el : current.getStackTrace()) {
				sb.append('\n').append(ConsoleColor.RED).append("    at ").append(el).append(ConsoleColor.RESET);
			}
		}
		addToConsole(sb.toString());
	}

	/** Returns the prefix for the console for internal SpigotGUI messages. */
	public static String getPrefix() {
		return ConsoleColor.BRIGHT_CYAN + "[ServerGUI] " + ConsoleColor.RESET;
	}

	/**
	 * Add a message to the GUI console. If the console isn't ready yet the message
	 * is buffered and flushed automatically once the UI finishes initialising, so
	 * callers do not need to worry about startup order.
	 */
	public static void addToConsole(String message) {
		if (instance == null || instance.consoleStyleHelper == null) {
			pendingStartupMessages.add(message);
			return;
		}
		instance.appendToConsolePane(message);
	}

	private void appendToConsolePane(String message) {
		// Run append and scroll on EDT so we can control whether the view scrolls at all.
		SwingUtilities.invokeLater(() -> {
			ignoreScrollBarUntil = System.currentTimeMillis() + 250;
			boolean wasStick = consoleStickToBottom;
			JViewport vp = consoleScrollPane != null ? consoleScrollPane.getViewport() : null;
			Point savedPos = (vp != null && !wasStick) ? vp.getViewPosition() : null;

			if (!wasStick && consoleTextPane != null) {
				// Prevent the view from scrolling when we append: caret won't scroll to show new content.
				DefaultCaret caret = (DefaultCaret) consoleTextPane.getCaret();
				caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
				try {
					consoleStyleHelper.appendLine(message);
					if (vp != null && savedPos != null) vp.setViewPosition(savedPos);
				} finally {
					caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
				}
			} else {
				consoleStyleHelper.appendLine(message);
				if (wasStick) scrollConsoleToBottom();
			}
			updateScrollStickyDebugCheckbox();
		});
	}

	/** Updates {@link #consoleStickToBottom}: true if scroll bar is at bottom, false if user has scrolled up. */
	private void updateConsoleStickToBottomFromScrollBar(AdjustmentEvent e) {
		// When manual mode is on, sticky is controlled only by the checkbox.
		if (manualConsoleScrollStickyMode) return;
		// Never update sticky when we're scrolling from the command (one-time jump without re-sticking).
		if (scrollingFromCommand) return;
		// User-initiated scroll (drag thumb, wheel, page up/down) always updates sticky so we re-stick when they scroll to bottom even during heavy append.
		// Programmatic scroll (our scroll or viewport restore) is ignored during ignoreScrollBarUntil.
		boolean userInitiated = (e != null && isUserScrollAdjustment(e.getAdjustmentType()));
		if (!userInitiated && System.currentTimeMillis() < ignoreScrollBarUntil) return;
		if (consoleScrollPane == null) return;
		javax.swing.JScrollBar bar = consoleScrollPane.getVerticalScrollBar();
		if (bar == null || !bar.isVisible()) return;
		int value = bar.getValue();
		int extent = bar.getModel().getExtent();
		int max = bar.getMaximum();
		// At bottom when visible range reaches the end (small tolerance)
		consoleStickToBottom = (value + extent >= max - 5);
		updateScrollStickyDebugCheckbox();
	}

	/** True if the adjustment type is from direct user input (drag, wheel, page), not programmatic value change. */
	private static boolean isUserScrollAdjustment(int adjustmentType) {
		return adjustmentType == AdjustmentEvent.TRACK
			|| adjustmentType == AdjustmentEvent.UNIT_INCREMENT
			|| adjustmentType == AdjustmentEvent.UNIT_DECREMENT
			|| adjustmentType == AdjustmentEvent.BLOCK_INCREMENT
			|| adjustmentType == AdjustmentEvent.BLOCK_DECREMENT;
	}

	/** Scrolls the console view so the latest output is visible; called when stick-to-bottom is on. */
	private void scrollConsoleToBottom() {
		scrollConsoleToBottomOnly();
		consoleStickToBottom = true; // we just scrolled to bottom, so stick until user scrolls up
		updateScrollStickyDebugCheckbox();
	}

	/** Scrolls the console to the bottom without changing the sticky state (e.g. after user enters a command). */
	@SuppressWarnings("deprecation")
	private void scrollConsoleToBottomOnly() {
		if (consoleTextPane == null) return;
		try {
			int len = consoleTextPane.getDocument().getLength();
			consoleTextPane.setCaretPosition(len);
			Rectangle r = consoleTextPane.modelToView(len);
			if (r != null) {
				consoleTextPane.scrollRectToVisible(r);
			}
		} catch (BadLocationException ignored) {}
	}

	/** Updates the debug checkbox to match {@link #consoleStickToBottom} (call on EDT). */
	void updateScrollStickyDebugCheckbox() {
		if (chkConsoleScrollSticky != null) {
			chkConsoleScrollSticky.setSelected(consoleStickToBottom);
		}
	}

	// --- Callbacks for ConsolePanel ---
	void sendConsoleCommand(String text, boolean asSay) {
		if (server == null || !server.isRunning()) return;
		try {
			server.sendCommand(asSay ? "say " + text : text);
		} catch (ProcessException e) {
			e.printStackTrace();
		}
	}
	void scheduleScrollAfterCommand() {
		Timer t = new Timer(150, e -> {
			scrollingFromCommand = true;
			ignoreScrollBarUntil = System.currentTimeMillis() + 250;
			scrollConsoleToBottomOnly();
			SwingUtilities.invokeLater(() -> scrollingFromCommand = false);
		});
		t.setRepeats(false);
		t.start();
	}
	void setConsoleStickToBottom(boolean stick) {
		consoleStickToBottom = stick;
	}
	void notifyConsoleScrollBarAdjustment(AdjustmentEvent e) {
		updateConsoleStickToBottomFromScrollBar(e);
	}

	/** Send a command to the server via the Core module. Used by Players panel and others. */
	public void sendCommand(String command) throws ProcessException {
		if (module != null) module.sendCommand(command);
	}

	/** 
	 * Returns a monospace font for the console using Java's logical "Monospaced" name.
	 * This lets the JVM pick the best available monospace font on each platform and enables
	 * automatic composite-font fallback for Unicode characters (emoji, symbols, etc.).
	 */
	private static Font getConsoleMonospaceFont(int size) {
		return new Font(Font.MONOSPACED, Font.PLAIN, size);
	}

	/** Default directory for file browser and choosers; works when run from IDE or from JAR. */
	public File getDefaultDirectory() {
		java.net.URL url = ClassLoader.getSystemClassLoader().getResource(".");
		if (url != null) {
			return new File(url.getPath().replaceAll("%20", " "));
		}
		return new File(System.getProperty("user.dir", "."));
	}

	public File getJarFile() { return jarFile; }
	public void setJarFile(File file) { jarFile = file; }

	/** Theme at startup; used by Settings panel to decide if theme can be applied without restart. */
	public Theme getInitialThemeForSession() { return initialThemeForSession; }

	/** Updates the theme label to (may require restart) or (restart required) in red; keeps label at fixed size so layout never shifts. */
	public void updateThemeLabelFor(Theme selectedTheme, JLabel lblTheme) {
		if (lblTheme == null) return;
		boolean sameFamily = initialThemeForSession != null && initialThemeForSession.getFamily() == selectedTheme.getFamily();
		Color fgColor = UIManager.getColor("Label.foreground");
		int minW = lblTheme.getFontMetrics(lblTheme.getFont()).stringWidth("Theme (may require restart)") + 8;
		if (sameFamily) {
			lblTheme.setText("Theme (may require restart)");
			lblTheme.setForeground(fgColor);
			if (themeLabelWidth <= 0) {
				int w = lblTheme.getPreferredSize().width;
				themeLabelWidth = Math.max(w, minW);
				themeLabelHeight = lblTheme.getPreferredSize().height;
			}
		} else {
			String hex = fgColor != null ? String.format("#%02x%02x%02x", fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue()) : "inherit";
			lblTheme.setText("<html><span style='color: " + hex + "'>Theme </span><span style='color: red'>(restart required)</span></html>");
			if (themeLabelWidth <= 0) {
				int w = lblTheme.getPreferredSize().width;
				themeLabelWidth = Math.max(w, minW);
				themeLabelHeight = lblTheme.getPreferredSize().height;
			}
		}
		lblTheme.setPreferredSize(new Dimension(themeLabelWidth, themeLabelHeight));
		lblTheme.setMinimumSize(new Dimension(themeLabelWidth, themeLabelHeight));
	}

	/** Accent color from Settings tab; falls back to settings if UI not built yet. */
	private int getAccentColorRgbFromUI() {
		if (settingsPanel != null) return settingsPanel.getAccentColorRgbFromUI();
		return settings != null ? settings.getAccentColorRgb() : 0x0096E6;
	}

	/** Apply current accent color from UI to FlatLaf and restart icon without restart. */
	/**
	 * Propagates the current look and feel to every window currently open in this
	 * application (main frame, file editors, help/admin windows, etc.).
	 * Uses {@link java.awt.Window#getWindows()} which returns all top-level windows
	 * created by this JVM, so no manual window registry is needed.
	 */
	static void updateAllWindowsUI() {
		for (java.awt.Window w : java.awt.Window.getWindows()) {
			if (w != null) {
				SwingUtilities.updateComponentTreeUI(w);
			}
		}
	}

	void applyAccentColorLive() {
		int rgb = getAccentColorRgbFromUI();
		String hex = String.format("#%06X", 0xFFFFFF & rgb);
		FlatLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", hex));
		UIManager.put("@accentColor", hex);
		try {
			javax.swing.LookAndFeel current = UIManager.getLookAndFeel();
			if (current != null) {
				UIManager.setLookAndFeel(current.getClass().getName());
				updateAllWindowsUI();
			} else {
				FlatLaf.updateUI();
			}
		} catch (Throwable t) {
			FlatLaf.updateUI();
		}
		boolean useIcons = (settingsPanel != null && settingsPanel.getServerButtonsUseTextCheckBox() != null)
			? !settingsPanel.getServerButtonsUseTextCheckBox().isSelected()
			: (settings != null && !settings.isServerButtonsUseText());
		applyServerButtonStyle(useIcons);
	}

	/** Enable/disable accent panel and show "(theme controlled)" when theme does not honor accent. */
	void updateAccentPanelForTheme(Theme theme) {
		if (settingsPanel != null) settingsPanel.updateAccentPanelForTheme(theme);
	}

	// --- Callbacks for SettingsPanel ---
	void onConsoleFontSizeChanged(int size) {
		Font newFont = getConsoleMonospaceFont(size);
		if (consoleTextPane != null) consoleTextPane.setFont(newFont);
		if (consoleStyleHelper != null) consoleStyleHelper.setBaseFont(newFont);
	}
	void onConsoleDarkModeChanged(boolean dark) {
		if (consoleStyleHelper != null) consoleStyleHelper.setDarkMode(dark);
	}
	void onConsoleAdaptiveColorsChanged(boolean adaptive) {
		if (consoleStyleHelper != null) consoleStyleHelper.setAdaptiveColors(adaptive);
	}
	void onConsoleColorsEnabledChanged(boolean enabled) {
		if (consoleStyleHelper != null) consoleStyleHelper.setColorsEnabled(enabled);
	}
	void onConsoleWrapWordBreakOnlyChanged(boolean wordBreakOnly) {
		if (consoleTextPane != null) {
			consoleTextPane.revalidate();
			consoleTextPane.repaint();
		}
	}
	void onOpenFilesInSystemDefaultChanged(boolean openInSystemDefault) {
		if (fileModel != null) fileModel.setOpenInSystemDefault(openInSystemDefault);
	}
	void onManualConsoleScrollStickyChanged(boolean manualMode) {
		manualConsoleScrollStickyMode = manualMode;
		if (chkConsoleScrollSticky != null) {
			chkConsoleScrollSticky.setVisible(manualMode);
			chkConsoleScrollSticky.setEnabled(manualMode);
			if (manualMode) chkConsoleScrollSticky.setSelected(consoleStickToBottom);
		}
	}

	void onDisplayKillButtonChanged(boolean display) {
		if (btnKillServer != null) {
			btnKillServer.setVisible(display);
		}
	}

	class ModuleCore extends Module implements ActionListener {

		public ModuleCore() {
			super("Core");
		}

		@Override
		public String getWebsite() {
			return "the Core";
		}

		@Override
		public void onConsolePrintRaw(String message) {
			super.onConsolePrintRaw(message);
			addToConsole(message);
		}

		@Override
		public void onPlayerJoin(String player, String ip) {
			updatePlayerData(new Player(player, ip));
		}

		@Override
		public void onPlayerLeave(String player, String reason) {
			removePlayerData(player);
		}

		@Override
		public void onServerClosed() {
			super.onServerClosed();

			if (restart) {
				try {
					startServer();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					try {
						setActive(false);
					} catch (IOException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}
				}
				restart = false;
			}else {
				setTitle("ServerGUI (" + versionTag + ")");
				try {
					setActive(false);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		@Override
		public void init() {
			super.init();
			RServer.serverPacketHandlers.add(serverHandler);

		}

		@Override
		public Permission[] getPermissions() {
			Permission[] permissions = {
					CorePermissions.CONSOLE_READ,
					CorePermissions.CONSOLE_SEND,
					CorePermissions.CHAT_READ,
					CorePermissions.CHAT_SEND,
					CorePermissions.ADMIN,
					CorePermissions.ALLOW_BOT,
			};

			return permissions;
		}

		@Override
		public void onBukkitVersionDetected(String version) {
			super.onBukkitVersionDetected(version);

			// On Windows, Spigot/Bukkit's bundled jline2 reads stdin via Win32 native console
			// APIs, decoding bytes with the OEM/ANSI code page regardless of -Dfile.encoding.
			// Writing ISO-8859-1 makes § arrive as the single byte 0xA7 that jline2 expects.
			// On Linux/Mac, jline2 detects the piped stdin is not a TTY and falls back to
			// System.in with the JVM default charset, so -Dfile.encoding=UTF-8 works there.
			// Paper uses jline3 on all platforms and reads UTF-8 correctly; it keeps the default.
			if (server != null && System.getProperty("os.name", "").toLowerCase().contains("win")) {
				server.setStdinCharset(java.nio.charset.StandardCharsets.ISO_8859_1);
			}

			setVersionDisplay();
		}

		@Override
		public void onSpongeVersionDetected(String version) {
			super.onSpongeVersionDetected(version);

			setVersionDisplay();
		}

		public void setVersionDisplay() {
			setTitle("ServerGUI (" + versionTag + ") - [" + getServerType().getName() + "]");
		}

		@Override
		public void onVersionDetected(String version) {
			setVersionDisplay();
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {

		}

	}

	public static boolean isRunning() {
		if (server == null) {
			return false;
		}
		return server.isRunning();
	}
}