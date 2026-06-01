package com.failprooftech.servergui.Utils;

import javax.swing.JOptionPane;

/** Reusable user-facing dialogs (errors, etc.). */
public final class Dialogs {

	private Dialogs() {}

	/** Show an error message in a popup; does not throw. */
	public static void showError(String message) {
		JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	/** Show an error message with title. */
	public static void showError(String title, String message) {
		JOptionPane.showMessageDialog(null, message, title != null ? title : "Error", JOptionPane.ERROR_MESSAGE);
	}
}
