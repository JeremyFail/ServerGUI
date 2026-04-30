package me.justicepro.spigotgui.Core;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.justicepro.spigotgui.Server;
import me.justicepro.spigotgui.ServerSettings;

/**
 * Tab panel that shows server process resource usage: memory graph, memory stats,
 * configured heap, and CPU usage. Similar to the Paper server JAR's built-in stats view.
 */
public class ResourcesPanel extends JPanel {

    private static final int MAX_SAMPLES = 200;  // ~5 minutes at 1.5s interval
    private static final int POLL_INTERVAL_MS = 1500;
    private static final long BYTES_PER_MB = 1024 * 1024;

    private final int configuredHeapMaxMb;
    private final int configuredHeapMinMb;
    /** Server JAR path for matching process when shell PID is not available (e.g. Windows Java 8). */
    private final String serverJarPath;

    private final List<Sample> samples = new ArrayList<>();
    private OSProcess previousProcessSnapshot;
    private Timer pollTimer;

    private final JLabel lblCpu;
    private final JLabel lblMemoryUse;
    private final JLabel lblHeap;
    private final JLabel lblPlayers;
    private final JLabel lblPid;
    private final GraphPanel graphPanel;

    private SystemInfo systemInfo;
    private OperatingSystem os;

    public ResourcesPanel(ServerSettings serverSettings) {
        Object maxRam = serverSettings != null ? serverSettings.getMaxRam() : 1024;
        Object minRam = serverSettings != null ? serverSettings.getMinRam() : 1024;
        this.configuredHeapMaxMb = toIntMb(maxRam);
        this.configuredHeapMinMb = toIntMb(minRam);
        File jarFile = serverSettings != null && serverSettings.getJarFile() != null
                ? serverSettings.getJarFile() : null;
        this.serverJarPath = jarFile != null ? jarFile.getAbsolutePath() : null;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel statsSection = new JPanel();
        statsSection.setLayout(new BoxLayout(statsSection, BoxLayout.Y_AXIS));
        statsSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblCpu = new JLabel("CPU: 0%");
        lblCpu.setMinimumSize(new Dimension(lblCpu.getFontMetrics(lblCpu.getFont()).stringWidth("CPU: 100.0%") + 8, lblCpu.getPreferredSize().height));
        lblCpu.setFont(lblCpu.getFont().deriveFont(Font.PLAIN));
        lblCpu.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblMemoryUse = new JLabel("Memory use: 0 mb (100% free)");
        lblMemoryUse.setFont(lblMemoryUse.getFont().deriveFont(Font.PLAIN));
        lblMemoryUse.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblHeap = new JLabel("Heap (allocated): " + configuredHeapMinMb + "–" + configuredHeapMaxMb + " mb");
        lblHeap.setFont(lblHeap.getFont().deriveFont(Font.PLAIN));
        lblHeap.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblPlayers = new JLabel("Players: 0");
        lblPlayers.setFont(lblPlayers.getFont().deriveFont(Font.PLAIN));
        lblPlayers.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblPid = new JLabel("PID: N/A");
        lblPid.setFont(lblPid.getFont().deriveFont(Font.PLAIN));
        lblPid.setHorizontalAlignment(SwingConstants.RIGHT);
        lblPid.setToolTipText("Process ID of the server JVM being monitored (- when not found or server offline).");

        statsSection.add(lblCpu);
        statsSection.add(Box.createVerticalStrut(4));
        statsSection.add(lblMemoryUse);
        statsSection.add(Box.createVerticalStrut(4));
        statsSection.add(lblHeap);
        statsSection.add(Box.createVerticalStrut(4));
        statsSection.add(lblPlayers);

        graphPanel = new GraphPanel(configuredHeapMaxMb);

        JLabel statisticsHeading = new JLabel("<html><u>Live Statistics</u></html>");
        statisticsHeading.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.add(statisticsHeading, BorderLayout.NORTH);
        north.add(statsSection, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        JPanel graphWrapper = new JPanel(new BorderLayout(0, 4));
        JPanel checkboxRow = new JPanel(new BorderLayout(0, 0));
        checkboxRow.add(graphPanel.getCheckboxPanel(), BorderLayout.WEST);
        checkboxRow.add(lblPid, BorderLayout.EAST);
        graphWrapper.add(checkboxRow, BorderLayout.NORTH);
        graphWrapper.add(graphPanel, BorderLayout.CENTER);
        add(graphWrapper, BorderLayout.CENTER);

        try {
            systemInfo = new SystemInfo();
            os = systemInfo.getOperatingSystem();
        } catch (Exception e) {
            lblMemoryUse.setText("Memory use: (OSHI unavailable)");
        }
    }

    private void updateLabelsWhenOffline() {
        lblCpu.setText("CPU: 0%");
        lblMemoryUse.setText("Memory use: 0 mb (100% free)");
        lblHeap.setText("Heap (allocated): " + configuredHeapMinMb + "–" + configuredHeapMaxMb + " mb");
        lblPlayers.setText("Players: 0");
        lblPid.setText("PID: N/A");
    }

    /**
     * Find the server's Minecraft JVM process via OSHI.
     * <p>
     * <ol>Priority: 
     * <li>PID from Server.getServerPid() — works for bridge, adopted, and direct modes;</li>
     * <li>direct process handle PID (Java 9+ path);</li>
     * <li>fall back to searching all Java processes by JAR path (last resort).</li>
     * </ol>
     * </p>
     * <p>
     * In bridge mode the bridge's own process is excluded automatically because we use the
     * server PID reported by the bridge, not a command-line substring scan.
     * </p>
     */
    private OSProcess findServerProcess(Server currentServer) {
        // Primary: use the precise PID the server reports.
        long pid = currentServer.getServerPid();
        if (pid >= 0) {
            try {
                for (OSProcess p : os.getProcesses()) {
                    if (p != null && p.getProcessID() == (int) pid) return p;
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback for direct mode when getServerPid() fails (Java 8 + Windows):
        // search all Java processes whose command line contains the server JAR path,
        // but skip any process whose command line also contains "--bridge" (that's us).
        try {
            int currentPid = os.getProcessId();
            List<OSProcess> all = os.getProcesses();
            for (OSProcess p : all) {
                if (p == null || p.getState() == OSProcess.State.INVALID || p.getProcessID() == currentPid) continue;
                String name = p.getName();
                if (name == null) continue;
                String lower = name.toLowerCase();
                if (!lower.contains("java") && !lower.endsWith("java.exe")) continue;
                String cmd = p.getCommandLine();
                if (cmd == null) continue;
                if (serverJarPath != null && cmd.contains(serverJarPath) && !cmd.contains("--bridge")) return p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int toIntMb(Object ram) {
        if (ram == null) return 1024;
        if (ram instanceof Number) return ((Number) ram).intValue();
        try {
            return Integer.parseInt(String.valueOf(ram));
        } catch (NumberFormatException e) {
            return 1024;
        }
    }

    /** Parse max-players from server.properties; return at least 1 for graph scale. */
    private static int parseMaxPlayers(String maxStr) {
        if (maxStr == null || maxStr.isEmpty()) return 20;
        try {
            int n = Integer.parseInt(maxStr.trim());
            return Math.max(1, n);
        } catch (NumberFormatException e) {
            return 20;
        }
    }

    /** Start polling when the tab is shown; call from SpigotGUI when building the tab. */
    public void startPolling() {
        if (pollTimer != null) return;
        pollTimer = new Timer(POLL_INTERVAL_MS, e -> poll());
        pollTimer.start();
    }

    /** Stop polling when the app shuts down or tab is no longer needed. */
    public void stopPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }
    }

    private void poll() {
        Server currentServer = SpigotGUI.server;
        if (os == null) return;
        if (currentServer == null) return;
        if (!currentServer.isRunning()) {
            updateLabelsWhenOffline();
            samples.clear();
            graphPanel.setSamples(new ArrayList<>());
            return;
        }

        OSProcess serverProcess = findServerProcess(currentServer);
        if (serverProcess == null) {
            lblCpu.setText("CPU: Unknown (server process not found)");
            lblMemoryUse.setText("Memory use: Unknown (server process not found)");
            lblHeap.setText("Heap (allocated): " + configuredHeapMinMb + "–" + configuredHeapMaxMb + " mb");
            lblPlayers.setText("Players: Unknown (server process not found)");
            lblPid.setText("PID: Unknown (server process not found)");
            return;
        }

        lblPid.setText("PID: " + serverProcess.getProcessID());

        long rss = serverProcess.getResidentSetSize();
        long rssMb = rss / BYTES_PER_MB;
        double cpuLoad = 0;
        if (previousProcessSnapshot != null && previousProcessSnapshot.getProcessID() == serverProcess.getProcessID()) {
            try {
                cpuLoad = serverProcess.getProcessCpuLoadBetweenTicks(previousProcessSnapshot);
            } catch (Exception ignored) {
            }
        }
        previousProcessSnapshot = serverProcess;
        double cpuPercent = cpuLoad * 100.0;

        int percentFree = configuredHeapMaxMb > 0
                ? (int) Math.round(100.0 * Math.max(0, configuredHeapMaxMb - rssMb) / configuredHeapMaxMb)
                : 0;
        lblCpu.setText(String.format("CPU: %.1f%%", cpuPercent));
        lblMemoryUse.setText(String.format("Memory use: %d mb (%d%% free)", rssMb, Math.min(100, percentFree)));
        lblHeap.setText("Heap (allocated): " + configuredHeapMinMb + "–" + configuredHeapMaxMb + " mb");
        int playersCount = SpigotGUI.players != null ? SpigotGUI.players.size() : 0;
        String maxPlayersStr = SpigotGUI.getServerMaxPlayersStatic();
        int maxPlayers = parseMaxPlayers(maxPlayersStr);
        lblPlayers.setText(maxPlayersStr != null ? "Players: " + playersCount + " / " + maxPlayersStr : "Players: " + playersCount + " / -");
        samples.add(new Sample(System.currentTimeMillis(), rssMb, cpuPercent, playersCount, maxPlayers));
        while (samples.size() > MAX_SAMPLES) {
            samples.remove(0);
        }
        graphPanel.setSamples(new ArrayList<>(samples));
    }

    static class Sample {
        final long timeMs;
        final long memoryMb;
        final double cpuPercent;
        final int playersCount;
        final int maxPlayers;

        Sample(long timeMs, long memoryMb, double cpuPercent, int playersCount, int maxPlayers) {
            this.timeMs = timeMs;
            this.memoryMb = memoryMb;
            this.cpuPercent = cpuPercent;
            this.playersCount = playersCount;
            this.maxPlayers = Math.max(1, maxPlayers);
        }
    }

    private static class GraphPanel extends JPanel {
        private static final int AXIS_LEFT = 42;
        private static final int AXIS_RIGHT = 38;
        private static final int AXIS_BOTTOM = 24;
        private static final int PAD_TOP = 4;
        private static final float FILL_ALPHA = 0.25f;

        private final int configuredHeapMaxMb;
        private static final long TIME_WINDOW_MS = 5 * 60 * 1000;
        private final List<Sample> graphSamples = new ArrayList<>();
        private final JCheckBox checkRam;
        private final JCheckBox checkCpu;
        private final JCheckBox checkPlayers;
        private int hoverIndex = -1;
        private final JPanel checkboxPanel;

        GraphPanel(int configuredHeapMaxMb) {
            this.configuredHeapMaxMb = Math.max(1, configuredHeapMaxMb);
            setPreferredSize(new Dimension(400, 200));
            setMinimumSize(new Dimension(200, 120));
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(uiColor("ControlShadow", 0x808080)),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)));

            checkCpu = new JCheckBox("CPU", true);
            checkRam = new JCheckBox("RAM", true);
            checkPlayers = new JCheckBox("Players", false);
            checkCpu.addActionListener(e -> repaint());
            checkRam.addActionListener(e -> repaint());
            checkPlayers.addActionListener(e -> repaint());
            checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
            checkboxPanel.add(new JLabel("Show:"));
            checkboxPanel.add(checkCpu);
            checkboxPanel.add(checkRam);
            checkboxPanel.add(checkPlayers);

            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    int prev = hoverIndex;
                    hoverIndex = sampleIndexAt(e.getX());
                    if (hoverIndex != prev) repaint();
                }
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    if (hoverIndex != -1) { hoverIndex = -1; repaint(); }
                }
            });
        }

        JPanel getCheckboxPanel() { return checkboxPanel; }

        private static Color uiColor(String key, int fallbackRgb) {
            Color c = UIManager.getColor(key);
            return c != null ? c : new Color(fallbackRgb);
        }

        private static Color contrastColor(Color bg) {
            double lum = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
            return lum > 0.5 ? new Color(0x40, 0x40, 0x40) : new Color(0xc0, 0xc0, 0xc0);
        }

        /** Orange-yellow that works on light and dark backgrounds. */
        private static Color themeAwareOrange(Color bg) {
            double lum = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
            return lum > 0.5 ? new Color(0xcc, 0x88, 0x00) : new Color(0xff, 0xcc, 0x66);
        }

        void setSamples(List<Sample> samples) {
            graphSamples.clear();
            graphSamples.addAll(samples);
            repaint();
        }

        private int graphLeft() { return AXIS_LEFT; }
        private int graphBottom() { return getHeight() - AXIS_BOTTOM; }
        private int graphWidth() { return getWidth() - AXIS_LEFT - AXIS_RIGHT; }
        private int graphHeight() { return graphBottom() - PAD_TOP; }

        private int sampleIndexAt(int mouseX) {
            int n = graphSamples.size();
            if (n == 0) return -1;
            int gx = mouseX - graphLeft();
            int gw = graphWidth();
            if (gx < 0 || gx > gw) return -1;
            long now = System.currentTimeMillis();
            long tMin = now - TIME_WINDOW_MS;
            long tMax = now;
            long timeAtMouse = tMin + (long) (gx * (tMax - tMin) / (double) gw);
            int best = 0;
            long bestDiff = Math.abs(graphSamples.get(0).timeMs - timeAtMouse);
            for (int i = 1; i < n; i++) {
                long d = Math.abs(graphSamples.get(i).timeMs - timeAtMouse);
                if (d < bestDiff) { bestDiff = d; best = i; }
            }
            return best;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bg = getBackground();
            if (bg == null) bg = uiColor("Panel.background", 0xf0f0f0);
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            int gw = graphWidth();
            int gh = graphHeight();
            int gx0 = graphLeft();
            int gy0 = PAD_TOP;
            if (gw <= 0 || gh <= 0) return;

            Color gridColor = new Color(contrastColor(bg).getRed(), contrastColor(bg).getGreen(), contrastColor(bg).getBlue(), 80);
            Color axisColor = contrastColor(bg);
            Font origFont = g2.getFont();
            g2.setFont(origFont.deriveFont(9f));

            boolean showRam = checkRam.isSelected();
            boolean showCpu = checkCpu.isSelected();
            boolean showPlayers = checkPlayers.isSelected();
            if (!showRam && !showCpu && !showPlayers) {
                g2.setColor(axisColor);
                g2.drawString("Select RAM, CPU and/or Players", gx0, gy0 + gh / 2);
                return;
            }

            int n = graphSamples.size();
            long now = System.currentTimeMillis();
            long tMin = now - TIME_WINDOW_MS;
            long tMax = now;
            long memMax = configuredHeapMaxMb;
            for (Sample s : graphSamples) {
                if (s.memoryMb > memMax) memMax = s.memoryMb;
            }
            if (memMax == 0) memMax = 1;

            g2.setColor(gridColor);
            for (int i = 1; i <= 4; i++) {
                int y = gy0 + gh - (gh * i / 5);
                g2.drawLine(gx0, y, gx0 + gw, y);
            }
            for (int i = 1; i <= 4; i++) {
                int x = gx0 + (gw * i / 5);
                g2.drawLine(x, gy0, x, gy0 + gh);
            }

            g2.setColor(axisColor);
            g2.drawLine(gx0, gy0, gx0, gy0 + gh);
            g2.drawLine(gx0, gy0 + gh, gx0 + gw, gy0 + gh);
            g2.drawLine(gx0 + gw, gy0, gx0 + gw, gy0 + gh);
            int labelBelowAxisY = gy0 + gh + 12;
            int labelAboveAxisY = gy0 + gh - 2;
            if (showRam) {
                g2.drawString("0", gx0 - 10, labelAboveAxisY);
                g2.drawString(memMax + " mb", gx0 - 32, gy0 + 4);
            }
            if (showCpu) {
                g2.drawString("0%", gx0 + gw + 8, labelAboveAxisY);
                g2.drawString("100%", gx0 + gw + 8, gy0 + 4);
            } else if (showPlayers) {
                g2.drawString("0%", gx0 + gw + 8, labelAboveAxisY);
                g2.drawString("100%", gx0 + gw + 8, gy0 + 4);
            }
            g2.drawString("5m ago", gx0, labelBelowAxisY);
            g2.drawString("now", gx0 + gw - 14, labelBelowAxisY);

            if (n < 2) return;

            int[] x = new int[n];
            for (int i = 0; i < n; i++) {
                x[i] = gx0 + (int) ((graphSamples.get(i).timeMs - tMin) * gw / (tMax - tMin));
            }

            Color ramLineColor = new Color(0x2e, 0x8b, 0x2e);
            Color ramFillColor = new Color(0x2e, 0x8b, 0x2e, (int) (255 * FILL_ALPHA));
            Color cpuLineColor = new Color(0x1e, 0x90, 0xff);
            Color cpuFillColor = new Color(0x1e, 0x90, 0xff, (int) (255 * FILL_ALPHA));
            Color playersLineColor = themeAwareOrange(bg);
            Color playersFillColor = new Color(playersLineColor.getRed(), playersLineColor.getGreen(), playersLineColor.getBlue(), (int) (255 * FILL_ALPHA));

            int[] yRam = new int[n];
            int[] yCpu = new int[n];
            int[] yPlayers = new int[n];
            for (int i = 0; i < n; i++) {
                Sample s = graphSamples.get(i);
                yRam[i] = gy0 + gh - (int) ((s.memoryMb * gh) / memMax);
                double cpu = Math.min(100.0, Math.max(0, s.cpuPercent));
                yCpu[i] = gy0 + gh - (int) (cpu * gh / 100.0);
                int pct = Math.min(100, (s.playersCount * 100) / s.maxPlayers);
                yPlayers[i] = gy0 + gh - (int) (pct * gh / 100.0);
            }
            if (showRam) drawSeries(g2, x, yRam, n, gy0, gh, ramFillColor, ramLineColor, 1.5f);
            if (showCpu) drawSeries(g2, x, yCpu, n, gy0, gh, cpuFillColor, cpuLineColor, 1.5f);
            if (showPlayers) drawSeries(g2, x, yPlayers, n, gy0, gh, playersFillColor, playersLineColor, 1.5f);

            if (hoverIndex >= 0 && hoverIndex < n) {
                Sample s = graphSamples.get(hoverIndex);
                int hx = x[hoverIndex];
                g2.setColor(new Color(0x60, 0x60, 0x60, 150));
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(hx, gy0, hx, gy0 + gh);
                if (showRam) {
                    int dy = gy0 + gh - (int) ((s.memoryMb * gh) / memMax);
                    g2.setColor(ramLineColor);
                    g2.fillOval(hx - 4, dy - 4, 8, 8);
                }
                if (showCpu) {
                    double cpu = Math.min(100.0, Math.max(0, s.cpuPercent));
                    int dy = gy0 + gh - (int) (cpu * gh / 100.0);
                    g2.setColor(cpuLineColor);
                    g2.fillOval(hx - 4, dy - 4, 8, 8);
                }
                if (showPlayers) {
                    int pct = Math.min(100, (s.playersCount * 100) / s.maxPlayers);
                    int dy = gy0 + gh - (int) (pct * gh / 100.0);
                    g2.setColor(playersLineColor);
                    g2.fillOval(hx - 4, dy - 4, 8, 8);
                }
                java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("HH:mm:ss");
                String timeStr = df.format(new java.util.Date(s.timeMs));
                StringBuilder tip = new StringBuilder("<html>");
                if (showCpu) tip.append("CPU: ").append(String.format("%.1f%%", s.cpuPercent)).append("<br>");
                if (showRam) tip.append("RAM: ").append(s.memoryMb).append(" mb (").append(memMax > 0 ? (s.memoryMb * 100 / memMax) : 0).append("%)<br>");
                if (showPlayers) tip.append("Players: ").append(s.playersCount).append(" / ").append(s.maxPlayers).append("<br>");
                tip.append(timeStr).append("</html>");
                setToolTipText(tip.toString());
            } else {
                setToolTipText(null);
            }
            g2.setFont(origFont);
        }

        private void drawSeries(Graphics2D g2, int[] x, int[] y, int n, int gy0, int gh, Color fillColor, Color lineColor, float strokeWidth) {
            graphLeft();
            g2.setColor(fillColor);
            java.awt.Polygon poly = new java.awt.Polygon();
            poly.addPoint(x[0], gy0 + gh);
            for (int i = 0; i < n; i++) poly.addPoint(x[i], y[i]);
            poly.addPoint(x[n - 1], gy0 + gh);
            g2.fillPolygon(poly);
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(strokeWidth));
            for (int i = 0; i < n - 1; i++) g2.drawLine(x[i], y[i], x[i + 1], y[i + 1]);
        }
    }
}
