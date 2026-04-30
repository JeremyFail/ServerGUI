package me.justicepro.spigotgui.Bridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The ServerGUI bridge process.
 *
 * <p>This process is a lightweight middleman launched by ServerGUI instead of launching the
 * Minecraft server directly. It:
 * <ol>
 *   <li>Starts the Minecraft server as a child process, owning its stdin/stdout.
 *   <li>Listens on a random localhost TCP port for connections from ServerGUI.
 *   <li>Relays all console output to every connected GUI client.
 *   <li>Accepts {@code CMD:<command>} lines from any connected client and forwards them to the
 *       server's stdin.
 *   <li>Writes (and on exit deletes) a lock file so a restarted ServerGUI can reconnect.
 * </ol>
 *
 * <p><b>Protocol</b> (plain text, UTF-8, line-delimited):
 * <pre>
 * GUI → Bridge   AUTH &lt;token&gt;\n          - first line; connection closed if wrong
 * Bridge → GUI   OK\n                      - auth accepted
 * Bridge → GUI   OUT &lt;console line&gt;\n     - a line of server console output
 * Bridge → GUI   STATUS RUNNING\n          - sent after auth to confirm server is up
 * Bridge → GUI   STATUS STOPPED\n          - sent when the server process exits
 * GUI → Bridge   CMD &lt;command text&gt;\n     - send a command to the server stdin
 * GUI → Bridge   DISCONNECT\n             - GUI is closing; bridge stays alive
 * </pre>
 *
 * <p>The bridge process is launched via:
 * <pre>java -jar ServerGUI.jar --bridge &lt;token&gt; &lt;port&gt; &lt;jar&gt; [server args...]</pre>
 * ServerGUI passes the token and a pre-selected port so the GUI process controls these values
 * and can write the lock file itself before the bridge even starts.
 */
public final class ServerBridge {

    /** Maximum number of recent console lines buffered for late-connecting clients. */
    private static final int BACKLOG_SIZE = 500;

    private final String authToken;
    private final int listenPort;
    private final File serverJar;
    private final String[] serverArgs; // everything after the JAR path
    private Charset stdinCharset = StandardCharsets.UTF_8;

    private volatile Process serverProcess;
    private volatile boolean serverRunning = false;

    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    /** Rolling backlog of recent console lines replayed to late-connecting clients. */
    private final ArrayDeque<String> backlog = new ArrayDeque<>();
    private final Object backlogLock = new Object();

    /** Signalled by ClientHandler when RELAUNCH or QUIT is received. */
    private final Object serverLock = new Object();
    private volatile boolean relaunchRequested = false;
    private volatile boolean quitRequested = false;

    private ServerBridge(String authToken, int listenPort, File serverJar, String[] serverArgs) {
        this.authToken = authToken;
        this.listenPort = listenPort;
        this.serverJar = serverJar;
        this.serverArgs = serverArgs;
    }

    /**
     * Entry point when invoked as a bridge process.
     * Args: {@code --bridge <token> <port> <jarPath> [serverArgs...]}
     */
    public static void main(String[] args) {
        // Parse: --bridge <token> <port> <jarPath> [server args...]
        if (args.length < 4 || !"--bridge".equals(args[0])) {
            System.err.println("[ServerGUI Bridge] Invalid arguments. Expected: --bridge <token> <port> <jarPath> [serverArgs...]");
            System.exit(1);
        }
        String token   = args[1];
        int port;
        try {
            port = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("[ServerGUI Bridge] Invalid port: " + args[2]);
            System.exit(1);
            return;
        }
        File jar = new File(args[3]);
        String[] serverArgs = new String[args.length - 4];
        System.arraycopy(args, 4, serverArgs, 0, serverArgs.length);

        ServerBridge bridge = new ServerBridge(token, port, jar, serverArgs);
        bridge.run();
    }

    private void run() {
        // Register shutdown hook - fires on normal termination but NOT on SIGKILL.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            BridgeLockFile.delete(serverJar);
            if (serverProcess != null && serverProcess.isAlive()) {
                sendToServer("stop");
                try { serverProcess.waitFor(); } catch (InterruptedException ignored) { }
            }
        }));

        // Start accepting GUI connections in a daemon thread.
        Thread acceptThread = new Thread(this::acceptLoop, "bridge-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        // Main loop: launch server, then wait for RELAUNCH or QUIT between runs.
        while (!quitRequested) {
            try {
                launchServer(); // blocks until server exits
            } catch (IOException e) {
                System.err.println("[ServerGUI Bridge] Failed to launch server: " + e.getMessage());
                broadcast("STATUS STOPPED");
                break;
            }

            serverRunning = false;
            broadcast("STATUS STOPPED");

            if (quitRequested) break;

            // Wait for a RELAUNCH or QUIT command (or timeout if no clients are connected).
            synchronized (serverLock) {
                while (!relaunchRequested && !quitRequested) {
                    try {
                        serverLock.wait(60_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        quitRequested = true;
                        break;
                    }
                    // Time out if no clients are connected — bridge has been abandoned.
                    if (!relaunchRequested && !quitRequested && clients.isEmpty()) {
                        quitRequested = true;
                        break;
                    }
                }
                relaunchRequested = false;
            }
        }

        // Clean up: delete lock file and give connected clients a moment to read STATUS STOPPED.
        BridgeLockFile.delete(serverJar);
        try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
    }

    // -------------------------------------------------------------------------
    // Server process management
    // -------------------------------------------------------------------------

    private void launchServer() throws IOException {
        // Clear backlog from any previous run so reconnecting clients don't see stale output.
        synchronized (backlogLock) { backlog.clear(); }
        List<String> cmd = buildServerCommand();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("TERM", "xterm-256color");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        serverRunning = true;
        // Notify all connected clients that the server is now running
        // (important for relaunch so the GUI can update its status).
        broadcast("STATUS RUNNING");

        // Drain stdout - relay every line to all connected clients and to the backlog.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String msg = "OUT " + line;
                synchronized (backlogLock) {
                    backlog.addLast(msg);
                    if (backlog.size() > BACKLOG_SIZE) {
                        backlog.removeFirst();
                    }
                }
                broadcast(msg);
                // Also print to our own stdout for debugging.
                System.out.println(line);
            }
        }
        // Wait for process to fully exit.
        try { serverProcess.waitFor(); } catch (InterruptedException ignored) { }
    }

    private List<String> buildServerCommand() {
        List<String> cmd = new ArrayList<>();
        // Resolve java executable from our own runtime so we use the same JVM by default.
        String home = System.getProperty("java.home");
        String javaExe = "java";
        if (home != null) {
            File win = new File(home, "bin/java.exe");
            File nix = new File(home, "bin/java");
            if (win.isFile()) javaExe = win.getAbsolutePath();
            else if (nix.isFile()) javaExe = nix.getAbsolutePath();
        }
        cmd.add(javaExe);
        cmd.add("-Dnet.kyori.ansi.colorLevel=truecolor");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Djline.terminal=jline.UnsupportedTerminal");
        cmd.add("-jar");
        cmd.add(serverJar.getAbsolutePath());
        for (String arg : serverArgs) {
            if (!arg.isEmpty()) cmd.add(arg);
        }
        return cmd;
    }

    private synchronized void sendToServer(String command) {
        if (serverProcess == null || !serverProcess.isAlive()) return;
        try {
            PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(serverProcess.getOutputStream(), stdinCharset), true);
            pw.println(command);
        } catch (Exception ignored) { }
    }

    // -------------------------------------------------------------------------
    // Networking
    // -------------------------------------------------------------------------

    private void acceptLoop() {
        try (ServerSocket ss = new ServerSocket(listenPort)) {
            ss.setSoTimeout(0); // block indefinitely
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket sock = ss.accept();
                    sock.setTcpNoDelay(true);
                    ClientHandler handler = new ClientHandler(sock);
                    clients.add(handler);
                    Thread t = new Thread(handler, "bridge-client");
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        System.err.println("[ServerGUI Bridge] Accept error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[ServerGUI Bridge] Cannot open listen socket on port " + listenPort + ": " + e.getMessage());
        }
    }

    private void broadcast(String message) {
        for (ClientHandler c : clients) {
            c.send(message);
        }
    }

    // -------------------------------------------------------------------------
    // Per-connection handler
    // -------------------------------------------------------------------------

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter writer;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        void send(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter pw = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                this.writer = pw;

                // First line must be AUTH <token>
                String firstLine = reader.readLine();
                if (firstLine == null || !firstLine.equals("AUTH " + authToken)) {
                    pw.println("ERROR Invalid token");
                    return;
                }
                pw.println("OK");

                // Send server status and recent backlog to help the client catch up.
                pw.println(serverRunning ? "STATUS RUNNING" : "STATUS STOPPED");
                synchronized (backlogLock) {
                    for (String line : backlog) {
                        pw.println(line);
                    }
                }

                // Read commands from this client.
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("DISCONNECT")) {
                        break;
                    } else if (line.equals("RELAUNCH")) {
                        if (!serverRunning) {
                            synchronized (serverLock) {
                                relaunchRequested = true;
                                serverLock.notifyAll();
                            }
                        }
                    } else if (line.equals("QUIT")) {
                        synchronized (serverLock) {
                            quitRequested = true;
                            serverLock.notifyAll();
                        }
                        break;
                    } else if (line.startsWith("CMD ")) {
                        String command = line.substring(4);
                        sendToServer(command);
                    }
                }
            } catch (IOException ignored) {
                // Client disconnected
            } finally {
                clients.remove(this);
                try { socket.close(); } catch (IOException ignored) { }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Generates a cryptographically-random 64-character hex token for use as the auth secret.
     */
    public static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Finds a free localhost TCP port by opening and immediately closing a ServerSocket.
     */
    public static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }
}
