package com.failprooftech.servergui.Core;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.LayoutStyle.ComponentPlacement;

import com.failprooftech.servergui.RemoteAdmin.LoginWindow;
import com.failprooftech.servergui.RemoteAdmin.ServerWindow;

/**
 * Remote Admin tab: buttons to connect to a remote server or host a server.
 */
public class RemoteAdminPanel extends JPanel {

	public RemoteAdminPanel(ServerGUI gui) {
		JButton btnConnectToServer = new JButton("Connect to Server");
		btnConnectToServer.addActionListener(e -> {
			LoginWindow login = new LoginWindow();
			login.setVisible(true);
		});

		JButton btnCreateServer = new JButton("Host Server");
		btnCreateServer.addActionListener(e -> {
			ServerWindow serverWindow = new ServerWindow();
			serverWindow.setVisible(true);
		});

		GroupLayout gl = new GroupLayout(this);
		setLayout(gl);
		gl.setHorizontalGroup(
			gl.createParallelGroup(Alignment.LEADING)
				.addGroup(gl.createSequentialGroup()
					.addComponent(btnConnectToServer, GroupLayout.PREFERRED_SIZE, 301, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.RELATED, 145, Short.MAX_VALUE)
					.addComponent(btnCreateServer, GroupLayout.PREFERRED_SIZE, 301, GroupLayout.PREFERRED_SIZE))
		);
		gl.setVerticalGroup(
			gl.createParallelGroup(Alignment.LEADING)
				.addGroup(Alignment.TRAILING, gl.createSequentialGroup()
					.addContainerGap(535, Short.MAX_VALUE)
					.addGroup(gl.createParallelGroup(Alignment.BASELINE)
						.addComponent(btnConnectToServer, GroupLayout.PREFERRED_SIZE, 95, GroupLayout.PREFERRED_SIZE)
						.addComponent(btnCreateServer, GroupLayout.PREFERRED_SIZE, 95, GroupLayout.PREFERRED_SIZE)))
		);
	}
}
