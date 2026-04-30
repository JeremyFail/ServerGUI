package me.justicepro.spigotgui;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import me.justicepro.spigotgui.Bridge.BridgeClient;
import me.justicepro.spigotgui.Bridge.BridgeLockFile;
import me.justicepro.spigotgui.Bridge.ServerBridge;
import me.justicepro.spigotgui.Core.SpigotGUI;
import me.justicepro.spigotgui.Utils.ConsoleColor;
import me.justicepro.spigotgui.Utils.ProcessUtils;

public class Server {
	
	private File jar;
	private String switches;
	private String arguments;
	/** Custom java/javaw executable path. Null means use the same JVM as the app. */
	private String customJvmPath;
	/**
	 * Charset used to encode commands written to the server's stdin.
	 * Defaults to UTF-8; switch to ISO-8859-1 for Spigot/Bukkit, whose bundled jline2
	 * reads stdin bytes as the Windows OEM/ANSI code page (where § = 0xA7, same as Latin-1)
	 * regardless of -Dfile.encoding.
	 */
	private volatile Charset stdinCharset = StandardCharsets.UTF_8;

	/** PID of an adopted (pre-existing) server process; -1 means not adopted. */
	private long adoptedPid = -1;
	/** OSHI OS interface for checking adopted process liveness; null when not adopted. */
	private oshi.software.os.OperatingSystem adoptedOs;

	/** Active bridge connection; non-null when this server is managed via the bridge process. */
	private BridgeClient bridgeClient;
	/** Lock-file data for the active bridge session; non-null when bridge-connected. */
	private BridgeLockFile bridgeLock;

	private Process process;
	
	public Server(File jar, String arguments, String switches) {
		this(jar, arguments, switches, null);
	}

	public Server(File jar, String arguments, String switches, String customJvmPath) {
		this.jar = jar;
		this.arguments = arguments;
		this.switches = switches;
		this.customJvmPath = (customJvmPath != null && !customJvmPath.isEmpty()) ? customJvmPath : null;
	}
	
	public Thread start(String arguments, String switches) throws IOException, ProcessException {

		if (process!=null) {
			if (process.isAlive()) {
				throw new ProcessException();
			}
		}else {
			System.out.println("Started Server");

			// Launch the JVM directly instead of piping a line into cmd/sh. Interactive shells with redirected
			// stdin are unreliable; bare "java" also depends on PATH rather than the runtime that
			// started this app. Merge stderr so launcher errors (e.g. bad flags) appear in the console.
			String ansiFlag = "-Dnet.kyori.ansi.colorLevel=truecolor";
			String encodingFlag = "-Dfile.encoding=UTF-8";
			String stdoutEncodingFlag = "-Dstdout.encoding=UTF-8"; // Java 17+: overrides System.out charset; log4j2 ConsoleAppender writes through System.out
			// Force jline2 (Spigot) to use its dumb/pipe mode so it honours file.encoding
			// rather than using native Win32 console APIs. jline3 (Paper) ignores this property.
			String jlineFlag = "-Djline.terminal=jline.UnsupportedTerminal";
			String javaExe = resolveJavaExecutable();
			int targetFeature = (customJvmPath != null) ? probeJvmFeatureVersion(javaExe) : javaFeatureVersion();
			JvmSwitchNormalization norm = normalizeJvmSwitchesWithNotes(switches, targetFeature);
			SpigotGUI.appendJvmNormalizationWarnings(norm.warnings);

			List<String> cmd = new ArrayList<>();
			cmd.add(javaExe);
			appendSplitTokens(cmd, norm.normalized);
			cmd.add(ansiFlag);
			cmd.add(encodingFlag);
			cmd.add(stdoutEncodingFlag);
			cmd.add(jlineFlag);
			cmd.add("-jar");
			cmd.add(jar.getAbsolutePath());
			appendSplitTokens(cmd, arguments);

			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.environment().put("TERM", "xterm-256color");
			pb.redirectErrorStream(true);
			process = pb.start();

			Thread thread = createThread();
			
			thread.start();
			
			return thread;
			
		}
		
		return null;
	}

	public Thread start() throws IOException, ProcessException {
		return start(arguments, switches);
	}
	
	public boolean isRunning() {
		if (bridgeClient != null) {
			return bridgeClient.isServerRunning();
		}
		if (adoptedPid >= 0) {
			if (adoptedOs == null) return false;
			try {
				for (oshi.software.os.OSProcess p : adoptedOs.getProcesses()) {
					if (p != null && p.getProcessID() == (int) adoptedPid
							&& p.getState() != oshi.software.os.OSProcess.State.INVALID) {
						return true;
					}
				}
			} catch (Exception ignored) { }
			return false;
		}
		if (process == null) {
			return false;
		}
		return process.isAlive();
	}
	
	private Thread createThread() {
		
		if (process == null) {
			return null;
		}
		
		return new Thread(new Runnable() {
			public void run() {
				// Use UTF-8 so § and ANSI escape sequences are preserved
				Scanner scanner = new Scanner(process.getInputStream(), StandardCharsets.UTF_8.name());
				while (isRunning()) {

					while (scanner.hasNextLine()) {
						String line = scanner.nextLine();
						
						for (Module module : ModuleManager.modules) {
							try {
								module.onConsolePrintRaw(line);
							} catch (Exception e) {
								SpigotGUI.logThrowable("Exception in module " + module.getClass().getSimpleName() + " while processing console output", e);
							}
						}
						
					}

				}
				scanner.close();
				
				for (Module module : ModuleManager.modules) {
					module.onServerClosed();
				}
				
			}
		});
	}

	public static String makeMemory(String ramMin, String ramMax) {

		String args = "-Xms" + ramMin + " -Xmx" + ramMax;

		return args;
	}

	private String resolveJavaExecutable() {
		if (customJvmPath != null && !customJvmPath.isEmpty()) {
			return customJvmPath;
		}
		String home = System.getProperty("java.home");
		if (home != null) {
			File win = new File(home, "bin" + File.separator + "java.exe");
			if (win.isFile()) {
				return win.getAbsolutePath();
			}
			File nix = new File(home, "bin" + File.separator + "java");
			if (nix.isFile()) {
				return nix.getAbsolutePath();
			}
		}
		return "java";
	}

	private static void appendSplitTokens(List<String> cmd, String s) {
		if (s == null) {
			return;
		}
		s = s.trim();
		if (s.isEmpty()) {
			return;
		}
		for (String part : s.split("\\s+")) {
			if (!part.isEmpty()) {
				cmd.add(part);
			}
		}
	}

	static final class JvmSwitchNormalization {
		final String normalized;
		final List<String> warnings;

		JvmSwitchNormalization(String normalized, List<String> warnings) {
			this.normalized = normalized;
			this.warnings = warnings;
		}
	}

	/**
	 * Maps legacy JVM flags from older tutorials and IDEs to forms that work on current JDKs.
	 * <ul>
	 *   <li>{@code -Xnoagent} - removed (invalid / ignored on modern VMs; was for obsolete JVMDI)
	 *   <li>{@code -Xdebug} - removed only on Java 22+ where it is invalid; kept on 8–21 where it is accepted
	 *   <li>{@code -Xrunjdwp:...} - rewritten to {@code -agentlib:jdwp=...} (works on all versions ≥ 8)
	 *   <li>{@code -Djava.compiler=...} - on JDK 21+ removed (obsolete). We do <em>not</em> inject {@code -Xint};
	 *       that would disable the JIT and cripple server performance; add {@code -Xint} manually only if you want interpreted-only mode.
	 * </ul>
	 * Non-empty {@link JvmSwitchNormalization#warnings} should be shown in the console (see {@link SpigotGUI#appendJvmNormalizationWarnings}).
	 * Delegates to {@link #normalizeJvmSwitchesWithNotes(String, int)} using the host JVM version.
	 */
	static JvmSwitchNormalization normalizeJvmSwitchesWithNotes(String switches) {
		return normalizeJvmSwitchesWithNotes(switches, javaFeatureVersion());
	}

	/**
	 * Version-aware form: {@code feature} is the major version of the JVM that will actually run the server
	 * (may differ from the host JVM when a custom JVM path is configured).
	 */
	static JvmSwitchNormalization normalizeJvmSwitchesWithNotes(String switches, int feature) {
		List<String> warnings = new ArrayList<>();
		if (switches == null || switches.trim().isEmpty()) {
			return new JvmSwitchNormalization("", warnings);
		}
		String[] parts = switches.trim().split("\\s+");
		List<String> out = new ArrayList<>();
		for (String t : parts) {
			if (t.isEmpty()) {
				continue;
			}
			String lower = t.toLowerCase();
			if (lower.startsWith("-dfile.encoding=")) {
				warnings.add("Removed " + t + " (-Dfile.encoding is managed by ServerGUI and cannot be overridden here).");
				continue;
			}
			if (lower.startsWith("-dstdout.encoding=")) {
				warnings.add("Removed " + t + " (-Dstdout.encoding is managed by ServerGUI and cannot be overridden here).");
				continue;
			}
			if (lower.startsWith("-dnet.kyori.ansi.colorlevel=")) {
				warnings.add("Removed " + t + " (-Dnet.kyori.ansi.colorLevel is managed by ServerGUI and cannot be overridden here).");
				continue;
			}
			if (lower.startsWith("-djline.terminal=")) {
				warnings.add("Removed " + t + " (-Djline.terminal is managed by ServerGUI and cannot be overridden here).");
				continue;
			}
			if ("-xnoagent".equals(lower)) {
				warnings.add("Removed -Xnoagent (not supported on current JDKs).");
				continue;
			}
			if ("-xdebug".equals(lower)) {
				if (feature >= 22) {
					// Java 22 removed -Xdebug entirely; it causes a launcher error.
					warnings.add("Removed -Xdebug (invalid on Java " + feature + "; JDWP via -agentlib:jdwp does not need it).");
					continue;
				}
				// Java 8–21: -Xdebug is accepted (deprecated post-9 but harmless); leave it.
				out.add(t);
				continue;
			}
			if (lower.startsWith("-xrunjdwp:")) {
				String agent = "-agentlib:jdwp=" + t.substring("-xrunjdwp:".length());
				out.add(agent);
				warnings.add("Replaced -Xrunjdwp:... with " + agent + " (current JDWP form).");
				continue;
			}
			if (feature >= 21 && lower.startsWith("-djava.compiler=")) {
				int eq = t.indexOf('=');
				if (eq >= 0 && eq < t.length() - 1) {
					String val = t.substring(eq + 1).trim();
					if ("none".equalsIgnoreCase(val) || "disabled".equalsIgnoreCase(val) || "disable".equalsIgnoreCase(val)) {
						warnings.add("Removed " + t + " (obsolete on JDK 21+). The JVM ignores this property; the server uses normal JIT. "
								+ "Do not add -Xint unless you intentionally want interpreted-only mode (very slow).");
					} else {
						warnings.add("Removed " + t + " (java.compiler is obsolete on JDK 21+).");
					}
				}
				continue;
			}
			out.add(t);
		}
		String normalized = out.isEmpty() ? "" : String.join(" ", out);
		return new JvmSwitchNormalization(normalized, warnings.isEmpty() ? Collections.emptyList() : warnings);
	}

	/**
	 * Probes the major feature version of the given java executable by running {@code <exe> -version}
	 * and parsing its output. Falls back to the host JVM version if the probe fails or times out.
	 * The minimum returned value is 8.
	 */
	private static int probeJvmFeatureVersion(String javaExe) {
		try {
			Process p = new ProcessBuilder(javaExe, "-version")
					.redirectErrorStream(true)
					.start();
			String versionLine = null;
			try (Scanner sc = new Scanner(p.getInputStream(), StandardCharsets.UTF_8.name())) {
				while (sc.hasNextLine()) {
					String line = sc.nextLine().trim();
					if (versionLine == null && line.contains("version \"")) {
						versionLine = line;
					}
				}
			}
			p.waitFor(5, TimeUnit.SECONDS);
			if (versionLine != null) {
				int q1 = versionLine.indexOf('"');
				int q2 = versionLine.lastIndexOf('"');
				if (q1 >= 0 && q2 > q1) {
					return parseFeatureVersion(versionLine.substring(q1 + 1, q2));
				}
			}
		} catch (Exception e) {
			// Fall through to host version fallback
		}
		return javaFeatureVersion();
	}

	private static int javaFeatureVersion() {
		return parseFeatureVersion(System.getProperty("java.specification.version", "8"));
	}

	private static int parseFeatureVersion(String v) {
		if (v == null) return 8;
		v = v.trim();
		try {
			if (v.startsWith("1.")) {
				// Legacy format: "1.8.x" → 8, "1.7.x" → 7, etc.
				if (v.startsWith("1.8")) return 8;
				String rest = v.substring(2);
				int dot = rest.indexOf('.');
				return Integer.parseInt(dot < 0 ? rest : rest.substring(0, dot));
			}
			// Modern format: "21.0.3", "17", etc.
			int dot = v.indexOf('.');
			return Integer.parseInt(dot < 0 ? v : v.substring(0, dot));
		} catch (Exception e) {
			return 8;
		}
	}

	public void setStdinCharset(Charset charset) {
		this.stdinCharset = charset != null ? charset : StandardCharsets.UTF_8;
	}

	public Charset getStdinCharset() {
		return stdinCharset;
	}

	public void sendCommand(String command) throws ProcessException {
		if (bridgeClient != null) {
			// Strip variation selectors before forwarding.
			command = command.replaceAll("[\\uFE0E\\uFE0F]", "");
			bridgeClient.sendCommand(command);
			return;
		}
		if (adoptedPid >= 0) {
			SpigotGUI.addToConsole(SpigotGUI.getPrefix() + ConsoleColor.YELLOW
					+ "Cannot send '" + command + "' - this server was not started by ServerGUI."
					+ ConsoleColor.RESET);
			return;
		}
		// Strip variation selectors (U+FE0E/U+FE0F) - invisible codepoints that modify emoji
		// presentation; meaningless in game and cause encoding issues on Latin-1 servers.
		command = command.replaceAll("[\\uFE0E\\uFE0F]", "");
		PrintWriter output = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), stdinCharset), true);

		if (process.isAlive()) {
			output.println(command);
		} else {
			throw new ProcessException();
		}
	}

	/**
	 * Forcefully terminates the server process immediately (like SIGKILL).
	 * Only use this if the server is hung and won't respond to the "stop" command.
	 */
	public void kill() {
		if (bridgeClient != null && bridgeLock != null) {
			// Kill the Minecraft server process then the bridge process.
			if (bridgeLock.serverPid > 0) killProcessByPid(bridgeLock.serverPid);
			if (bridgeLock.bridgePid > 0) killProcessByPid(bridgeLock.bridgePid);
			bridgeClient.disconnect();
			return;
		}
		if (adoptedPid >= 0) {
			killProcessByPid(adoptedPid);
			return;
		}
		if (process != null && process.isAlive()) {
			process.destroyForcibly();
		}
	}

	/** Kills an arbitrary process by PID using the OS task-kill command. */
	private static void killProcessByPid(long pid) {
		try {
			String osName = System.getProperty("os.name", "").toLowerCase();
			if (osName.contains("win")) {
				new ProcessBuilder("taskkill", "/f", "/pid", String.valueOf(pid)).start();
			} else {
				new ProcessBuilder("kill", "-9", String.valueOf(pid)).start();
			}
		} catch (IOException ignored) { }
	}

	/**
	 * Returns the server JVM process. Used by the Resources tab for PID and OSHI metrics.
	 * Returns null for adopted (pre-existing) server processes.
	 */
	public Process getProcess() {
		return process;
	}

	/** Returns true if this server was adopted from a pre-existing process and was not started by ServerGUI. */
	public boolean isAdopted() {
		return adoptedPid >= 0;
	}

	/** Returns true if this server is connected via the bridge process. */
	public boolean isBridgeConnected() {
		return bridgeClient != null;
	}

	/**
	 * Returns the PID of the actual Minecraft server JVM.
	 * In bridge mode returns the PID reported by the bridge; in adopted mode returns the adopted PID;
	 * in direct mode returns the PID of the launched Process.
	 */
	public long getServerPid() {
		if (bridgeClient != null) return bridgeClient.getServerPid();
		if (adoptedPid >= 0) return adoptedPid;
		return ProcessUtils.getPid(process);
	}

	/**
	 * Tells the bridge client the GUI is going away (e.g. on app close) but the server should
	 * keep running. The bridge process continues; a future ServerGUI session can reconnect.
	 */
	public void disconnectBridge() {
		if (bridgeClient != null) {
			bridgeClient.disconnect();
		}
	}

	/**
	 * Asks the bridge to relaunch the server after it has stopped.
	 * The GUI will receive STATUS RUNNING (via onServerStarted) when the server is up again.
	 */
	public void relaunchViaBridge() {
		if (bridgeClient != null) {
			bridgeClient.sendRelaunch();
		}
	}

	/**
	 * Tells the bridge to exit cleanly. Clears the bridge reference so this Server reverts to
	 * a stopped/unmanaged state.
	 */
	public void quitBridge() {
		if (bridgeClient != null) {
			bridgeClient.sendQuit();
			bridgeClient = null;
			bridgeLock = null;
		}
	}

	/**
	 * Connects to an already-running bridge process described by {@code lock}.
	 * Fires {@link Module#onServerClosed()} via the client's stop callback.
	 *
	 * @return the connected Server, or null if connection/auth fails
	 */
	public static Server connectViaBridge(BridgeLockFile lock, File jar, String arguments, String switches, String customJvmPath) {
		Server s = new Server(jar, arguments != null ? arguments : "", switches != null ? switches : "", customJvmPath);
		BridgeClient client = new BridgeClient("127.0.0.1", lock.port, lock.token);
		client.setOnServerStopped(() -> {
			for (Module module : ModuleManager.modules) {
				module.onServerClosed();
			}
		});
		client.setOnServerStarted(() -> {
			EventQueue.invokeLater(() -> {
				try {
					if (SpigotGUI.instance != null) SpigotGUI.instance.setActive(true);
				} catch (IOException e) { e.printStackTrace(); }
			});
		});
		try {
			client.connect();
		} catch (IOException e) {
			return null; // bridge gone
		}
		s.bridgeClient = client;
		s.bridgeLock = lock;
		return s;
	}

	/**
	 * Launches a new bridge process for the given JAR, writes the lock file, connects to it,
	 * and returns a fully wired Server. This replaces the normal {@link #start()} path.
	 *
	 * @param javaExe      java executable to use for the bridge itself
	 * @param selfJar      the ServerGUI JAR (to launch the bridge via {@code --bridge})
	 * @param serverJar    the Minecraft server JAR
	 * @param arguments    arguments passed after the server JAR (e.g. "nogui")
	 * @param switches     JVM switches for the Minecraft server (forwarded as-is to the bridge)
	 * @param customJvmPath custom JVM path or null
	 * @throws IOException if the bridge cannot be started or connected to
	 */
	public static Server startViaBridge(
			String javaExe,
			File selfJar,
			File serverJar,
			String arguments,
			String switches,
			String customJvmPath) throws IOException {

		// Pick a free port and generate an auth token before launching so we can write
		// the lock file immediately (the bridge reads its params from CLI args).
		int port = ServerBridge.findFreePort();
		String token = ServerBridge.generateToken();

		// Build bridge command:
		// <java> [server JVM switches] -jar <selfJar> --bridge <token> <port> <serverJar> [arguments]
		List<String> cmd = new ArrayList<>();
		cmd.add(javaExe);
		// Pass the server JVM switches to the bridge so it can forward them to the server.
		// We re-use the existing normalization path by appending them here.
		if (switches != null && !switches.trim().isEmpty()) {
			for (String sw : switches.trim().split("\\s+")) {
				if (!sw.isEmpty()) cmd.add(sw);
			}
		}
		cmd.add("-jar");
		cmd.add(selfJar.getAbsolutePath());
		cmd.add("--bridge");
		cmd.add(token);
		cmd.add(String.valueOf(port));
		cmd.add(serverJar.getAbsolutePath());
		if (arguments != null && !arguments.trim().isEmpty()) {
			for (String arg : arguments.trim().split("\\s+")) {
				if (!arg.isEmpty()) cmd.add(arg);
			}
		}

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.inheritIO(); // bridge's stdout goes to our terminal for debugging
		Process bridgeProcess = pb.start();

		// Get the bridge PID for the lock file (best-effort; -1 on Java 8 Windows if unavailable).
		long bridgePid = ProcessUtils.getPid(bridgeProcess);

		// Give the bridge a moment to start listening before we connect.
		try { Thread.sleep(800); } catch (InterruptedException ignored) { }

		// Write the lock file so future ServerGUI instances can find this bridge.
		BridgeLockFile lock = new BridgeLockFile(port, token, bridgePid, -1, serverJar.getAbsolutePath());
		lock.write(serverJar);

		// Connect.
		Server s = new Server(serverJar, arguments != null ? arguments : "", switches != null ? switches : "", customJvmPath);
		BridgeClient client = new BridgeClient("127.0.0.1", port, token);
		client.setOnServerStopped(() -> {
			BridgeLockFile.delete(serverJar);
			for (Module module : ModuleManager.modules) {
				module.onServerClosed();
			}
		});
		client.setOnServerStarted(() -> {
			EventQueue.invokeLater(() -> {
				try {
					if (SpigotGUI.instance != null) SpigotGUI.instance.setActive(true);
				} catch (IOException e) { e.printStackTrace(); }
			});
		});

		// Retry connection briefly in case the bridge needs a bit more time.
		IOException lastError = null;
		for (int attempt = 0; attempt < 5; attempt++) {
			try {
				client.connect();
				lastError = null;
				break;
			} catch (IOException e) {
				lastError = e;
				try { Thread.sleep(500); } catch (InterruptedException ignored) { }
			}
		}
		if (lastError != null) {
			BridgeLockFile.delete(serverJar);
			bridgeProcess.destroy();
			throw new IOException("Could not connect to bridge process: " + lastError.getMessage(), lastError);
		}

		s.bridgeClient = client;
		s.bridgeLock   = lock;
		return s;
	}

	/**
	 * Creates a Server wrapper around an already-running server process that was not started by this
	 * ServerGUI session. The adopted server can be monitored and killed but cannot receive stdin commands.
	 *
	 * @param pid           PID of the running process
	 * @param jar           the server JAR file
	 * @param arguments     server arguments (from settings)
	 * @param switches      JVM switches (from settings)
	 * @param customJvmPath custom JVM path or null
	 * @return a Server in adopted mode
	 */
	public static Server adoptOrphanedProcess(long pid, File jar, String arguments, String switches, String customJvmPath) {
		Server s = new Server(jar, arguments != null ? arguments : "", switches != null ? switches : "", customJvmPath);
		s.adoptedPid = pid;
		try {
			s.adoptedOs = new oshi.SystemInfo().getOperatingSystem();
		} catch (Exception ignored) { }
		return s;
	}

	/**
	 * Returns true if a process with the given PID is currently alive according to OSHI.
	 * Returns false if OSHI is unavailable or the process is not found.
	 */
	public static boolean isProcessAlive(long pid) {
		if (pid < 0) return false;
		try {
			oshi.SystemInfo si = new oshi.SystemInfo();
			oshi.software.os.OperatingSystem os = si.getOperatingSystem();
			for (oshi.software.os.OSProcess p : os.getProcesses()) {
				if (p != null && p.getProcessID() == (int) pid
						&& p.getState() != oshi.software.os.OSProcess.State.INVALID) {
					return true;
				}
			}
		} catch (Exception ignored) { }
		return false;
	}

	/**
	 * Searches all running processes for a Java process whose command line contains the given JAR's
	 * absolute path. Returns the PID if found, or -1 if not running. Excludes the current JVM process.
	 */
	public static long findOrphanedPid(File jarFile) {
		if (jarFile == null) return -1;
		String jarPath = jarFile.getAbsolutePath();
		try {
			oshi.SystemInfo si = new oshi.SystemInfo();
			oshi.software.os.OperatingSystem os = si.getOperatingSystem();
			int currentPid = os.getProcessId();
			for (oshi.software.os.OSProcess p : os.getProcesses()) {
				if (p == null) continue;
				if (p.getState() == oshi.software.os.OSProcess.State.INVALID) continue;
				if (p.getProcessID() == currentPid) continue;
				String name = p.getName();
				if (name == null) continue;
				String lower = name.toLowerCase();
				if (!lower.contains("java") && !lower.endsWith("java.exe")) continue;
				String cmd = p.getCommandLine();
				if (cmd != null && cmd.contains(jarPath)) return p.getProcessID();
			}
		} catch (Exception ignored) { }
		return -1;
	}

	/**
	 * Starts a daemon thread that polls the adopted process every 2 seconds until it exits,
	 * then fires {@link Module#onServerClosed()} on all registered modules.
	 * Only valid when {@link #isAdopted()} is true.
	 */
	public Thread startAdoptedMonitorThread() {
		if (!isAdopted()) return null;
		Thread t = new Thread(() -> {
			while (isRunning()) {
				try { Thread.sleep(2000); } catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			for (Module module : ModuleManager.modules) {
				module.onServerClosed();
			}
		});
		t.setDaemon(true);
		t.start();
		return t;
	}

}