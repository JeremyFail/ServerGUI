package me.justicepro.spigotgui.FileExplorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.RUndoManager;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import me.justicepro.spigotgui.Theme;
import me.justicepro.spigotgui.Utils.AppIcons;

public class FileEditor extends JFrame {

	/** Open editor windows so we can apply scheme changes to all of them. */
	private static final List<FileEditor> openEditors = new CopyOnWriteArrayList<>();
	/** Currently active application theme; used to choose the editor color scheme. */
	private static Theme currentAppTheme = null;

	/**
	 * Set the application theme without immediately applying it (called at startup
	 * before any editors are open).
	 */
	public static void setAppTheme(Theme theme) {
		currentAppTheme = theme;
	}

	/**
	 * Apply the editor color scheme for the given theme to all currently open editors.
	 * Call this when the application theme changes live (same-family switch).
	 */
	public static void applyCurrentScheme(Theme theme) {
		currentAppTheme = theme;
		for (FileEditor editor : openEditors) {
			EditorSchemeApplier.apply(editor.textArea, theme);
			// After a L&F/theme switch, force the FindBar to re-layout so
			// button sizes are recalculated and text is not clipped.
			if (editor.findBar != null && editor.findBar.isVisible()) {
				SwingUtilities.updateComponentTreeUI(editor.findBar);
				editor.findBar.revalidate();
				editor.findBar.repaint();
				editor.positionFindBar();
			}
		}
	}

	private JPanel contentPane;
	private boolean newFile = true;
	/** True when the document has been modified since last save/open. */
	private boolean dirty = false;

	private RSyntaxTextArea textArea;
	private RUndoManager undoManager;
	private RTextScrollPane scrollPane;
	private JLayeredPane editorLayer;
	private FindBar findBar;

	private File openedFile;
	/** Last-known modification timestamp of the open file (ms). Used to detect external changes. */
	private long lastKnownModified = 0L;
	/** Guards against stacking multiple "file changed" dialogs at once. */
	private boolean checkingExternalChange = false;
	/** Polls for external file changes while this window has focus. */
	private final javax.swing.Timer fileWatchTimer = new javax.swing.Timer(3000, ev -> checkForExternalChanges());

	public FileEditor() {
		setIconImages(AppIcons.getIcons());
		setTitle("New - File Editor");
		setResizable(true);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (!promptSaveIfDirty()) return;
				openEditors.remove(FileEditor.this);
				dispose();
			}
		});
		addWindowFocusListener(new WindowFocusListener() {
			@Override
			public void windowGainedFocus(WindowEvent e) {
				checkForExternalChanges();
				fileWatchTimer.start();
			}
			@Override
			public void windowLostFocus(WindowEvent e) {
				fileWatchTimer.stop();
			}
		});
		setBounds(100, 100, 663, 567);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);

		textArea = new RSyntaxTextArea(20, 60);
		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
		textArea.setCodeFoldingEnabled(true);
		textArea.setAntiAliasingEnabled(true);

		undoManager = new RUndoManager(textArea);
		textArea.getDocument().addUndoableEditListener(undoManager);
		undoManager.discardAllEdits();
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) { dirty = true; }
			@Override
			public void removeUpdate(DocumentEvent e) { dirty = true; }
			@Override
			public void changedUpdate(DocumentEvent e) { dirty = true; }
		});

		textArea.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.isControlDown()) {
					switch (e.getKeyCode()) {
						case KeyEvent.VK_S:
							if (e.isShiftDown()) break; // let Ctrl+Shift+S reach the menu accelerator
							e.consume();
							try {
								saveFile();
								JOptionPane.showMessageDialog(FileEditor.this, "Saved File");
							} catch (IOException e1) {
								e1.printStackTrace();
								JOptionPane.showMessageDialog(FileEditor.this, "Save failed: " + e1.getMessage());
							}
							break;
						case KeyEvent.VK_F:
							e.consume();
							showFindBar(false);
							break;
						case KeyEvent.VK_H:
							e.consume();
							showFindBar(true);
							break;
					}
				} else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					if (findBar != null && findBar.isVisible()) findBar.close();
				}
			}
		});

		openEditors.add(this);

		scrollPane = new RTextScrollPane(textArea);
		scrollPane.setLineNumbersEnabled(true);
		scrollPane.setFoldIndicatorEnabled(true);

		// JLayeredPane lets the FindBar float over the editor as a popover.
		editorLayer = new JLayeredPane() {
			@Override
			public boolean isOptimizedDrawingEnabled() { return false; }
		};
		editorLayer.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				scrollPane.setBounds(0, 0, editorLayer.getWidth(), editorLayer.getHeight());
				if (findBar != null && findBar.isVisible()) positionFindBar();
			}
		});
		editorLayer.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
		contentPane.add(editorLayer, BorderLayout.CENTER);
		// Defer scheme application until after setVisible(true) so Swing's first
		// updateUI() pass (which resets the Gutter background to the L&F default)
		// has already completed before we paint our custom gutter colors over it.
		SwingUtilities.invokeLater(() -> EditorSchemeApplier.apply(textArea, currentAppTheme));

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmNew = new JMenuItem("New");
		mntmNew.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
		mntmNew.addActionListener(e -> doNew());
		mnFile.add(mntmNew);

		JMenuItem mntmNewWindow = new JMenuItem("New Window");
		mntmNewWindow.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
		mntmNewWindow.addActionListener(e -> doNewWindow());
		mnFile.add(mntmNewWindow);
		
		mnFile.addSeparator();

		JMenuItem mntmOpen = new JMenuItem("Open");
		mntmOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
		mntmOpen.addActionListener(e -> doOpen());
		mnFile.add(mntmOpen);

		JMenuItem mntmSave = new JMenuItem("Save");
		mntmSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
		mntmSave.addActionListener(e -> {
			try {
				saveFile();
				dirty = false;
				JOptionPane.showMessageDialog(FileEditor.this, "Saved File");
			} catch (IOException e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(FileEditor.this, "Save failed: " + e1.getMessage());
			}
		});
		mnFile.add(mntmSave);

		JMenuItem mntmSaveAs = new JMenuItem("Save As...");
		mntmSaveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
		mntmSaveAs.addActionListener(e -> doSaveAs());
		mnFile.add(mntmSaveAs);

		mnFile.addSeparator();

		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addActionListener(e -> {
			if (!promptSaveIfDirty()) return;
			openEditors.remove(FileEditor.this);
			dispose();
		});
		mnFile.add(mntmExit);

		JMenu mnEdit = new JMenu("Edit");
		menuBar.add(mnEdit);

		JMenuItem mntmUndo = new JMenuItem("Undo");
		mntmUndo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK));
		mntmUndo.addActionListener(e -> {
			if (undoManager.canUndo()) undoManager.undo();
		});
		mnEdit.add(mntmUndo);

		JMenuItem mntmRedo = new JMenuItem("Redo");
		mntmRedo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK));
		mntmRedo.addActionListener(e -> {
			if (undoManager.canRedo()) undoManager.redo();
		});
		mnEdit.add(mntmRedo);

		mnEdit.addSeparator();

		JMenuItem mntmCut = new JMenuItem("Cut");
		mntmCut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK));
		mntmCut.addActionListener(e -> textArea.cut());
		mnEdit.add(mntmCut);

		JMenuItem mntmCopy = new JMenuItem("Copy");
		mntmCopy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK));
		mntmCopy.addActionListener(e -> textArea.copy());
		mnEdit.add(mntmCopy);

		JMenuItem mntmPaste = new JMenuItem("Paste");
		mntmPaste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK));
		mntmPaste.addActionListener(e -> textArea.paste());
		mnEdit.add(mntmPaste);

		mnEdit.addSeparator();

		JMenuItem mntmFind = new JMenuItem("Find");
		mntmFind.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK));
		mntmFind.addActionListener(e -> showFindBar(false));
		mnEdit.add(mntmFind);

		JMenuItem mntmReplace = new JMenuItem("Replace");
		mntmReplace.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_DOWN_MASK));
		mntmReplace.addActionListener(e -> showFindBar(true));
		mnEdit.add(mntmReplace);

		// Update undo/redo enabled state when edits occur
		javax.swing.Timer timer = new javax.swing.Timer(200, ev -> {
			mntmUndo.setEnabled(undoManager.canUndo());
			mntmRedo.setEnabled(undoManager.canRedo());
		});
		timer.setRepeats(true);
		timer.start();

		// Periodic fallback: detect external file changes while the window has focus.
		// Timer starts/stops with window focus via the WindowFocusListener above.
		fileWatchTimer.setRepeats(true);
	}

	/**
	 * Checks whether the open file has been modified on disk since it was last read or
	 * written by this editor. If so, prompts the user to reload or ignore.
	 */
	private void checkForExternalChanges() {
		if (newFile || openedFile == null || checkingExternalChange) return;
		if (!openedFile.exists()) return;
		long currentModified = openedFile.lastModified();
		if (currentModified == 0 || currentModified == lastKnownModified) return;
		checkingExternalChange = true;
		try {
			String msg = "\"" + openedFile.getName() + "\" has been modified by an external program.\n"
				+ "Would you like to reload it with the new changes?";
			int choice = JOptionPane.showOptionDialog(this, msg, "File Modified Externally",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
					new String[]{ "Reload", "Ignore" }, "Reload");
			if (choice == JOptionPane.YES_OPTION) {
				try {
					int caretPos = textArea.getCaretPosition();
					textArea.setText(new String(Files.readAllBytes(openedFile.toPath()), StandardCharsets.UTF_8));
					undoManager.discardAllEdits();
					dirty = false;
					int docLen = textArea.getDocument().getLength();
					textArea.setCaretPosition(Math.min(caretPos, docLen));
				} catch (IOException ex) {
					JOptionPane.showMessageDialog(this, "Reload failed: " + ex.getMessage());
				}
			}
			// Update the stored timestamp so we don't prompt again for this same change.
			lastKnownModified = openedFile.lastModified();
		} finally {
			checkingExternalChange = false;
		}
	}

	/**
	 * If dirty, prompt to save. Returns true if we can proceed (saved, don't save, or not dirty), false if user cancelled.
	 */
	private boolean promptSaveIfDirty() {
		if (!dirty) return true;
		String msg = openedFile != null ? "Save changes to \"" + openedFile.getName() + "\"?" : "Save changes to unsaved file?";
		int choice = JOptionPane.showOptionDialog(this, msg, "Unsaved changes",
			JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
			new String[] { "Save", "Don't Save", "Cancel" }, "Save");
		if (choice == JOptionPane.CANCEL_OPTION || choice == -1) return false;
		if (choice == JOptionPane.NO_OPTION) return true; // Don't Save
		try {
			saveFile();
			dirty = false;
			return true;
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
			return false;
		}
	}

	private void doNew() {
		if (!promptSaveIfDirty()) return;
		newFile = true;
		openedFile = null;
		textArea.setText("");
		undoManager.discardAllEdits();
		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
		dirty = false;
		setTitle("New - File Editor");
	}

	private void doOpen() {
		if (!promptSaveIfDirty()) return;
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(FileEditor.this) != JFileChooser.APPROVE_OPTION) return;
		try {
			openFile(chooser.getSelectedFile());
			dirty = false;
		} catch (IOException e1) {
			e1.printStackTrace();
			JOptionPane.showMessageDialog(FileEditor.this, "Open failed: " + e1.getMessage());
		}
	}

	private void doSaveAs() {
		JFileChooser chooser = new JFileChooser();
		if (openedFile != null) chooser.setSelectedFile(openedFile);
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File file = chooser.getSelectedFile();
		try {
			Files.write(file.toPath(), textArea.getText().getBytes(StandardCharsets.UTF_8));
			newFile = false;
			openedFile = file;
			setSyntaxStyleFromFile(file);
			dirty = false;
			lastKnownModified = file.lastModified();
			setTitle(file.getName() + " - File Editor");
			JOptionPane.showMessageDialog(this, "Saved File");
		} catch (IOException e1) {
			e1.printStackTrace();
			JOptionPane.showMessageDialog(this, "Save failed: " + e1.getMessage());
		}
	}

	private void doNewWindow() {
		FileEditor newEditor = new FileEditor();
		newEditor.setLocation(getX() + 30, getY() + 30);
		newEditor.setVisible(true);
	}

	private void showFindBar(boolean withReplace) {
		boolean isNew = (findBar == null);
		if (isNew) {
			findBar = new FindBar();
			editorLayer.add(findBar, JLayeredPane.PALETTE_LAYER);
		}
		if (withReplace) {
			// Ctrl+H / Replace menu: ensure replace row is open
			if (!findBar.replaceVisible) findBar.setReplaceVisible(true);
		} else {
			// Ctrl+F / Find menu: always start in find-only mode
			if (findBar.replaceVisible) findBar.setReplaceVisible(false);
		}
		positionFindBar();
		findBar.setVisible(true);
		// Pre-populate from selection only when the text area is the focus owner
		// (not when the find bar is already active)
		if (textArea.isFocusOwner()) {
			String sel = textArea.getSelectedText();
			if (sel != null && !sel.isEmpty()) {
				findBar.setFindText(sel);
			}
		}
		findBar.focusFindField();
	}

	private void positionFindBar() {
		Dimension pref = findBar.getPreferredSize();
		int barX = Math.max(0, editorLayer.getWidth() - pref.width - 10);
		findBar.setBounds(barX, 10, pref.width, pref.height);
		findBar.revalidate();
	}

	public void applyEditorTheme(String themeName) {
		EditorSchemeApplier.apply(textArea, currentAppTheme);
	}

	private void setSyntaxStyleFromFile(File file) {
		String name = file.getName();
		int dot = name.lastIndexOf('.');
		String ext = (dot > 0) ? name.substring(dot + 1).toLowerCase() : "";
		switch (ext) {
			case "java":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
				break;
			case "json":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
				break;
			case "properties":
				textArea.setSyntaxEditingStyle("text/properties");
				break;
			case "xml":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
				break;
			case "html":
			case "htm":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML);
				break;
			case "yml":
			case "yaml":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_YAML);
				break;
			case "css":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSS);
				break;
			case "js":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
				break;
			case "php":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PHP);
				break;
			case "sql":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
				break;
			case "csv":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSV);
				break;
			case "md":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
				break;
			case "bat":
			case "cmd":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
				break;
			case "sh":
			case "bash":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
				break;
			case "ini":
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_INI);
				break;
			default:
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
				break;
		}
	}

	public void saveFile() throws IOException {
		if (newFile) {
			JFileChooser chooser = new JFileChooser();
			if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
			File file = chooser.getSelectedFile();
			Files.write(file.toPath(), textArea.getText().getBytes(StandardCharsets.UTF_8));
			newFile = false;
			openedFile = file;
			setSyntaxStyleFromFile(file);
			dirty = false;
			lastKnownModified = file.lastModified();
			setTitle(file.getName() + " - File Editor");
		} else {
			Files.write(openedFile.toPath(), textArea.getText().getBytes(StandardCharsets.UTF_8));
			dirty = false;
			lastKnownModified = openedFile.lastModified();
			setTitle(openedFile.getName() + " - File Editor");
		}
	}

	public void openFile(File file) throws IOException {
		if (!file.exists()) {
			Files.write(file.toPath(), new byte[0]);
			setTitle(file.getName() + " - File Editor");
		}
		newFile = false;
		openedFile = file;
		textArea.setText(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		undoManager.discardAllEdits();
		setSyntaxStyleFromFile(file);
		dirty = false;
		lastKnownModified = file.lastModified();
		setTitle(file.getName() + " - File Editor");
	}

	// =========================================================================
	// VS Code-style inline Find/Replace bar
	// =========================================================================

	/**
	 * A compact panel that floats in the top-right corner of the editor, mimicking
	 * the VS Code find/replace widget.
	 *
	 * Find row:    [▶] [find field] [Aa][W][.*][≡]  [X of Y]  [↑][↓]  [×]
	 * Replace row: [indent] [replace field] [Replace] [Replace All]
	 */
	private class FindBar extends JPanel {

		// --- Controls ---
		private final JButton       expandBtn;                    // ▶/▼ toggle replace row
		private final JTextField    findField    = new JTextField();
		private final JToggleButton matchCaseBtn;                 // Aa
		private final JToggleButton wholeWordBtn;                 // W
		private final JToggleButton regexBtn;                     // .*
		private final JLabel        resultLabel  = new JLabel("", JLabel.LEFT);
		private final JButton       prevBtn;                      // ↑
		private final JButton       nextBtn;                      // ↓
		private final JButton       closeBtn;                     // ×

		private final JPanel     replaceRow;
		private final JTextField replaceField = new JTextField();
		private final JButton    replaceBtn;
		private final JButton    replaceAllBtn;

		// --- Expand icons (right = collapsed, down = expanded) ---
		private Icon expandIconRight;
		private Icon expandIconDown;

		// --- State ---
		boolean replaceVisible = false;
		private SearchContext context      = new SearchContext();
		private int           currentMatch = 0;
		private int           totalMatches = 0;

		FindBar() {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(true);

			Color bg     = UIManager.getColor("Panel.background");
			Color border = UIManager.getColor("controlShadow");
			if (border == null) border = Color.GRAY;
			// Blend the border 75% toward the background for a subtle, dark-theme-friendly line
			Color bg2 = bg != null ? bg : new Color(0xF3F3F3);
			Color mutedBorder = new Color(
					(border.getRed()   + 3 * bg2.getRed())   / 4,
					(border.getGreen() + 3 * bg2.getGreen()) / 4,
					(border.getBlue()  + 3 * bg2.getBlue())  / 4);
			setBackground(bg2);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(mutedBorder, 1),
					new EmptyBorder(3, 4, 3, 4)));

			// Set mark-all highlight colour: amber on dark themes, bright yellow on light
			double lum = (0.299 * bg2.getRed() + 0.587 * bg2.getGreen() + 0.114 * bg2.getBlue()) / 255.0;
			textArea.setMarkAllHighlightColor(lum < 0.4 ? new Color(0xB8860B) : new Color(0xFFFF00));

			// Expand/collapse chevron: use Menu.arrowIcon (a right-facing triangle in FlatLaf)
			Icon menuArrow = UIManager.getIcon("Menu.arrowIcon");
			if (menuArrow == null) menuArrow = UIManager.getIcon("Tree.collapsedIcon");
			expandIconRight = menuArrow != null ? menuArrow : null;
			expandIconDown  = menuArrow != null ? new RotatedIcon(menuArrow, Math.PI / 2) : null;

			// ---- Find row --------------------------------------------------------
			JPanel findRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
			findRow.setOpaque(false);

			expandBtn = mkGhostBtn("Toggle Replace (Ctrl+H)");
			expandBtn.setIcon(expandIconRight);
			// Match the foreground colour used by other buttons in the bar
			Color btnFg = UIManager.getColor("Button.foreground");
			if (btnFg == null) btnFg = UIManager.getColor("Label.foreground");
			if (btnFg != null) expandBtn.setForeground(btnFg);
			expandBtn.addActionListener(e -> setReplaceVisible(!replaceVisible));
			findRow.add(expandBtn);

			findField.setPreferredSize(new Dimension(170, 22));
			findRow.add(findField);

			matchCaseBtn = mkToggle("Aa", "Match Case");
			wholeWordBtn = mkToggle("W",  "Match Whole Word");
			regexBtn     = mkToggle(".*", "Use Regular Expression");
			findRow.add(matchCaseBtn);
			findRow.add(wholeWordBtn);
			findRow.add(regexBtn);

			resultLabel.setPreferredSize(new Dimension(72, 20));
			resultLabel.setFont(resultLabel.getFont().deriveFont(Font.PLAIN, 11f));
			findRow.add(resultLabel);

			prevBtn  = mkArrowBtn(true,  "Previous Match (Shift+Enter)");
			nextBtn  = mkArrowBtn(false, "Next Match (Enter)");
			closeBtn = mkCloseBtn();
			prevBtn.addActionListener(e  -> navigate(true));
			nextBtn.addActionListener(e  -> navigate(false));
			closeBtn.addActionListener(e -> close());
			findRow.add(prevBtn);
			findRow.add(nextBtn);
			findRow.add(closeBtn);

			add(findRow);

			// ---- Replace row (hidden by default) ---------------------------------
			replaceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
			replaceRow.setOpaque(false);
			// Indent to align the replace field with the find field
			replaceRow.add(Box.createHorizontalStrut(expandBtn.getPreferredSize().width));
			replaceField.setPreferredSize(new Dimension(170, 22));
			replaceRow.add(replaceField);
			replaceBtn    = mkTextBtn("Replace");
			replaceAllBtn = mkTextBtn("Replace All");
			replaceBtn.addActionListener(e    -> replaceOne());
			replaceAllBtn.addActionListener(e -> replaceAll());
			replaceRow.add(replaceBtn);
			replaceRow.add(replaceAllBtn);
			replaceRow.setVisible(false);
			add(replaceRow);

			// ---- Listeners -------------------------------------------------------
			findField.getDocument().addDocumentListener(new DocumentListener() {
				@Override public void insertUpdate(DocumentEvent e)  { onFindTextChanged(); }
				@Override public void removeUpdate(DocumentEvent e)  { onFindTextChanged(); }
				@Override public void changedUpdate(DocumentEvent e) { }
			});
			findField.addKeyListener(new KeyAdapter() {
				@Override public void keyPressed(KeyEvent e) {
					if      (e.getKeyCode() == KeyEvent.VK_ENTER)  navigate(e.isShiftDown());
					else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) close();
				}
			});
			replaceField.addKeyListener(new KeyAdapter() {
				@Override public void keyPressed(KeyEvent e) {
					if      (e.getKeyCode() == KeyEvent.VK_ENTER)  replaceOne();
					else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) close();
				}
			});
			matchCaseBtn.addActionListener(e -> onFindTextChanged());
			wholeWordBtn.addActionListener(e -> onFindTextChanged());
			regexBtn.addActionListener(e     -> onFindTextChanged());

			// ---- Tab/Shift-Tab cycle (installed after all controls exist) --------
			installTabCycle();
		}

		// --- Public API ----------------------------------------------------------

		/**
		 * Installs Tab / Shift-Tab traversal on every focusable control so the user
		 * can cycle through the bar without touching the text area.
		 *
		 * Full cycle (replace-visible):
		 *   findField → replaceField → matchCaseBtn → wholeWordBtn → regexBtn
		 *   → replaceBtn → replaceAllBtn → prevBtn → nextBtn → closeBtn → findField
		 *
		 * When replace is hidden the replaceField / replaceBtn / replaceAllBtn are
		 * skipped dynamically at traversal time.
		 */
		private void installTabCycle() {
			// Full ordered list; replace-only items are filtered at runtime.
			javax.swing.JComponent[] all = {
				expandBtn, findField, replaceField,
				matchCaseBtn, wholeWordBtn, regexBtn,
				replaceBtn, replaceAllBtn,
				prevBtn, nextBtn, closeBtn
			};
			// Disable default Swing focus traversal on every control so Tab
			// doesn't escape to the editor or other windows.
			for (javax.swing.JComponent c : all) {
				c.setFocusTraversalKeysEnabled(false);
			}
			KeyAdapter tabListener = new KeyAdapter() {
				@Override public void keyPressed(KeyEvent e) {
					// Ctrl+H inside bar: switch to replace mode
					if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_H) {
						e.consume();
						if (!replaceVisible) setReplaceVisible(true);
						return;
					}
					// Ctrl+F inside bar: switch to find-only mode
					if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F) {
						e.consume();
						if (replaceVisible) setReplaceVisible(false);
						return;
					}
					if (e.getKeyCode() != KeyEvent.VK_TAB) return;
					e.consume();
					// Build visible cycle on the fly
					java.util.List<javax.swing.JComponent> cycle = new java.util.ArrayList<>();
					cycle.add(expandBtn);
					cycle.add(findField);
					if (replaceVisible) cycle.add(replaceField);
					cycle.add(matchCaseBtn);
					cycle.add(wholeWordBtn);
					cycle.add(regexBtn);
					if (replaceVisible) { cycle.add(replaceBtn); cycle.add(replaceAllBtn); }
					cycle.add(prevBtn);
					cycle.add(nextBtn);
					cycle.add(closeBtn);

					javax.swing.JComponent src = (javax.swing.JComponent) e.getSource();
					int idx = cycle.indexOf(src);
					if (idx < 0) idx = 0;
					int next = e.isShiftDown()
							? (idx - 1 + cycle.size()) % cycle.size()
							: (idx + 1) % cycle.size();
					cycle.get(next).requestFocusInWindow();
				}
			};
			for (javax.swing.JComponent c : all) {
				c.addKeyListener(tabListener);
			}
		}

		void setReplaceVisible(boolean visible) {
			replaceVisible = visible;
			replaceRow.setVisible(visible);
			expandBtn.setIcon(visible ? expandIconDown : expandIconRight);
			revalidate();
			positionFindBar(); // height changed
		}

		void focusFindField() {
			findField.requestFocusInWindow();
			findField.selectAll();
		}

		void setFindText(String text) {
			findField.setText(text);
		}

		void close() {
			setVisible(false);
			// Clear mark-all highlights
			SearchEngine.markAll(textArea, new SearchContext());
			textArea.requestFocusInWindow();
		}

		// --- Internal logic -------------------------------------------------------

		/** Called whenever the find text or any search option changes. */
		private void onFindTextChanged() {
			currentMatch = 0;
			updateCount();
			if (totalMatches > 0) {
				// Navigate forward from current caret to the nearest match
				buildContext(true);
				try {
					SearchEngine.find(textArea, context);
					refreshCurrentIndex();
				} catch (PatternSyntaxException ex) {
					// already reflected in resultLabel by updateCount()
				}
			}
		}

		/**
		 * Mark all occurrences and update totalMatches + resultLabel.
		 */
		private void updateCount() {
			String searchFor = findField.getText();
			if (searchFor.isEmpty()) {
				totalMatches = 0;
				currentMatch = 0;
				resultLabel.setText("");
				resultLabel.setForeground(UIManager.getColor("Label.foreground"));
				SearchEngine.markAll(textArea, new SearchContext()); // clear highlights
				return;
			}
			try {
				SearchResult result = SearchEngine.markAll(textArea, buildContextCopy());
				totalMatches = result.getMarkedCount();
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
			}
		}

		/**
		 * After navigation, inspect the caret/selection position to determine which
		 * numbered match we are on, then update the result label.
		 */
		private void refreshCurrentIndex() {
			String searchFor = findField.getText();
			if (searchFor.isEmpty() || totalMatches == 0) return;
			int caretPos = textArea.getSelectionStart();
			String text  = textArea.getText();
			try {
				Pattern pat = buildPattern(searchFor);
				Matcher m = pat.matcher(text);
				int idx = 0;
				while (m.find()) {
					idx++;
					if (m.end() >= caretPos) {
						currentMatch = idx;
						resultLabel.setText(currentMatch + " of " + totalMatches);
						resultLabel.setForeground(UIManager.getColor("Label.foreground"));
						return;
					}
				}
				// Wrapped – last visible match
				currentMatch = totalMatches;
				resultLabel.setText(currentMatch + " of " + totalMatches);
			} catch (PatternSyntaxException ex) {
				// ignore – already reported
			}
		}

		private void navigate(boolean backward) {
			buildContext(!backward);
			if (context.getSearchFor().isEmpty()) return;
			try {
				SearchResult result = SearchEngine.find(textArea, context);
				if (result.wasFound() && totalMatches > 0) {
					if (backward) currentMatch = (currentMatch <= 1) ? totalMatches : currentMatch - 1;
					else          currentMatch = (currentMatch >= totalMatches) ? 1 : currentMatch + 1;
					resultLabel.setText(currentMatch + " of " + totalMatches);
					resultLabel.setForeground(UIManager.getColor("Label.foreground"));
				}
			} catch (PatternSyntaxException ex) {
				resultLabel.setText("Invalid regex");
				resultLabel.setForeground(new Color(0xCC3333));
			}
		}

		private void replaceOne() {
			buildContext(true);
			if (context.getSearchFor().isEmpty()) return;
			try {
				SearchEngine.replace(textArea, context);
				updateCount();
				refreshCurrentIndex();
			} catch (PatternSyntaxException ex) {
				resultLabel.setText("Invalid regex");
				resultLabel.setForeground(new Color(0xCC3333));
			}
		}

		private void replaceAll() {
			buildContext(true);
			if (context.getSearchFor().isEmpty()) return;
			try {
				SearchResult result = SearchEngine.replaceAll(textArea, context);
				int count = result.getMarkedCount();
				totalMatches = 0;
				currentMatch = 0;
				resultLabel.setText("Replaced " + count);
				resultLabel.setForeground(UIManager.getColor("Label.foreground"));
				// Refresh the count after the document settles
				SwingUtilities.invokeLater(() -> { currentMatch = 0; updateCount(); });
			} catch (PatternSyntaxException ex) {
				resultLabel.setText("Invalid regex");
				resultLabel.setForeground(new Color(0xCC3333));
			}
		}

		// --- Context / pattern builders ------------------------------------------

		private void buildContext(boolean forward) {
			context.setSearchFor(findField.getText());
			context.setReplaceWith(replaceField.getText());
			context.setMatchCase(matchCaseBtn.isSelected());
			context.setWholeWord(wholeWordBtn.isSelected());
			context.setRegularExpression(regexBtn.isSelected());
			context.setSearchForward(forward);
			context.setSearchWrap(true);
		}

		/** Builds a context copy suitable for markAll (no direction/wrap needed). */
		private SearchContext buildContextCopy() {
			SearchContext c = new SearchContext();
			c.setSearchFor(findField.getText());
			c.setMatchCase(matchCaseBtn.isSelected());
			c.setWholeWord(wholeWordBtn.isSelected());
			c.setRegularExpression(regexBtn.isSelected());
			return c;
		}

		/** Builds a java.util.regex.Pattern reflecting the current search options. */
		private Pattern buildPattern(String searchFor) {
			int flags = Pattern.DOTALL;
			if (!matchCaseBtn.isSelected()) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
			String pat = regexBtn.isSelected() ? searchFor : Pattern.quote(searchFor);
			if (wholeWordBtn.isSelected()) pat = "\\b(?:" + pat + ")\\b";
			return Pattern.compile(pat, flags);
		}

		// --- Widget factories ----------------------------------------------------

		private JToggleButton mkToggle(String text, String tooltip) {
			JToggleButton b = new JToggleButton(text);
			b.setToolTipText(tooltip);
			b.setMargin(new Insets(1, 4, 1, 4));
			b.setFont(b.getFont().deriveFont(Font.BOLD, 11f));
			b.setPreferredSize(new Dimension(30, 22));
			return b;
		}

		/** Ghost-style expand button: no border/fill by default; subtle hover fill; icon-based. */
		private JButton mkGhostBtn(String tooltip) {
			JButton b = new JButton() {
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					if (isFocusOwner()) {
						Graphics2D g2 = (Graphics2D) g.create();
						g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						Color fc = UIManager.getColor("Component.focusColor");
						if (fc == null) fc = new Color(0x4D90FE);
						g2.setColor(fc);
						g2.setStroke(new java.awt.BasicStroke(1.0f));
						g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 4, 4);
						g2.dispose();
					}
				}
			};
			b.addFocusListener(new java.awt.event.FocusAdapter() {
				@Override public void focusGained(java.awt.event.FocusEvent e) { b.repaint(); }
				@Override public void focusLost(java.awt.event.FocusEvent e)   { b.repaint(); }
			});
			b.setText("");
			b.setToolTipText(tooltip);
			b.setFocusPainted(false);
			b.setBorderPainted(false);
			b.setContentAreaFilled(false);
			b.setOpaque(false);
			b.setMargin(new Insets(1, 2, 1, 2));
			b.setPreferredSize(new Dimension(22, 22));
			b.addMouseListener(new MouseAdapter() {
				@Override public void mouseEntered(MouseEvent e) {
					Color base = UIManager.getColor("Panel.background");
					if (base == null) base = new Color(0xF3F3F3);
					// Lighten on dark themes, darken on light themes
					double l = (0.299 * base.getRed() + 0.587 * base.getGreen() + 0.114 * base.getBlue()) / 255.0;
					int delta = l < 0.4 ? 20 : -25;
					int r  = Math.min(255, Math.max(0, base.getRed()   + delta));
					int g  = Math.min(255, Math.max(0, base.getGreen() + delta));
					int bv = Math.min(255, Math.max(0, base.getBlue()  + delta));
					b.setBackground(new Color(r, g, bv));
					b.setContentAreaFilled(true);
					b.setOpaque(true);
				}
				@Override public void mouseExited(MouseEvent e) {
					b.setContentAreaFilled(false);
					b.setOpaque(false);
				}
			});
			return b;
		}

		/**
		 * Navigation button using arrow.svg. The SVG arrow faces left;
		 * rotate +90 degrees for previous (up) and -90 degrees for next (down).
		 */
		private JButton mkArrowBtn(boolean upward, String tooltip) {
			FlatSVGIcon arrowSvg = new FlatSVGIcon("svg/arrow.svg", 14, 14);
			JButton b = new JButton(new RotatedIcon(arrowSvg, upward ? Math.PI / 2 : -Math.PI / 2));
			b.setText("");
			b.setToolTipText(tooltip);
			b.setMargin(new Insets(1, 4, 1, 4));
			b.setPreferredSize(new Dimension(26, 22));
			return b;
		}

		/**
		 * Close button using DesktopIcon.closeIcon (falls back to InternalFrame.closeIcon,
		 * then plain × text). Uses a normal JButton so the L&F's standard hover applies
		 * instead of the title-bar red hover.
		 */
		private JButton mkCloseBtn() {
			FlatSVGIcon closeIcon = new FlatSVGIcon("svg/close.svg", 18, 18);
			// Force the icon stroke to match the theme foreground (close.svg uses a
			// non-standard stroke color + opacity that FlatLaf won't auto-remap).
			closeIcon.setColorFilter(new FlatSVGIcon.ColorFilter(
					c -> UIManager.getColor("Button.foreground")));
			JButton b = new JButton(closeIcon);
			b.setText("");
			b.setToolTipText("Close (Escape)");
			b.setMargin(new Insets(1, 4, 1, 4));
			b.setPreferredSize(new Dimension(26, 22));
			return b;
		}

		private JButton mkTextBtn(String text) {
			// Override getPreferredSize() so the width is always recalculated from the
			// current UI delegate/font rather than being locked in at construction time.
			// This prevents text truncation after a theme switch.
			JButton b = new JButton(text) {
				@Override
				public Dimension getPreferredSize() {
					Dimension d = super.getPreferredSize();
					return new Dimension(d.width, 22);
				}
			};
			b.setMargin(new Insets(0, 6, 0, 6));
			b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
			return b;
		}
	}

	// =========================================================================
	// RotatedIcon – wraps any Icon and paints it at a given rotation angle
	// =========================================================================

	private static class RotatedIcon implements Icon {
		private final Icon   delegate;
		private final double angle; // radians

		RotatedIcon(Icon delegate, double angle) {
			this.delegate = delegate;
			this.angle    = angle;
		}

		@Override public int getIconWidth() {
			// Only swap dimensions for odd multiples of 90 degrees (±90 degrees, ±270 degrees, etc.)
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
