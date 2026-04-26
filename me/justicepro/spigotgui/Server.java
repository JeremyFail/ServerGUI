package me.justicepro.spigotgui;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import me.justicepro.spigotgui.Core.SpigotGUI;

public class Server {
	
	private File jar;
	private String switches;
	private String arguments;
	
	private Process process;
	
	public Server(File jar, String arguments, String switches) {
		this.jar = jar;
		this.arguments = arguments;
		this.switches = switches;
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
			String ansiFlag = "-Dnet.kyori.ansi.colorLevel=indexed16";
			JvmSwitchNormalization norm = normalizeJvmSwitchesWithNotes(switches);
			SpigotGUI.appendJvmNormalizationWarnings(norm.warnings);

			List<String> cmd = new ArrayList<>();
			cmd.add(resolveJavaExecutable());
			appendSplitTokens(cmd, norm.normalized);
			cmd.add(ansiFlag);
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
							module.onConsolePrintRaw(line);
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

	private static String resolveJavaExecutable() {
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
	 *   <li>{@code -Xnoagent} — removed (invalid / ignored on modern VMs; was for obsolete JVMDI)
	 *   <li>{@code -Xdebug} — removed (deprecated JDK 22+; JDWP via {@code -agentlib:jdwp} does not need it)
	 *   <li>{@code -Xrunjdwp:...} — rewritten to {@code -agentlib:jdwp=...}
	 *   <li>{@code -Djava.compiler=...} — on JDK 21+ removed (obsolete). We do <em>not</em> inject {@code -Xint};
	 *       that would disable the JIT and cripple server performance; add {@code -Xint} manually only if you want interpreted-only mode.
	 * </ul>
	 * Non-empty {@link JvmSwitchNormalization#warnings} should be shown in the console (see {@link SpigotGUI#appendJvmNormalizationWarnings}).
	 */
	static JvmSwitchNormalization normalizeJvmSwitchesWithNotes(String switches) {
		List<String> warnings = new ArrayList<>();
		if (switches == null || switches.trim().isEmpty()) {
			return new JvmSwitchNormalization("", warnings);
		}
		int feature = javaFeatureVersion();
		String[] parts = switches.trim().split("\\s+");
		List<String> out = new ArrayList<>();
		for (String t : parts) {
			if (t.isEmpty()) {
				continue;
			}
			String lower = t.toLowerCase();
			if ("-xnoagent".equals(lower)) {
				warnings.add("Removed -Xnoagent (not supported on current JDKs).");
				continue;
			}
			if ("-xdebug".equals(lower)) {
				warnings.add("Removed -Xdebug (unnecessary with JDWP; removed on newer JDKs).");
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

	private static int javaFeatureVersion() {
		try {
			String v = System.getProperty("java.specification.version", "8");
			if (v.startsWith("1.")) {
				if ("1.8".equals(v)) {
					return 8;
				}
				return Integer.parseInt(v.substring(2));
			}
			int dot = v.indexOf('.');
			return Integer.parseInt(dot < 0 ? v : v.substring(0, dot));
		} catch (Exception e) {
			return 8;
		}
	}

	public void sendCommand(String command) throws ProcessException {
		
		PrintWriter output = new PrintWriter(process.getOutputStream(), true);

		if (process.isAlive()) {
			output.println(command);
		}else {
			throw new ProcessException();
		}
		
	}

	/**
	 * Returns the server JVM process. Used by the Resources tab for PID and OSHI metrics.
	 */
	public Process getProcess() {
		return process;
	}
	
}