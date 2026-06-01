package com.failprooftech.servergui;

import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JFrame;
import com.failprooftech.servergui.Utils.AppIcons;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;

public class ReporterWindow extends JFrame {

	private JPanel contentPane;

	/**
	 * Create the frame.
	 */
	public ReporterWindow(Exception e) {
		setIconImages(AppIcons.getIcons());
		setTitle(e.toString() + " - Bug Reporter");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setBounds(100, 100, 650, 550);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		
		JScrollPane scrollPane = new JScrollPane();
		
		JButton btnExitServerGUI = new JButton("Exit ServerGUI");
		btnExitServerGUI.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.exit(1);
			}
		});
		
		JButton btnContinueServerGUI = new JButton("Continue ServerGUI");
		btnContinueServerGUI.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		
		JButton btnCopyStacktrace = new JButton("Copy Stacktrace");
		btnCopyStacktrace.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				getToolkit().getSystemClipboard().setContents(new StringSelection(getStacktrace(e)), null);
			}
		});
		
		JLabel lblPleaseReportThis = new JLabel("Please report this in the ServerGUI GitHub Issues.");
		GroupLayout gl_contentPane = new GroupLayout(contentPane);
		gl_contentPane.setHorizontalGroup(
			gl_contentPane.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_contentPane.createSequentialGroup()
					.addGroup(gl_contentPane.createParallelGroup(Alignment.LEADING)
						.addGroup(gl_contentPane.createSequentialGroup()
							.addComponent(btnExitServerGUI)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnContinueServerGUI)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(btnCopyStacktrace))
						.addComponent(lblPleaseReportThis))
					.addContainerGap(273, Short.MAX_VALUE))
				.addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 624, Short.MAX_VALUE)
		);
		gl_contentPane.setVerticalGroup(
			gl_contentPane.createParallelGroup(Alignment.TRAILING)
				.addGroup(gl_contentPane.createSequentialGroup()
					.addComponent(scrollPane, GroupLayout.PREFERRED_SIZE, 441, GroupLayout.PREFERRED_SIZE)
					.addGap(18, 18, Short.MAX_VALUE)
					.addComponent(lblPleaseReportThis)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(gl_contentPane.createParallelGroup(Alignment.BASELINE)
						.addComponent(btnExitServerGUI)
						.addComponent(btnContinueServerGUI)
						.addComponent(btnCopyStacktrace)))
		);
		
		JTextArea textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		
		textArea.setText(getStacktrace(e));
		
		scrollPane.setViewportView(textArea);
		contentPane.setLayout(gl_contentPane);
	}
	
	public static String getStacktrace(Throwable throwable) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		throwable.printStackTrace(pw);
		return sw.toString();
	}
}