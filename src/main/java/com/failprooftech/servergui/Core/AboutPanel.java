package com.failprooftech.servergui.Core;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LayoutStyle.ComponentPlacement;

import com.failprooftech.servergui.Instructions.InstructionWindow;

/**
 * About/Help tab: Help button and credits label.
 */
public class AboutPanel extends JPanel {

	public AboutPanel(ServerGUI gui) {
		JButton btnHelp = new JButton("Help");
		btnHelp.addActionListener(e -> {
			try {
				InstructionWindow window = new InstructionWindow();
				window.setLocationRelativeTo(gui);
				window.setVisible(true);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});

		JLabel lblCredits = new JLabel("By Ymerejliaf, JusticePro");

		GroupLayout gl = new GroupLayout(this);
		setLayout(gl);
		gl.setHorizontalGroup(
			gl.createParallelGroup(GroupLayout.Alignment.LEADING)
				.addGroup(gl.createSequentialGroup()
					.addContainerGap()
					.addComponent(btnHelp, GroupLayout.PREFERRED_SIZE, 109, GroupLayout.PREFERRED_SIZE)
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
				.addGroup(gl.createSequentialGroup()
					.addContainerGap()
					.addComponent(lblCredits)
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
		);
		gl.setVerticalGroup(
			gl.createSequentialGroup()
				.addContainerGap()
				.addComponent(btnHelp)
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addComponent(lblCredits)
				.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
		);
	}
}
