package me.justicepro.spigotgui.Core;

import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.GroupLayout;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.GroupLayout.Alignment;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.DefaultListCellRenderer;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import me.justicepro.spigotgui.FileExplorer.FileEditor;
import me.justicepro.spigotgui.FileExplorer.FileModel;

/**
 * Files tab: directory browser list, context menu (Open, Cut, Copy, Paste, Delete, Rename),
 * and buttons (New..., File Editor). Uses FileModel for navigation and operations.
 */
public class FilesPanel extends JPanel {

	private final FileModel fileModel;
	private int fileListHoverIndex = -1;

	public FilesPanel(SpigotGUI gui, File initialDirectory, boolean openInSystemDefault) {
		JList<String> fileList = new JList<>();
		fileModel = new FileModel(fileList);
		fileModel.setParentFrame(gui);
		fileModel.setOpenInSystemDefault(openInSystemDefault);
		fileList.setModel(fileModel);
		fileList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (!isSelected && index == fileListHoverIndex) {
					java.awt.Color bg = getBackground();
					setBackground(bg != null ? bg.darker() : java.awt.Color.LIGHT_GRAY);
				}
				String s = value == null ? "" : value.toString();
				Icon upDirIcon = UIManager.getIcon("FileChooser.upFolderIcon");
				Icon dirIcon = UIManager.getIcon("FileView.directoryIcon");
				Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
				if ("..".equals(s)) {
					setIcon(upDirIcon != null ? upDirIcon : dirIcon);
					setText(".. (up one level)");
				} else if (s.startsWith("/")) {
					setIcon(dirIcon);
					setText(s.substring(1));
				} else {
					setIcon(fileIcon);
					setText(s);
				}
				return this;
			}
		});
		fileList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			@Override
			public void mouseMoved(java.awt.event.MouseEvent e) {
				int i = fileList.locationToIndex(e.getPoint());
				if (i != fileListHoverIndex) {
					fileListHoverIndex = i;
					fileList.repaint();
				}
			}
		});
		fileList.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseExited(java.awt.event.MouseEvent e) {
				if (fileListHoverIndex != -1) {
					fileListHoverIndex = -1;
					fileList.repaint();
				}
			}
		});
		fileList.addMouseListener(fileModel.createMouseListener());
		fileList.addKeyListener(fileModel.createKeyListener());
		fileModel.loadDirectory(initialDirectory);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(fileList);

		JPopupMenu contextMenu = new JPopupMenu();
		JMenuItem mnuOpen = new JMenuItem("Open");
		mnuOpen.addActionListener(e -> fileModel.onFileRun());
		contextMenu.add(mnuOpen);
		contextMenu.addSeparator();
		JMenuItem mnuCut = new JMenuItem("Cut");
		mnuCut.addActionListener(e -> { if (fileModel.getSelectedFile() != null) fileModel.setClipboard(fileModel.getSelectedFile(), true); });
		contextMenu.add(mnuCut);
		JMenuItem mnuCopy = new JMenuItem("Copy");
		mnuCopy.addActionListener(e -> { if (fileModel.getSelectedFile() != null) fileModel.setClipboard(fileModel.getSelectedFile(), false); });
		contextMenu.add(mnuCopy);
		JMenuItem mnuPaste = new JMenuItem("Paste");
		mnuPaste.addActionListener(e -> fileModel.pasteFromClipboard());
		contextMenu.add(mnuPaste);
		contextMenu.addSeparator();
		JMenuItem mnuDelete = new JMenuItem("Delete");
		mnuDelete.addActionListener(e -> {
			File sel = fileModel.getSelectedFile();
			if (sel != null && JOptionPane.showConfirmDialog(gui, "Delete \"" + sel.getName() + "\"?", "Confirm Delete", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
				fileModel.deleteSelected();
		});
		contextMenu.add(mnuDelete);
		JMenuItem mnuRename = new JMenuItem("Rename");
		mnuRename.addActionListener(e -> {
			File sel = fileModel.getSelectedFile();
			if (sel != null) {
				String name = JOptionPane.showInputDialog(gui, "New name:", sel.getName());
				if (name != null) fileModel.renameSelected(name);
			}
		});
		contextMenu.add(mnuRename);
		contextMenu.addPopupMenuListener(new PopupMenuListener() {
			@Override public void popupMenuCanceled(PopupMenuEvent e) {}
			@Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
			@Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				boolean hasSel = fileModel.getSelectedFile() != null;
				mnuOpen.setEnabled(hasSel);
				mnuCut.setEnabled(hasSel);
				mnuCopy.setEnabled(hasSel);
				mnuDelete.setEnabled(hasSel);
				mnuRename.setEnabled(hasSel);
				mnuPaste.setEnabled(FileModel.getClipboardFile() != null);
			}
		});
		fileList.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger()) showContextMenu(e);
			}
			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger()) showContextMenu(e);
			}
			private void showContextMenu(java.awt.event.MouseEvent e) {
				int i = fileList.locationToIndex(e.getPoint());
				if (i >= 0) {
					fileList.setSelectedIndex(i);
					fileList.requestFocusInWindow();
				}
				contextMenu.show(e.getComponent(), e.getX(), e.getY());
			}
		});

		JButton btnNew = new JButton("New...");
		JPopupMenu newMenu = new JPopupMenu();
		JMenuItem mnuNewFile = new JMenuItem("New file");
		mnuNewFile.addActionListener(e -> {
			String name = JOptionPane.showInputDialog(gui, "File name:", "new file.txt");
			if (name != null) fileModel.createNewFile(name);
		});
		newMenu.add(mnuNewFile);
		JMenuItem mnuNewFolder = new JMenuItem("New folder");
		mnuNewFolder.addActionListener(e -> {
			String name = JOptionPane.showInputDialog(gui, "Folder name:", "new folder");
			if (name != null) fileModel.createNewFolder(name);
		});
		newMenu.add(mnuNewFolder);
		btnNew.addActionListener(e -> newMenu.show(btnNew, 0, btnNew.getHeight()));

		JButton btnFileEditor = new JButton("File Editor");
		btnFileEditor.addActionListener(e -> {
			FileEditor editor = new FileEditor();
			editor.setLocationRelativeTo(gui);
			editor.setVisible(true);
		});

		GroupLayout gl = new GroupLayout(this);
		setLayout(gl);
		gl.setHorizontalGroup(
			gl.createParallelGroup(Alignment.LEADING)
				.addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 747, Short.MAX_VALUE)
				.addGroup(gl.createSequentialGroup()
					.addComponent(btnNew)
					.addPreferredGap(ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
					.addComponent(btnFileEditor, GroupLayout.PREFERRED_SIZE, 86, GroupLayout.PREFERRED_SIZE)
					.addContainerGap())
		);
		gl.setVerticalGroup(
			gl.createParallelGroup(Alignment.LEADING)
				.addGroup(gl.createSequentialGroup()
					.addGap(6)
					.addGroup(gl.createParallelGroup(Alignment.BASELINE)
						.addComponent(btnNew)
						.addComponent(btnFileEditor))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 599, Short.MAX_VALUE))
		);
	}

	public FileModel getFileModel() {
		return fileModel;
	}

	/** Reload the current directory listing from disk. */
	public void refreshDirectoryListing() {
		if (fileModel.getCurrentDirectory() != null) {
			fileModel.refresh();
		}
	}

	/**
	 * Refresh the file list when the user switches to the Files tab or when the
	 * main window regains focus while the Files tab is selected.
	 */
	public void registerAutoRefreshOnFocus(JFrame frame, JTabbedPane tabbedPane) {
		int filesTabIndex = tabbedPane.indexOfTab("Files");
		if (filesTabIndex < 0) {
			return;
		}
		tabbedPane.addChangeListener(e -> {
			if (tabbedPane.getSelectedIndex() == filesTabIndex) {
				refreshDirectoryListing();
			}
		});
		frame.addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowGainedFocus(WindowEvent e) {
				if (tabbedPane.getSelectedIndex() == filesTabIndex) {
					refreshDirectoryListing();
				}
			}
		});
	}
}
