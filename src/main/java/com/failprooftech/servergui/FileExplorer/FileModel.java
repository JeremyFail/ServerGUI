package com.failprooftech.servergui.FileExplorer;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;

import java.awt.Component;
import java.awt.Desktop;
import java.nio.file.StandardCopyOption;

public class FileModel extends DefaultListModel<String> {

	private JList<String> list;
	private File dir;
	private static File clipboardFile;
	private static boolean clipboardCut;
	/** When true, open files in the system default application instead of the built-in editor. */
	private boolean openInSystemDefault = false;
	/** Optional parent for dialogs and positioning child windows on the same monitor. */
	private Component parentFrame;

	public FileModel(JList<String> list) {
		this.list = list;
	}

	public void setParentFrame(Component parentFrame) {
		this.parentFrame = parentFrame;
	}

	public void setOpenInSystemDefault(boolean openInSystemDefault) {
		this.openInSystemDefault = openInSystemDefault;
	}
	
	public void loadDirectory(File dir) {
		this.dir = dir;
		clear();
		
		ArrayList<File> dirs = new ArrayList<>();
		ArrayList<File> fils = new ArrayList<>();
		
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				dirs.add(file);
			}else {
				fils.add(file);
			}
		}
		
		addElement("..");
		
		for (File d : dirs) {
			addElement("/" + d.getName());
		}
		
		for (File f : fils) {
			addElement("" + f.getName());
		}
		
	}
	
	public void refresh() {
		loadDirectory(dir);
	}

	/** Current directory shown in the list. */
	public File getCurrentDirectory() {
		return dir;
	}

	/** Returns the selected file or directory, or null if ".." or none selected. */
	public File getSelectedFile() {
		if (list.getSelectedIndex() == -1) return null;
		String item = getElementAt(list.getSelectedIndex());
		if (item.equalsIgnoreCase("..")) return null;
		String path = item.startsWith("/") ? dir.getAbsolutePath() + item : dir.getAbsolutePath() + "/" + item;
		return new File(path);
	}

	public void setClipboard(File file, boolean isCut) {
		clipboardFile = file;
		clipboardCut = isCut;
	}

	public static File getClipboardFile() { return clipboardFile; }
	public static boolean isClipboardCut() { return clipboardCut; }

	public void clearClipboard() {
		clipboardFile = null;
	}

	/** Create a new file in the current directory; returns true if created. */
	public boolean createNewFile(String name) {
		if (name == null || name.trim().isEmpty()) return false;
		File f = new File(dir, name.trim());
		try {
			if (f.createNewFile()) { refresh(); return true; }
		} catch (IOException e) {
			JOptionPane.showMessageDialog(parentFrame, "Could not create file: " + e.getMessage());
		}
		return false;
	}

	/** Create a new folder in the current directory; returns true if created. */
	public boolean createNewFolder(String name) {
		if (name == null || name.trim().isEmpty()) return false;
		File f = new File(dir, name.trim());
		if (f.mkdir()) { refresh(); return true; }
		JOptionPane.showMessageDialog(parentFrame, "Could not create folder.");
		return false;
	}

	/** Delete the selected file or empty directory; returns true if deleted. Confirmation is done by caller. */
	public boolean deleteSelected() {
		File f = getSelectedFile();
		if (f == null || !f.exists()) return false;
		try {
			if (f.isDirectory()) {
				File[] children = f.listFiles();
				if (children != null && children.length > 0) {
					JOptionPane.showMessageDialog(parentFrame, "Folder is not empty. Delete its contents first.");
					return false;
				}
			}
			Files.delete(f.toPath());
			refresh();
			return true;
		} catch (IOException e) {
			JOptionPane.showMessageDialog(parentFrame, "Could not delete: " + e.getMessage());
			return false;
		}
	}

	/** Rename the selected item; returns true if renamed. */
	public boolean renameSelected(String newName) {
		if (newName == null || newName.trim().isEmpty()) return false;
		File f = getSelectedFile();
		if (f == null || !f.exists()) return false;
		File dest = new File(f.getParentFile(), newName.trim());
		try {
			Files.move(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
			refresh();
			return true;
		} catch (IOException e) {
			JOptionPane.showMessageDialog(parentFrame, "Could not rename: " + e.getMessage());
			return false;
		}
	}

	/** Paste from clipboard (copy or move). */
	public void pasteFromClipboard() {
		if (clipboardFile == null || !clipboardFile.exists()) return;
		File dest = new File(dir, clipboardFile.getName());
		try {
			if (clipboardCut) {
				Files.move(clipboardFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
				clearClipboard();
			} else {
				if (clipboardFile.isDirectory()) copyDirectory(clipboardFile, dest);
				else Files.copy(clipboardFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			refresh();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(parentFrame, "Could not paste: " + e.getMessage());
		}
	}

	private void copyDirectory(File src, File dest) throws IOException {
		if (!dest.exists()) dest.mkdirs();
		for (File f : src.listFiles()) {
			File d = new File(dest, f.getName());
			if (f.isDirectory()) copyDirectory(f, d);
			else Files.copy(f.toPath(), d.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public MouseListener createMouseListener() {
		return new MouseAdapter() {
			
			@Override
			public void mouseClicked(MouseEvent e) {
				
				if (e.getButton()==MouseEvent.BUTTON1 && e.getClickCount()>=2) {
					onFileRun();
				}
				
			}
			
		};
	}
	
	public void onFileRun() {
		
		// If there is an item selected.
		if (list.getSelectedIndex() != -1) {
			
			String item = getElementAt(list.getSelectedIndex());
			
			// If the item starts with '/' it is a directory.
			if (item.startsWith("/")) {
				System.out.println(dir.getAbsolutePath() + item);
				loadDirectory(new File(dir.getAbsolutePath() + item));
			}else {
				
				if (item.equalsIgnoreCase("..")) {
					// Go up a directory
					loadDirectory(new File(dir.getAbsolutePath() + item).getParentFile());
				} else {
					File file = new File(dir.getAbsolutePath() + "/" + item);
					if (openInSystemDefault) {
						try {
							Desktop.getDesktop().open(file);
						} catch (IOException ex) {
							JOptionPane.showMessageDialog(parentFrame, "Could not open file: " + ex.getMessage());
						}
					} else {
						FileEditor editor = new FileEditor();
						if (parentFrame != null) {
							editor.setLocationRelativeTo(parentFrame);
						}
						try {
							editor.openFile(file);
						} catch (IOException e) {
							e.printStackTrace();
						}
						editor.setVisible(true);
					}
				}
				
			}
			
		}
		
	}
	
	public void onFileDelete() {
		File sel = getSelectedFile();
		if (sel != null && JOptionPane.showConfirmDialog(parentFrame, "Do you want to delete \"" + sel.getName() + "\"?", "Confirm Delete", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
			deleteSelected();
	}
	
	public KeyListener createKeyListener() {
		return new KeyAdapter() {
			
			@Override
			public void keyReleased(KeyEvent e) {
				
				if (e.getKeyCode()==KeyEvent.VK_ENTER) {
					onFileRun();
				}
				
				if (e.getKeyCode()==KeyEvent.VK_DELETE) {
					onFileDelete();
				}
				
			}
			
		};
	}
	
}