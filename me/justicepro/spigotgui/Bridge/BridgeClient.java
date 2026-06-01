package me.justicepro.spigotgui.Bridge;

import me.justicepro.spigotgui.Module;
import me.justicepro.spigotgui.ModuleManager;
import me.justicepro.spigotgui.Core.SpigotGUI;
import me.justicepro.spigotgui.Utils.ConsoleColor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * GUI-side connection to a {@link ServerBridge} process.
 *
 * <p>Connects to the bridge's TCP socket, authenticates, then runs a read loop that:
 * <ul>
 *   <li>Feeds {@code OUT} lines into the ServerGUI console (firing module hooks).
 *   <li>Handles {@code STATUS STOPPED} by firing {@link Module#onServerClosed()}.
 * </ul>
 *
 * <p>Commands can be sent to the server at any time via {@link #sendCommand(String)}.
 * Call {@link #disconnect()} when the GUI is closing but the server should keep running.
 */
public final class BridgeClient {

    private final String host;
    private final int port;
    private final String token;

    private volatile Socket socket;
    private volatile PrintWriter writer;
    private volatile boolean connected = false;
    private volatile boolean serverRunning = false;
    /** PID of the actual Minecraft server process, as reported by the bridge. -1 = unknown. */
    private volatile long serverPid = -1;

    /** Callback fired when the server exits (STATUS STOPPED received). */
    private Runnable onServerStopped;
    /** Callback fired when the server starts (STATUS RUNNING received after a previous STOPPED). */
    private Runnable onServerStarted;

    public BridgeClient(String host, int port, String token) {
        this.host = host;
        this.port = port;
        this.token = token;
    }

    public void setOnServerStopped(Runnable r) {
        this.onServerStopped = r;
    }

    public void setOnServerStarted(Runnable r) {
        this.onServerStarted = r;
    }

    /** @return true if currently connected and authenticated to the bridge. */
    public boolean isConnected() {
        return connected;
    }

    /** @return true if the bridge has reported that the server is running. */
    public boolean isServerRunning() {
        return serverRunning;
    }

    /** @return the PID of the Minecraft server process as reported by the bridge, or -1 if not yet known. */
    public long getServerPid() {
        return serverPid;
    }

    /**
     * Connects to the bridge, authenticates, and starts the read loop on a daemon thread.
     *
     * @throws IOException if the connection or authentication fails
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        // Authenticate
        writer.println("AUTH " + token);
        String response = reader.readLine();
        if (response == null || !response.equals("OK")) {
            socket.close();
            throw new IOException("Bridge authentication failed: " + response);
        }
        connected = true;

        // Start read loop
        Thread t = new Thread(() -> readLoop(reader), "bridge-reader");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Continuously reads lines from the bridge and handles them until the socket is closed or an error occurs.
     * Handles STATUS lines to track server running state and fire onServerStopped, OUT lines to print to console,
     * and ERROR lines to print as bridge errors. Ignores malformed lines.
     * 
     * @param reader the BufferedReader to read lines from the bridge
     */
    private void readLoop(BufferedReader reader) {
        // Tracks whether we have ever received STATUS STOPPED, so we know a
        // subsequent STATUS RUNNING is a relaunch rather than the initial state.
        boolean everStopped = false;
        boolean sawRunning = false;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("STATUS ")) {
                    String status = line.substring(7);
                    if ("RUNNING".equals(status)) {
                        serverRunning = true;
                        sawRunning = true;
                        // Only fire the started callback on a relaunch (not the initial STATUS RUNNING).
                        if (everStopped && onServerStarted != null) {
                            onServerStarted.run();
                        }
                    } else if ("STOPPED".equals(status)) {
                        serverRunning = false;
                        serverPid = -1;
                        everStopped = true;
                        if (onServerStopped != null) {
                            onServerStopped.run();
                        }
                        // Stay connected - the bridge may relaunch the server.
                    }
                } else if (line.startsWith("PID ")) {
                    try {
                        serverPid = Long.parseLong(line.substring(4).trim());
                    } catch (NumberFormatException ignored) { }
                } else if (line.startsWith("OUT ")) {
                    String consoleLine = line.substring(4);
                    // Fire module hooks just like the normal server thread does.
                    for (Module module : ModuleManager.modules) {
                        try {
                            module.onConsolePrintRaw(consoleLine);
                        } catch (Exception e) {
                            SpigotGUI.logThrowable(
                                    "Exception in module " + module.getClass().getSimpleName()
                                    + " while processing bridge console output", e);
                        }
                    }
                } else if (line.startsWith("ERROR ")) {
                    SpigotGUI.addToConsole(SpigotGUI.getPrefix() + ConsoleColor.RED
                            + "[Bridge] " + line.substring(6) + ConsoleColor.RESET);
                }
            }
        } catch (IOException ignored) {
            // Socket closed
        } finally {
            connected = false;
            // If the bridge connection drops unexpectedly, treat it as a stop so the GUI
            // doesn't remain stuck in a "running" state.
            boolean wasRunning = serverRunning;
            serverRunning = false;
            serverPid = -1;
            // If STATUS STOPPED already ran the callback, do not fire again on disconnect.
            if (!everStopped && (wasRunning || sawRunning)) {
                if (onServerStopped != null) {
                    onServerStopped.run();
                } else {
                    SpigotGUI.addToConsole(SpigotGUI.getPrefix() + ConsoleColor.RED
                            + "[Bridge] Connection lost - assuming server stopped." + ConsoleColor.RESET);
                }
            }
            try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        }
    }

    /**
     * Sends a command to the Minecraft server's stdin via the bridge.
     * Silently ignored if not connected.
     */
    public void sendCommand(String command) {
        PrintWriter w = writer;
        if (w != null && connected) {
            w.println("CMD " + command);
        }
    }

    /**
     * Asks the bridge to relaunch the server after it has stopped.
     * The bridge will start a new server process; STATUS RUNNING will be received when ready.
     */
    public void sendRelaunch() {
        PrintWriter w = writer;
        if (w != null && connected) {
            w.println("RELAUNCH");
        }
    }

    /**
     * Tells the bridge which charset to use when writing to the server's stdin pipe.
     * Called when the GUI detects the actual server type from console output at runtime,
     * overriding the bridge's JAR-name heuristic.
     *
     * @param charset the charset to use (e.g. {@code StandardCharsets.ISO_8859_1} for Spigot/CraftBukkit)
     */
    public void sendStdinCharset(java.nio.charset.Charset charset) {
        PrintWriter w = writer;
        if (w != null && connected) {
            w.println("STDIN_CHARSET " + charset.name());
        }
    }

    /**
     * Asks the bridge to exit cleanly. The bridge will delete the lock file and terminate.
     * This client's read loop will end when the socket is closed by the bridge.
     */
    public void sendQuit() {
        PrintWriter w = writer;
        if (w != null && connected) {
            w.println("QUIT");
        }
        connected = false;
    }

    /**
     * Tells the bridge this client is disconnecting but the server should keep running.
     * The bridge stays alive; a future ServerGUI can reconnect.
     */
    public void disconnect() {
        PrintWriter w = writer;
        if (w != null) {
            w.println("DISCONNECT");
        }
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
    }
}
