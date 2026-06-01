package com.failprooftech.servergui.Bridge;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * Reads and writes a small properties file ({@code servergui-bridge.lock}) placed next to the
 * server JAR. This allows a freshly-launched ServerGUI to reconnect to an existing bridge process
 * rather than starting a duplicate server.
 *
 * <p>Format (Java Properties):
 * <pre>
 * port=&lt;TCP port the bridge is listening on&gt;
 * token=&lt;random 64-char hex authentication token&gt;
 * bridge_pid=&lt;PID of the bridge process&gt;
 * server_pid=&lt;PID of the Minecraft server process&gt;
 * jar=&lt;absolute path to the server JAR&gt;
 * </pre>
 */
public final class BridgeLockFile {

    public static final String LOCK_FILE_NAME = "servergui-bridge.lock";

    public final int port;
    public final String token;
    public final long bridgePid;
    public final long serverPid;
    public final String jarPath;

    public BridgeLockFile(int port, String token, long bridgePid, long serverPid, String jarPath) {
        this.port = port;
        this.token = token;
        this.bridgePid = bridgePid;
        this.serverPid = serverPid;
        this.jarPath = jarPath;
    }

    /** Returns the lock file next to the given server JAR. */
    public static File lockFileFor(File jarFile) {
        return new File(jarFile.getParentFile(), LOCK_FILE_NAME);
    }

    /** Writes this lock file to disk. */
    public void write(File jarFile) throws IOException {
        Properties p = new Properties();
        p.setProperty("port", String.valueOf(port));
        p.setProperty("token", token);
        p.setProperty("bridge_pid", String.valueOf(bridgePid));
        p.setProperty("server_pid", String.valueOf(serverPid));
        p.setProperty("jar", jarPath);
        try (FileWriter fw = new FileWriter(lockFileFor(jarFile))) {
            p.store(fw, "ServerGUI Bridge Lock File - do not edit");
        }
    }

    /**
     * Loads a lock file from disk. Returns null if the file does not exist or is malformed.
     */
    public static BridgeLockFile read(File jarFile) {
        File f = lockFileFor(jarFile);
        if (!f.exists()) return null;
        try (FileReader fr = new FileReader(f)) {
            Properties p = new Properties();
            p.load(fr);
            int port       = Integer.parseInt(p.getProperty("port", "-1"));
            String token   = p.getProperty("token", "");
            long bridgePid = Long.parseLong(p.getProperty("bridge_pid", "-1"));
            long serverPid = Long.parseLong(p.getProperty("server_pid", "-1"));
            String jar     = p.getProperty("jar", "");
            if (port <= 0 || token.isEmpty() || bridgePid < 0) return null;
            return new BridgeLockFile(port, token, bridgePid, serverPid, jar);
        } catch (Exception e) {
            return null;
        }
    }

    /** Deletes the lock file if it exists. */
    public static void delete(File jarFile) {
        File f = lockFileFor(jarFile);
        if (f.exists()) f.delete();
    }
}
