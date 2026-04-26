package me.justicepro.spigotgui.FileExplorer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.PatternSyntaxException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import me.justicepro.spigotgui.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.RUndoManager;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchContext;

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
		}
	}

	private JPanel contentPane;
	private boolean newFile = true;
	/** True when the document has been modified since last save/open. */
	private boolean dirty = false;

	private RSyntaxTextArea textArea;
	private RUndoManager undoManager;

	private File openedFile;
	private FindReplaceDialog findReplaceDialog;

	public FileEditor() {
		setTitle("New - File Editor");
		setResizable(true);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				if (!promptSaveIfDirty()) return;
				openEditors.remove(FileEditor.this);
				dispose();
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
				if (e.getKeyCode() == KeyEvent.VK_S && e.isControlDown()) {
					e.consume();
					try {
						saveFile();
						JOptionPane.showMessageDialog(FileEditor.this, "Saved File");
					} catch (IOException e1) {
						e1.printStackTrace();
						JOptionPane.showMessageDialog(FileEditor.this, "Save failed: " + e1.getMessage());
					}
				}
			}
		});

		openEditors.add(this);

		RTextScrollPane scrollPane = new RTextScrollPane(textArea);
		scrollPane.setLineNumbersEnabled(true);
		scrollPane.setFoldIndicatorEnabled(true);
		contentPane.add(scrollPane, BorderLayout.CENTER);
		// Defer scheme application until after setVisible(true) so Swing's first
		// updateUI() pass (which resets the Gutter background to the L&F default)
		// has already completed before we paint our custom gutter colors over it.
		SwingUtilities.invokeLater(() -> EditorSchemeApplier.apply(textArea, currentAppTheme));

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmNew = new JMenuItem("New");
		mntmNew.addActionListener(e -> doNew());
		mnFile.add(mntmNew);

		JMenuItem mntmOpen = new JMenuItem("Open");
		mntmOpen.addActionListener(e -> doOpen());
		mnFile.add(mntmOpen);

		JMenuItem mntmSave = new JMenuItem("Save");
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

		JMenu mnEdit = new JMenu("Edit");
		menuBar.add(mnEdit);

		JMenuItem mntmUndo = new JMenuItem("Undo");
		mntmUndo.addActionListener(e -> {
			if (undoManager.canUndo()) undoManager.undo();
		});
		mnEdit.add(mntmUndo);

		JMenuItem mntmRedo = new JMenuItem("Redo");
		mntmRedo.addActionListener(e -> {
			if (undoManager.canRedo()) undoManager.redo();
		});
		mnEdit.add(mntmRedo);

		mnEdit.addSeparator();

		JMenuItem mntmCut = new JMenuItem("Cut");
		mntmCut.addActionListener(e -> textArea.cut());
		mnEdit.add(mntmCut);

		JMenuItem mntmCopy = new JMenuItem("Copy");
		mntmCopy.addActionListener(e -> textArea.copy());
		mnEdit.add(mntmCopy);

		JMenuItem mntmPaste = new JMenuItem("Paste");
		mntmPaste.addActionListener(e -> textArea.paste());
		mnEdit.add(mntmPaste);

		mnEdit.addSeparator();

		JMenuItem mntmFind = new JMenuItem("Find...");
		mntmFind.addActionListener(e -> showFindReplace(false));
		mnEdit.add(mntmFind);

		JMenuItem mntmReplace = new JMenuItem("Replace...");
		mntmReplace.addActionListener(e -> showFindReplace(true));
		mnEdit.add(mntmReplace);

		// Update undo/redo enabled state when edits occur
		javax.swing.Timer timer = new javax.swing.Timer(200, ev -> {
			mntmUndo.setEnabled(undoManager.canUndo());
			mntmRedo.setEnabled(undoManager.canRedo());
		});
		timer.setRepeats(true);
		timer.start();
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

	private void showFindReplace(boolean showReplace) {
		if (findReplaceDialog == null) {
			findReplaceDialog = new FindReplaceDialog(this, textArea);
		}
		findReplaceDialog.setReplaceVisible(showReplace);
		findReplaceDialog.setLocationRelativeTo(this);
		findReplaceDialog.setVisible(true);
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
			setTitle(file.getName() + " - File Editor");
		} else {
			Files.write(openedFile.toPath(), textArea.getText().getBytes(StandardCharsets.UTF_8));
			dirty = false;
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
		setTitle(file.getName() + " - File Editor");
	}

	/**
	 * Simple Find/Replace dialog using SearchEngine.
	 */
	private static class FindReplaceDialog extends JFrame {
		private final RSyntaxTextArea textArea;
		private final JTextField findField;
		private final JTextField replaceField;
		private final JCheckBox matchCaseCheckBox;
		private final JCheckBox regexCheckBox;
		private final JCheckBox wholeWordCheckBox;
		private final JPanel replacePanel;
		private SearchContext context = new SearchContext();

		FindReplaceDialog(JFrame parent, RSyntaxTextArea textArea) {
			this.textArea = textArea;
			setTitle("Find and Replace");
			setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
			setResizable(false);

			JPanel main = new JPanel();
			main.setBorder(new EmptyBorder(10, 10, 10, 10));
			main.setLayout(new BorderLayout(10, 10));

			JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT));
			fields.add(new JLabel("Find:"));
			findField = new JTextField(28);
			fields.add(findField);
			main.add(fields, BorderLayout.NORTH);

			replacePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			replacePanel.add(new JLabel("Replace:"));
			replaceField = new JTextField(28);
			replacePanel.add(replaceField);

			JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT));
			matchCaseCheckBox = new JCheckBox("Match case");
			wholeWordCheckBox = new JCheckBox("Whole word");
			regexCheckBox = new JCheckBox("Regex");
			options.add(matchCaseCheckBox);
			options.add(wholeWordCheckBox);
			options.add(regexCheckBox);

			JPanel center = new JPanel();
			center.setLayout(new GridLayout(3, 1, 0, 8));
			center.add(replacePanel);
			center.add(options);
			main.add(center, BorderLayout.CENTER);

			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			JButton findNext = new JButton("Find Next");
			findNext.addActionListener(e -> findNext(false));
			JButton findPrev = new JButton("Find Previous");
			findPrev.addActionListener(e -> findNext(true));
			JButton replace = new JButton("Replace");
			replace.addActionListener(e -> replaceOne());
			JButton replaceAll = new JButton("Replace All");
			replaceAll.addActionListener(e -> replaceAll());
			JButton closeBtn = new JButton("Close");
			closeBtn.addActionListener(e -> setVisible(false));
			buttons.add(findNext);
			buttons.add(findPrev);
			buttons.add(replace);
			buttons.add(replaceAll);
			buttons.add(closeBtn);
			main.add(buttons, BorderLayout.SOUTH);

			setContentPane(main);
			pack();
		}

		void setReplaceVisible(boolean visible) {
			replacePanel.setVisible(visible);
		}

		private void updateContext() {
			context.setSearchFor(findField.getText());
			context.setReplaceWith(replaceField.getText());
			context.setMatchCase(matchCaseCheckBox.isSelected());
			context.setWholeWord(wholeWordCheckBox.isSelected());
			context.setRegularExpression(regexCheckBox.isSelected());
			context.setSearchForward(true);
		}

		private void findNext(boolean backward) {
			updateContext();
			context.setSearchForward(!backward);
			if (context.getSearchFor().isEmpty()) return;
			try {
				boolean found = SearchEngine.find(textArea, context).wasFound();
				if (!found) {
					JOptionPane.showMessageDialog(this, "No more occurrences.");
				}
			} catch (PatternSyntaxException ex) {
				JOptionPane.showMessageDialog(this, "Invalid regex: " + ex.getMessage());
			}
		}

		private void replaceOne() {
			updateContext();
			if (context.getSearchFor().isEmpty()) return;
			try {
				boolean found = SearchEngine.replace(textArea, context).wasFound();
				if (!found) JOptionPane.showMessageDialog(this, "No occurrence to replace.");
			} catch (PatternSyntaxException ex) {
				JOptionPane.showMessageDialog(this, "Invalid regex: " + ex.getMessage());
			}
		}

		private void replaceAll() {
			updateContext();
			if (context.getSearchFor().isEmpty()) return;
			try {
				int count = SearchEngine.replaceAll(textArea, context).getMarkedCount();
				JOptionPane.showMessageDialog(this, "Replaced " + count + " occurrence(s).");
			} catch (PatternSyntaxException ex) {
				JOptionPane.showMessageDialog(this, "Invalid regex: " + ex.getMessage());
			}
		}
	}
}
