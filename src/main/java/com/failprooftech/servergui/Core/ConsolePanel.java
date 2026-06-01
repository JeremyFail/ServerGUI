package com.failprooftech.servergui.Core;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultCaret;
import javax.swing.text.StyledDocument;

import com.failprooftech.servergui.Utils.ConsoleFindBar;
import com.failprooftech.servergui.Utils.ConsoleStyleHelper;

/**
 * Builds the Console tab: scrollable log, command input field, and options
 * (Console for /say, Console scroll sticky). Callbacks to the main frame
 * for sending commands and scroll/sticky behavior.
 */
public class ConsolePanel extends JPanel {

	private final JTextPane consoleTextPane;
	private final JScrollPane consoleScrollPane;
	private final JTextField consoleCommandInput;
	private final JCheckBox chkConsoleInputAsSay;
	private final JCheckBox chkConsoleScrollSticky;
	private final ConsoleStyleHelper consoleStyleHelper;

	private final List<String> commandHistory = new ArrayList<>();
	private int historyIndex = 0;
	private String pendingText = "";

	private JLayeredPane consoleLayer;
	private ConsoleFindBar findBar;

	/**
	 * @param gui Main frame; must implement console callbacks (send command, scroll, sticky).
	 * @param consoleFont Monospace font for the log and input.
	 * @param consoleDarkMode Dark background for the log.
	 * @param colorsEnabled Whether to apply ANSI/§ colors.
	 * @param wrapWordBreakOnly Wrap only at word boundaries.
	 * @param manualStickyMode Whether the sticky checkbox is visible and controls sticky.
	 * @param initialStickToBottom Initial value for scroll sticky.
	 */
	public ConsolePanel(ServerGUI gui, Font consoleFont, boolean consoleDarkMode, boolean colorsEnabled,
			boolean wrapWordBreakOnly, boolean manualStickyMode, boolean initialStickToBottom) {
		ConsoleStyleHelper.setConsoleWrapWordBreakOnly(wrapWordBreakOnly);
		consoleTextPane = new JTextPane();
		consoleTextPane.setEditorKit(new com.failprooftech.servergui.Utils.WrapEditorKit());
		consoleTextPane.setFont(consoleFont);
		consoleTextPane.setEditable(false);
		consoleTextPane.setMargin(new Insets(4, 4, 4, 4));
		consoleStyleHelper = new ConsoleStyleHelper(consoleTextPane, consoleFont, consoleDarkMode, 500_000);
		consoleStyleHelper.setColorsEnabled(colorsEnabled);
		DefaultCaret caret = (DefaultCaret) consoleTextPane.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(consoleTextPane);
		consoleScrollPane = scrollPane;
		scrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				if (consoleTextPane != null) consoleTextPane.revalidate();
			}
		});

		consoleLayer = new JLayeredPane() {
			@Override
			public boolean isOptimizedDrawingEnabled() { return false; }
		};
		consoleLayer.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				scrollPane.setBounds(0, 0, consoleLayer.getWidth(), consoleLayer.getHeight());
				if (findBar != null && findBar.isVisible()) positionFindBar();
			}
		});
		consoleLayer.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
		scrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
			@Override
			public void adjustmentValueChanged(AdjustmentEvent e) {
				gui.notifyConsoleScrollBarAdjustment(e);
			}
		});

		// Clickable links in console
		consoleTextPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() != MouseEvent.BUTTON1) return;
				String url = getLinkUrlAt(consoleTextPane, e.getPoint());
				if (url != null) {
					try {
						java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
					} catch (Exception ex) { /* ignore */ }
				}
			}
		});
		consoleTextPane.addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				String url = getLinkUrlAt(consoleTextPane, e.getPoint());
				consoleTextPane.setCursor(url != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
		});

		getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "consoleFind");
		getActionMap().put("consoleFind", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				showFindBar();
			}
		});
		getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "consoleFindClose");
		getActionMap().put("consoleFindClose", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				if (findBar != null && findBar.isVisible()) findBar.close();
			}
		});

		chkConsoleInputAsSay = new JCheckBox("Console for /say");
		chkConsoleInputAsSay.setToolTipText("When checked, your console input is sent as \"say <text>\", so it appears as a server message in game chat. When unchecked, input is sent as a raw command.");

		chkConsoleScrollSticky = new JCheckBox("Console scroll sticky");
		chkConsoleScrollSticky.setSelected(initialStickToBottom);
		chkConsoleScrollSticky.setToolTipText("When checked, the console will auto-scroll to the bottom when new lines arrive. When unchecked, it stays at the current position.");
		chkConsoleScrollSticky.setVisible(manualStickyMode);
		chkConsoleScrollSticky.setEnabled(manualStickyMode);
		chkConsoleScrollSticky.addActionListener(ev -> {
			if (manualStickyMode) {
				gui.setConsoleStickToBottom(chkConsoleScrollSticky.isSelected());
				gui.updateScrollStickyDebugCheckbox();
			}
		});

		consoleCommandInput = new JTextField();
		consoleCommandInput.setFont(consoleFont);
		consoleCommandInput.setMargin(new Insets(4, 6, 4, 6));
		consoleCommandInput.setPreferredSize(new java.awt.Dimension(consoleCommandInput.getPreferredSize().width, 26));
		consoleCommandInput.setMinimumSize(new java.awt.Dimension(60, 26));
		Runnable sendCmd = () -> {
			String text = consoleCommandInput.getText().trim();
			if (text.isEmpty()) return;
			commandHistory.add(text);
			historyIndex = commandHistory.size();
			pendingText = "";
			gui.sendConsoleCommand(text, chkConsoleInputAsSay.isSelected());
			consoleCommandInput.setText("");
			gui.scheduleScrollAfterCommand();
		};
		consoleCommandInput.addActionListener(e -> sendCmd.run());
		consoleCommandInput.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_UP) {
					if (commandHistory.isEmpty()) return;
					if (historyIndex == commandHistory.size()) {
						pendingText = consoleCommandInput.getText();
					}
					if (historyIndex > 0) {
						historyIndex--;
					}
					consoleCommandInput.setText(commandHistory.get(historyIndex));
					e.consume();
				} else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					if (historyIndex >= commandHistory.size()) return;
					historyIndex++;
					if (historyIndex == commandHistory.size()) {
						consoleCommandInput.setText(pendingText);
					} else {
						consoleCommandInput.setText(commandHistory.get(historyIndex));
					}
					e.consume();
				}
			}
		});
		consoleCommandInput.setColumns(10);

		// Send button with vertically-centered "enter" symbol
		JButton sendButton = new JButton("") {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setFont(getFont());
				g2.setColor(getForeground());
				FontMetrics fm = g2.getFontMetrics();
				String sym = "\u21B5";
				int x = (getWidth() - fm.stringWidth(sym)) / 2;
					int y = ((getHeight() + fm.getAscent() - fm.getDescent()) / 2) - 2;
				g2.drawString(sym, x, y);
				g2.dispose();
			}
		};
		sendButton.setFont(sendButton.getFont().deriveFont(20f));
		sendButton.setMargin(new Insets(0, 0, 0, 0));
		sendButton.setPreferredSize(new java.awt.Dimension(26, 26));
		sendButton.setMinimumSize(new java.awt.Dimension(26, 26));
		sendButton.setMaximumSize(new java.awt.Dimension(26, 26));
		sendButton.setToolTipText("Send command (Enter)");
		sendButton.addActionListener(e -> sendCmd.run());

		GroupLayout gl = new GroupLayout(this);
		setLayout(gl);
		gl.setHorizontalGroup(
			gl.createParallelGroup(Alignment.LEADING)
				.addComponent(consoleLayer, GroupLayout.DEFAULT_SIZE, 650, Short.MAX_VALUE)
				.addGroup(gl.createSequentialGroup()
					.addGap(10)
					.addComponent(consoleCommandInput, GroupLayout.DEFAULT_SIZE, 640, Short.MAX_VALUE)
					.addGap(4)
					.addComponent(sendButton, 26, 26, 26)
					.addGap(10))
				.addGroup(gl.createSequentialGroup()
					.addContainerGap()
					.addComponent(chkConsoleInputAsSay)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(chkConsoleScrollSticky)
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
		);
		gl.setVerticalGroup(
			gl.createParallelGroup(Alignment.LEADING)
				.addGroup(gl.createSequentialGroup()
					.addComponent(consoleLayer, GroupLayout.DEFAULT_SIZE, 383, Short.MAX_VALUE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(gl.createParallelGroup(Alignment.CENTER)
						.addComponent(consoleCommandInput, GroupLayout.PREFERRED_SIZE, 26, GroupLayout.PREFERRED_SIZE)
						.addComponent(sendButton, 26, 26, 26))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(gl.createParallelGroup(Alignment.BASELINE)
						.addComponent(chkConsoleInputAsSay)
						.addComponent(chkConsoleScrollSticky))
					.addGap(3))
		);

		SwingUtilities.invokeLater(() ->
				scrollPane.setBounds(0, 0, Math.max(consoleLayer.getWidth(), 1), Math.max(consoleLayer.getHeight(), 1)));
	}

	private void showFindBar() {
		if (findBar == null) {
			findBar = new ConsoleFindBar(consoleTextPane, consoleScrollPane, consoleStyleHelper);
			consoleLayer.add(findBar, JLayeredPane.PALETTE_LAYER);
		}
		positionFindBar();
		if (consoleTextPane.isFocusOwner()) {
			String sel = consoleTextPane.getSelectedText();
			if (sel != null && !sel.isEmpty()) {
				findBar.setFindText(sel);
			} else {
				findBar.clearFindText();
			}
		} else {
			findBar.clearFindText();
		}
		findBar.setVisible(true);
		findBar.refreshSearch();
		findBar.focusFindField();
	}

	/** Called when console dark/light mode changes so find highlights stay readable. */
	public void updateFindBarTheme() {
		if (findBar != null) findBar.updateHighlightColor();
	}

	private void positionFindBar() {
		Dimension pref = findBar.getPreferredSize();
		int barX = Math.max(0, consoleLayer.getWidth() - pref.width - 10);
		findBar.setBounds(barX, 10, pref.width, pref.height);
		findBar.revalidate();
	}

	@SuppressWarnings("deprecation")
	private static String getLinkUrlAt(JTextPane textPane, Point p) {
		int offs = textPane.viewToModel(p);
		if (offs < 0) return null;
		StyledDocument doc = (StyledDocument) textPane.getDocument();
		javax.swing.text.Element el = doc.getCharacterElement(offs);
		Object url = el.getAttributes().getAttribute(ConsoleStyleHelper.LINK_URL);
		return (url instanceof String) ? (String) url : null;
	}

	public JTextPane getConsoleTextPane() { return consoleTextPane; }
	public JScrollPane getConsoleScrollPane() { return consoleScrollPane; }
	public JTextField getConsoleCommandInput() { return consoleCommandInput; }
	public JCheckBox getChkConsoleInputAsSay() { return chkConsoleInputAsSay; }
	public JCheckBox getChkConsoleScrollSticky() { return chkConsoleScrollSticky; }
	public ConsoleStyleHelper getConsoleStyleHelper() { return consoleStyleHelper; }
}
