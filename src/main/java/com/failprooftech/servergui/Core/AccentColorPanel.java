package com.failprooftech.servergui.Core;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;

/**
 * Panel showing preset accent color swatches for FlatLaf themes.
 * Selection border is light on dark backgrounds and dark on light backgrounds.
 * Notifies a listener when the user picks a different color.
 */
public class AccentColorPanel extends JPanel {

	private static final int[] PRESET_RGB = {
		0x0096E6, 0xE63946, 0x7B2CBF, 0xF77F00, 0x2A9D8F, 0x6C757D
	};
	private static final int SWATCH_SIZE = 22;

	private int selectedRgb;
	private Runnable accentChangeListener;

	public AccentColorPanel(int initialRgb) {
		selectedRgb = initialRgb != 0 ? initialRgb : 0x0096E6;
		setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
		setPreferredSize(new Dimension((SWATCH_SIZE * PRESET_RGB.length) + (4 * (PRESET_RGB.length - 1)) + 5, SWATCH_SIZE + 8));
		for (final int rgb : PRESET_RGB) {
			JPanel swatch = new JPanel() {
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setColor(new Color(rgb));
					g2.fillRect(1, 1, getWidth() - 2, getHeight() - 2);
					if (selectedRgb == rgb) {
						Color bg = getParent() != null ? getParent().getBackground() : getBackground();
						float lum = bg != null ? (0.299f * bg.getRed() + 0.587f * bg.getGreen() + 0.114f * bg.getBlue()) / 255f : 0.5f;
						g2.setColor(lum < 0.5f ? Color.LIGHT_GRAY : Color.DARK_GRAY);
						g2.setStroke(new java.awt.BasicStroke(2f));
						g2.drawRect(1, 1, getWidth() - 2, getHeight() - 2);
					}
					g2.dispose();
				}
			};
			swatch.setPreferredSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
			swatch.setBackground(getBackground());
			swatch.setCursor(new Cursor(Cursor.HAND_CURSOR));
			swatch.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					selectedRgb = rgb;
					AccentColorPanel.this.repaint();
					if (accentChangeListener != null) accentChangeListener.run();
				}
			});
			add(swatch);
		}
	}

	public void setAccentChangeListener(Runnable listener) {
		this.accentChangeListener = listener;
	}

	public int getSelectedRgb() {
		return selectedRgb;
	}
}
