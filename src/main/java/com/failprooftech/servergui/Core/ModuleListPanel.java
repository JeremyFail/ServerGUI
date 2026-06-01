package com.failprooftech.servergui.Core;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import com.failprooftech.servergui.Module;
import com.failprooftech.servergui.ModuleManager;

/**
 * Module List tab: list of loaded modules with a context menu that shows
 * the selected module's custom menu items (or "No Module Items").
 */
public class ModuleListPanel extends JPanel {

	public ModuleListPanel() {
		JScrollPane scrollPane = new JScrollPane();
		JList<String> moduleList = new JList<>();
		DefaultListModel<String> model = new DefaultListModel<>();
		for (Module m : ModuleManager.modules) {
			model.addElement(m.getName());
		}
		moduleList.setModel(model);
		scrollPane.setViewportView(moduleList);

		JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.addPopupMenuListener(new PopupMenuListener() {
			@Override public void popupMenuCanceled(PopupMenuEvent e) {}
			@Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
			@Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				popupMenu.removeAll();
				if (moduleList.getSelectedIndex() == -1) return;
				popupMenu.add(new JMenuItem(moduleList.getSelectedValue()));
				popupMenu.add(new JMenuItem(" "));
				Module module = ModuleManager.getModule(moduleList.getSelectedValue());
				if (module != null && module.getMenuItems() != null) {
					for (JMenuItem item : module.getMenuItems()) {
						popupMenu.add(item);
					}
				} else {
					popupMenu.add(new JMenuItem("No Module Items"));
				}
			}
		});
		addPopup(moduleList, popupMenu);

		setLayout(new java.awt.BorderLayout());
		add(scrollPane, java.awt.BorderLayout.CENTER);
	}

	private static void addPopup(java.awt.Component component, JPopupMenu popup) {
		component.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mousePressed(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger()) popup.show(e.getComponent(), e.getX(), e.getY());
			}
			@Override public void mouseReleased(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger()) popup.show(e.getComponent(), e.getX(), e.getY());
			}
		});
	}
}
