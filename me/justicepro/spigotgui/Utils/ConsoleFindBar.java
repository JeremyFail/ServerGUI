package me.justicepro.spigotgui.Utils;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * VS Code-style find-only bar for the console {@link JTextPane}.
 * Behavior mirrors the file editor find bar (no replace row).
 */
public class ConsoleFindBar extends JPanel {

	private final JTextPane textPane;
	private final JScrollPane scrollPane;
	private final ConsoleStyleHelper styleHelper;

	private final JTextField findField = new JTextField();
	private final JToggleButton matchCaseBtn;
	private final JToggleButton wholeWordBtn;
	private final JToggleButton regexBtn;
	private final JLabel resultLabel = new JLabel("", JLabel.LEFT);
	private final JButton prevBtn;
	private final JButton nextBtn;
	private final JButton closeBtn;

	private final List<Object> markTags = new ArrayList<>();
	private final List<int[]> matchRanges = new ArrayList<>();
	private Highlighter.HighlightPainter markPainter;
	private Highlighter.HighlightPainter activePainter;
	private Object activeTag;
	private int activeStart = -1;
	private int activeEnd = -1;
	private int currentMatch = 0;
	private int totalMatches = 0;

	public ConsoleFindBar(JTextPane textPane, JScrollPane scrollPane, ConsoleStyleHelper styleHelper) {
		this.textPane = textPane;
		this.scrollPane = scrollPane;
		this.styleHelper = styleHelper;
		updateHighlightColor();

		setLayout(new FlowLayout(FlowLayout.LEFT, 3, 1));
		setOpaque(true);

		Color bg = UIManager.getColor("Panel.background");
		Color border = UIManager.getColor("controlShadow");
		if (border == null) border = Color.GRAY;
		Color bg2 = bg != null ? bg : new Color(0xF3F3F3);
		Color mutedBorder = new Color(
				(border.getRed() + 3 * bg2.getRed()) / 4,
				(border.getGreen() + 3 * bg2.getGreen()) / 4,
				(border.getBlue() + 3 * bg2.getBlue()) / 4);
		setBackground(bg2);
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(mutedBorder, 1),
				new EmptyBorder(3, 4, 3, 4)));

		findField.setPreferredSize(new Dimension(170, 22));
		add(findField);

		matchCaseBtn = mkToggle("Aa", "Match Case");
		wholeWordBtn = mkToggle("W", "Match Whole Word");
		regexBtn = mkToggle(".*", "Use Regular Expression");
		add(matchCaseBtn);
		add(wholeWordBtn);
		add(regexBtn);

		resultLabel.setPreferredSize(new Dimension(72, 20));
		resultLabel.setFont(resultLabel.getFont().deriveFont(Font.PLAIN, 11f));
		add(resultLabel);

		prevBtn = mkArrowBtn(true, "Previous Match (Shift+Enter)");
		nextBtn = mkArrowBtn(false, "Next Match (Enter)");
		closeBtn = mkCloseBtn();
		prevBtn.addActionListener(e -> navigate(true));
		nextBtn.addActionListener(e -> navigate(false));
		closeBtn.addActionListener(e -> close());
		add(prevBtn);
		add(nextBtn);
		add(closeBtn);

		findField.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e) { onFindTextChanged(); }
			@Override public void removeUpdate(DocumentEvent e) { onFindTextChanged(); }
			@Override public void changedUpdate(DocumentEvent e) { }
		});
		findField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) navigate(e.isShiftDown());
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) close();
			}
		});
		matchCaseBtn.addActionListener(e -> onFindTextChanged());
		wholeWordBtn.addActionListener(e -> onFindTextChanged());
		regexBtn.addActionListener(e -> onFindTextChanged());

		installTabCycle();

		textPane.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e) { refreshIfVisible(); }
			@Override public void removeUpdate(DocumentEvent e) { refreshIfVisible(); }
			@Override public void changedUpdate(DocumentEvent e) { }
		});
	}

	/** Amber on dark console background, bright yellow on light (same idea as the file editor). */
	public void updateHighlightColor() {
		Color consoleBg = styleHelper != null ? styleHelper.getDefaultBackground() : Color.WHITE;
		double lum = (0.299 * consoleBg.getRed() + 0.587 * consoleBg.getGreen() + 0.114 * consoleBg.getBlue()) / 255.0;
		markPainter = new DefaultHighlighter.DefaultHighlightPainter(
				lum < 0.4 ? new Color(0xB8860B) : new Color(0xFFFF00));
		Color selBg = UIManager.getColor("TextPane.selectionBackground");
		if (selBg == null) selBg = UIManager.getColor("textHighlight");
		if (selBg == null) selBg = new Color(0x3399FF);
		activePainter = new DefaultHighlighter.DefaultHighlightPainter(selBg);
		if (isVisible()) onFindTextChanged();
	}

	public void focusFindField() {
		findField.requestFocusInWindow();
		findField.selectAll();
	}

	public void setFindText(String text) {
		findField.setText(text);
	}

	public void clearFindText() {
		findField.setText("");
	}

	/** Re-run search/highlights for the current find field text (e.g. when reopening the bar). */
	public void refreshSearch() {
		onFindTextChanged();
	}

	public void close() {
		setVisible(false);
		clearHighlights();
		textPane.requestFocusInWindow();
	}

	private void refreshIfVisible() {
		if (isVisible()) SwingUtilities.invokeLater(this::onFindTextChanged);
	}

	private void onFindTextChanged() {
		currentMatch = 0;
		activeStart = -1;
		activeEnd = -1;
		updateCount();
		if (totalMatches > 0) {
			try {
				SearchText docText = SearchText.fromDocument(textPane.getDocument());
				int from = textPane.getSelectionStart();
				if (from < 0) from = textPane.getCaretPosition();
				int[] match = findNextMatch(docText, from, true);
				selectMatch(match, false);
				refreshCurrentIndex(docText);
				reapplyHighlights();
			} catch (PatternSyntaxException | BadLocationException ex) {
				// reflected in resultLabel by updateCount()
			}
		}
	}

	private void updateCount() {
		String searchFor = findField.getText();
		if (searchFor.isEmpty()) {
			totalMatches = 0;
			currentMatch = 0;
			resultLabel.setText("");
			resultLabel.setForeground(UIManager.getColor("Label.foreground"));
			clearHighlights();
			return;
		}
		try {
			matchRanges.clear();
			SearchText docText = SearchText.fromDocument(textPane.getDocument());
			Pattern pat = buildPattern(searchFor);
			Matcher m = pat.matcher(docText.text);
			while (m.find()) {
				matchRanges.add(new int[] {
						docText.modelStart(m.start()),
						docText.modelEnd(m.end())
				});
			}
			totalMatches = matchRanges.size();
			if (totalMatches == 0) {
				resultLabel.setText("No results");
				resultLabel.setForeground(new Color(0xCC3333));
			} else {
				resultLabel.setForeground(UIManager.getColor("Label.foreground"));
				int shown = currentMatch > 0 ? currentMatch : 1;
				resultLabel.setText(shown + " of " + totalMatches);
			}
		} catch (PatternSyntaxException ex) {
			totalMatches = 0;
			resultLabel.setText("Invalid regex");
			resultLabel.setForeground(new Color(0xCC3333));
			clearHighlights();
		} catch (BadLocationException ex) {
			totalMatches = 0;
			clearHighlights();
		}
	}

	private void refreshCurrentIndex(SearchText docText) {
		String searchFor = findField.getText();
		if (searchFor.isEmpty() || totalMatches == 0) return;
		int caretPos = textPane.getSelectionStart();
		int searchCaret = docText.toSearchIndex(caretPos);
		try {
			Pattern pat = buildPattern(searchFor);
			Matcher m = pat.matcher(docText.text);
			int idx = 0;
			while (m.find()) {
				idx++;
				if (m.end() >= searchCaret) {
					currentMatch = idx;
					resultLabel.setText(currentMatch + " of " + totalMatches);
					resultLabel.setForeground(UIManager.getColor("Label.foreground"));
					return;
				}
			}
			currentMatch = totalMatches;
			resultLabel.setText(currentMatch + " of " + totalMatches);
		} catch (PatternSyntaxException ex) {
			// ignore
		}
	}

	private void navigate(boolean backward) {
		String searchFor = findField.getText();
		if (searchFor.isEmpty()) return;
		try {
			SearchText docText = SearchText.fromDocument(textPane.getDocument());
			int from = textPane.getSelectionStart();
			if (from < 0) from = textPane.getCaretPosition();
			int[] match = backward ? findPrevMatch(docText, from) : findNextMatch(docText, from, false);
			if (match != null) {
				selectMatch(match, true);
				refreshCurrentIndex(docText);
				reapplyHighlights();
			}
		} catch (PatternSyntaxException | BadLocationException ex) {
			resultLabel.setText("Invalid regex");
			resultLabel.setForeground(new Color(0xCC3333));
		}
	}

	private int[] findNextMatch(SearchText docText, int modelFrom, boolean includeCurrent)
			throws BadLocationException {
		String searchFor = findField.getText();
		if (searchFor.isEmpty()) return null;
		Pattern pat = buildPattern(searchFor);
		Matcher m = pat.matcher(docText.text);
		int searchFrom = docText.toSearchIndex(modelFrom);
		int startAt = includeCurrent ? searchFrom : searchFrom + 1;
		if (startAt < 0) startAt = 0;
		if (m.find(startAt)) {
			return new int[] { docText.modelStart(m.start()), docText.modelEnd(m.end()) };
		}
		if (m.find(0)) {
			return new int[] { docText.modelStart(m.start()), docText.modelEnd(m.end()) };
		}
		return null;
	}

	private int[] findPrevMatch(SearchText docText, int modelFrom) throws BadLocationException {
		String searchFor = findField.getText();
		if (searchFor.isEmpty()) return null;
		Pattern pat = buildPattern(searchFor);
		Matcher m = pat.matcher(docText.text);
		int searchBefore = docText.toSearchIndex(modelFrom > 0 ? modelFrom - 1 : 0);
		int[] last = null;
		while (m.find()) {
			if (m.start() > searchBefore) break;
			last = new int[] { docText.modelStart(m.start()), docText.modelEnd(m.end()) };
		}
		if (last != null) return last;
		m = pat.matcher(docText.text);
		while (m.find()) {
			last = new int[] { docText.modelStart(m.start()), docText.modelEnd(m.end()) };
		}
		return last;
	}

	private void selectMatch(int[] match, boolean scroll) {
		if (match == null) return;
		activeStart = match[0];
		activeEnd = match[1];
		textPane.setCaretPosition(match[0]);
		textPane.moveCaretPosition(match[1]);
		if (scroll) scrollMatchIntoView(match[0], match[1]);
	}

	/** Mark-all highlights for every match except the active one; active match uses selection color. */
	private void reapplyHighlights() {
		clearHighlights();
		if (activePainter == null) {
			Color selBg = UIManager.getColor("TextPane.selectionBackground");
			if (selBg == null) selBg = UIManager.getColor("textHighlight");
			if (selBg == null) selBg = new Color(0x3399FF);
			activePainter = new DefaultHighlighter.DefaultHighlightPainter(selBg);
		}
		try {
			Highlighter highlighter = textPane.getHighlighter();
			for (int i = 0; i < matchRanges.size(); i++) {
				if (currentMatch > 0 && i == currentMatch - 1) continue;
				int[] r = matchRanges.get(i);
				markTags.add(highlighter.addHighlight(r[0], r[1], markPainter));
			}
			if (activeStart >= 0 && activeEnd > activeStart) {
				activeTag = highlighter.addHighlight(activeStart, activeEnd, activePainter);
				textPane.setCaretPosition(activeStart);
				textPane.moveCaretPosition(activeEnd);
			}
		} catch (BadLocationException ex) {
			// ignore
		}
	}

	private void scrollMatchIntoView(int start, int end) {
		try {
			Rectangle r0 = textPane.modelToView(start);
			Rectangle r1 = textPane.modelToView(end);
			if (r0 == null || r1 == null) return;
			Rectangle union = r0.union(r1);
			if (scrollPane != null) {
				Rectangle viewRect = scrollPane.getViewport().getViewRect();
				if (!viewRect.contains(union)) {
					textPane.scrollRectToVisible(union);
				}
			} else {
				textPane.scrollRectToVisible(union);
			}
		} catch (BadLocationException ex) {
			// ignore
		}
	}

	private void clearHighlights() {
		Highlighter highlighter = textPane.getHighlighter();
		for (Object tag : markTags) {
			highlighter.removeHighlight(tag);
		}
		markTags.clear();
		if (activeTag != null) {
			highlighter.removeHighlight(activeTag);
			activeTag = null;
		}
	}

	private Pattern buildPattern(String searchFor) {
		int flags = Pattern.DOTALL;
		if (!matchCaseBtn.isSelected()) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		String pat = regexBtn.isSelected() ? searchFor : Pattern.quote(searchFor);
		if (wholeWordBtn.isSelected()) pat = "\\b(?:" + pat + ")\\b";
		return Pattern.compile(pat, flags);
	}

	private void installTabCycle() {
		javax.swing.JComponent[] cycle = {
				findField, matchCaseBtn, wholeWordBtn, regexBtn, prevBtn, nextBtn, closeBtn
		};
		for (javax.swing.JComponent c : cycle) {
			c.setFocusTraversalKeysEnabled(false);
		}
		KeyAdapter tabListener = new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() != KeyEvent.VK_TAB) return;
				e.consume();
				javax.swing.JComponent src = (javax.swing.JComponent) e.getSource();
				int idx = -1;
				for (int i = 0; i < cycle.length; i++) {
					if (cycle[i] == src) { idx = i; break; }
				}
				if (idx < 0) idx = 0;
				int next = e.isShiftDown()
						? (idx - 1 + cycle.length) % cycle.length
						: (idx + 1) % cycle.length;
				cycle[next].requestFocusInWindow();
			}
		};
		for (javax.swing.JComponent c : cycle) {
			c.addKeyListener(tabListener);
		}
	}

	private JToggleButton mkToggle(String text, String tooltip) {
		JToggleButton b = new JToggleButton(text);
		b.setToolTipText(tooltip);
		b.setMargin(new Insets(1, 4, 1, 4));
		b.setFont(b.getFont().deriveFont(Font.BOLD, 11f));
		b.setPreferredSize(new Dimension(30, 22));
		return b;
	}

	private JButton mkArrowBtn(boolean upward, String tooltip) {
		FlatSVGIcon arrowSvg = new FlatSVGIcon("svg/arrow.svg", 14, 14);
		JButton b = new JButton(new RotatedIcon(arrowSvg, upward ? Math.PI / 2 : -Math.PI / 2));
		b.setText("");
		b.setToolTipText(tooltip);
		b.setMargin(new Insets(1, 4, 1, 4));
		b.setPreferredSize(new Dimension(26, 22));
		return b;
	}

	private JButton mkCloseBtn() {
		FlatSVGIcon closeIcon = new FlatSVGIcon("svg/close.svg", 18, 18);
		closeIcon.setColorFilter(new FlatSVGIcon.ColorFilter(
				c -> UIManager.getColor("Button.foreground")));
		JButton b = new JButton(closeIcon);
		b.setText("");
		b.setToolTipText("Close (Escape)");
		b.setMargin(new Insets(1, 4, 1, 4));
		b.setPreferredSize(new Dimension(26, 22));
		return b;
	}

	/**
	 * Plain text for searching with a map back to {@link Document} offsets.
	 * Carriage returns are omitted from the searchable string because they occupy model
	 * positions but do not advance the painted text, which would misalign highlights.
	 */
	private static final class SearchText {
		final String text;
		private final int[] toModel;

		private SearchText(String text, int[] toModel) {
			this.text = text;
			this.toModel = toModel;
		}

		static SearchText fromDocument(Document doc) throws BadLocationException {
			int len = doc.getLength();
			StringBuilder sb = new StringBuilder(len);
			int[] offsets = new int[len];
			int si = 0;
			for (int i = 0; i < len; i++) {
				char c = doc.getText(i, 1).charAt(0);
				if (c == '\r') continue;
				sb.append(c);
				offsets[si++] = i;
			}
			return new SearchText(sb.toString(), java.util.Arrays.copyOf(offsets, si));
		}

		int modelStart(int searchIndex) {
			return toModel[searchIndex];
		}

		int modelEnd(int searchEndExclusive) {
			if (searchEndExclusive <= 0) return toModel[0];
			return toModel[searchEndExclusive - 1] + 1;
		}

		int toSearchIndex(int modelOffset) {
			for (int i = toModel.length - 1; i >= 0; i--) {
				if (toModel[i] <= modelOffset) return i;
			}
			return 0;
		}
	}

	private static class RotatedIcon implements Icon {
		private final Icon delegate;
		private final double angle;

		RotatedIcon(Icon delegate, double angle) {
			this.delegate = delegate;
			this.angle = angle;
		}

		@Override public int getIconWidth() {
			double a = angle % Math.PI;
			if (a < 0) a += Math.PI;
			boolean swap = (a > Math.PI / 4 && a < 3 * Math.PI / 4);
			return swap ? delegate.getIconHeight() : delegate.getIconWidth();
		}

		@Override public int getIconHeight() {
			double a = angle % Math.PI;
			if (a < 0) a += Math.PI;
			boolean swap = (a > Math.PI / 4 && a < 3 * Math.PI / 4);
			return swap ? delegate.getIconWidth() : delegate.getIconHeight();
		}

		@Override
		public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.translate(x + getIconWidth() / 2.0, y + getIconHeight() / 2.0);
			g2.rotate(angle);
			delegate.paintIcon(c, g2, -delegate.getIconWidth() / 2, -delegate.getIconHeight() / 2);
			g2.dispose();
		}
	}
}
