package com.failprooftech.servergui.Core;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableModel;

import com.failprooftech.servergui.ProcessException;

/**
 * Players tab: table of online players, context menu (Op, De-Op, Kick, Ban),
 * and buttons (Kick, Ban, Op, De-Op, Pardon). Requires the main frame for
 * server checks and sending commands via the Core module.
 */
public class PlayersPanel extends JPanel {

	private final JTable playersTable;
	private final JLabel lblPlayersOnlineCount;

	public PlayersPanel(ServerGUI gui) {
		JScrollPane scrollPane = new JScrollPane();
		playersTable = new JTable();
		playersTable.setModel(new DefaultTableModel(new String[] { "Username", "Running IP" }, 0));
		lblPlayersOnlineCount = new JLabel("Players online: 0");
		scrollPane.setViewportView(playersTable);

		JPopupMenu popupMenu = new JPopupMenu();
		JMenuItem mntmPlayerName = new JMenuItem("Player Name");
		popupMenu.addPopupMenuListener(new PopupMenuListener() {
			@Override public void popupMenuCanceled(PopupMenuEvent e) {}
			@Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
			@Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				int row = playersTable.getSelectedRow();
				if (row >= 0) {
					Object val = playersTable.getModel().getValueAt(row, 0);
					String player = (val == null) ? "" : val.toString().trim();
					mntmPlayerName.setText(player.isEmpty() ? "(no player)" : player);
				}
			}
		});
		playersTable.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
			@Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
			private void showPopup(MouseEvent e) {
				int row = playersTable.rowAtPoint(e.getPoint());
				if (row < 0) return;
				Object val = playersTable.getModel().getValueAt(row, 0);
				String player = (val == null) ? "" : val.toString().trim();
				if (player.isEmpty() || "(No players online)".equals(player)) return;
				playersTable.setRowSelectionInterval(row, row);
				popupMenu.show(playersTable, e.getX(), e.getY());
			}
		});

		JMenuItem mntmOp = new JMenuItem("Op");
		mntmOp.addActionListener(e -> runIfServerOnline(gui, () -> {
			String player = getSelectedPlayerName();
			if (player != null) try { gui.sendCommand("op " + player); } catch (ProcessException ex) { ex.printStackTrace(); }
		}));
		JMenuItem mntmDeop = new JMenuItem("De-Op");
		mntmDeop.addActionListener(e -> runIfServerOnline(gui, () -> {
			String player = getSelectedPlayerName();
			if (player != null) try { gui.sendCommand("deop " + player); } catch (ProcessException ex) { ex.printStackTrace(); }
		}));
		JMenuItem mntmKick = new JMenuItem("Kick");
		mntmKick.addActionListener(e -> runIfServerOnline(gui, () -> {
			String player = getSelectedPlayerName();
			if (player == null) return;
			if (JOptionPane.showConfirmDialog(gui, "Are you sure you want to kick " + player + "?", "Kick", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				String reason = JOptionPane.showInputDialog(gui, "Reason?");
				try { gui.sendCommand("kick " + player + (reason != null ? " " + reason : "")); } catch (ProcessException ex) { ex.printStackTrace(); }
			}
		}));
		JMenuItem mntmBan = new JMenuItem("Ban");
		mntmBan.addActionListener(e -> runIfServerOnline(gui, () -> {
			String player = getSelectedPlayerName();
			if (player == null) return;
			if (JOptionPane.showConfirmDialog(gui, "Are you sure you want to ban " + player + "?", "Ban", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				String reason = JOptionPane.showInputDialog(gui, "Reason?");
				try { gui.sendCommand("ban " + player + (reason != null ? " " + reason : "")); } catch (ProcessException ex) { ex.printStackTrace(); }
			}
		}));

		popupMenu.add(mntmPlayerName);
		popupMenu.add(new JMenuItem(" "));
		popupMenu.add(mntmOp);
		popupMenu.add(mntmDeop);
		popupMenu.add(mntmKick);
		popupMenu.add(mntmBan);

		JButton btnPardon = new JButton("Pardon a Player");
		btnPardon.addActionListener(e -> {
			String player = JOptionPane.showInputDialog(gui, "Player Name");
			if (player == null || player.trim().isEmpty()) return;
			player = player.trim();
			if (JOptionPane.showConfirmDialog(gui, "Are you sure you want to pardon " + player + "?", "Pardon", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				try { gui.sendCommand("pardon " + player); } catch (ProcessException ex) { ex.printStackTrace(); }
			}
		});
		JButton btnKick = new JButton("Kick");
		btnKick.addActionListener(e -> runIfServerOnline(gui, () -> {
			String player = getSelectedPlayerName();
			if (player == null) return;
			if (JOptionPane.showConfirmDialog(gui, "Are you sure you want to kick " + player + "?", "Kick", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				String reason = JOptionPane.showInputDialog(gui, "Reason?");
				try { gui.sendCommand("kick " + player + (reason != null ? " " + reason : "")); } catch (ProcessException ex) { ex.printStackTrace(); }
			}
		}));
		JButton btnBan = new JButton("Ban");
		btnBan.addActionListener(e -> runIfServerOnline(gui, () -> {
			String player = getSelectedPlayerName();
			if (player == null) return;
			if (JOptionPane.showConfirmDialog(gui, "Are you sure you want to ban " + player + "?", "Ban", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				String reason = JOptionPane.showInputDialog(gui, "Reason?");
				try { gui.sendCommand("ban " + player + (reason != null ? " " + reason : "")); } catch (ProcessException ex) { ex.printStackTrace(); }
			}
		}));
		JButton btnOp = new JButton("Op");
		btnOp.addActionListener(e -> runIfServerOnline(gui, () -> {
			String player = getSelectedPlayerName();
			if (player != null) try { gui.sendCommand("op " + player); } catch (ProcessException ex) { ex.printStackTrace(); }
		}));
		JButton btnDeop = new JButton("De-Op");
		btnDeop.addActionListener(e -> runIfServerOnline(gui, () -> {
			String player = getSelectedPlayerName();
			if (player != null) try { gui.sendCommand("deop " + player); } catch (ProcessException ex) { ex.printStackTrace(); }
		}));

		GroupLayout gl = new GroupLayout(this);
		setLayout(gl);
		gl.setHorizontalGroup(
			gl.createParallelGroup(Alignment.LEADING)
				.addGroup(gl.createSequentialGroup()
					.addGap(12)
					.addGroup(gl.createParallelGroup(Alignment.LEADING)
						.addGroup(gl.createSequentialGroup()
							.addComponent(btnKick)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnBan)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnOp)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnDeop)
							.addPreferredGap(ComponentPlacement.RELATED, 0, Short.MAX_VALUE)
							.addComponent(btnPardon, GroupLayout.PREFERRED_SIZE, 142, GroupLayout.PREFERRED_SIZE))
						.addComponent(lblPlayersOnlineCount)
						.addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 626, Short.MAX_VALUE))
					.addContainerGap())
		);
		gl.setVerticalGroup(
			gl.createParallelGroup(Alignment.TRAILING)
				.addGroup(gl.createSequentialGroup()
					.addContainerGap()
					.addComponent(lblPlayersOnlineCount)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 430, Short.MAX_VALUE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(gl.createParallelGroup(Alignment.BASELINE)
						.addComponent(btnKick).addComponent(btnBan).addComponent(btnOp).addComponent(btnDeop).addComponent(btnPardon))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGap(6)
					.addContainerGap())
		);
	}

	private String getSelectedPlayerName() {
		int row = playersTable.getSelectedRow();
		if (row < 0) return null;
		Object val = playersTable.getModel().getValueAt(row, 0);
		return (val == null) ? null : val.toString().trim();
	}

	private static void runIfServerOnline(ServerGUI gui, Runnable action) {
		if (ServerGUI.server == null || !ServerGUI.server.isRunning()) {
			JOptionPane.showMessageDialog(gui, "There is no server running");
			return;
		}
		action.run();
	}

	public JTable getPlayersTable() { return playersTable; }
	public JLabel getLblPlayersOnlineCount() { return lblPlayersOnlineCount; }
}
