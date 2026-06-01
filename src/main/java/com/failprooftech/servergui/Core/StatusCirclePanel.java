package com.failprooftech.servergui.Core;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

import javax.swing.JPanel;

/**
 * Small panel that shows server status as a colored circle:
 * green when the server is online, red when offline.
 * Used in the main window top bar next to the status label.
 */
public class StatusCirclePanel extends JPanel {

	private boolean online = false;

	public void setOnline(boolean online) {
		if (this.online != online) {
			this.online = online;
			repaint();
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		int d = (int) (Math.min(w, h) * 0.6);
		if (d < 4) d = 4;
		int x = (w - d) / 2;
		int y = (h - d) / 2;
		g2.setColor(online ? new Color(0, 180, 0) : new Color(200, 0, 0));
		g2.fill(new Ellipse2D.Float(x, y, d, d));
		g2.setColor(online ? new Color(0, 220, 0) : new Color(255, 80, 80));
		g2.draw(new Ellipse2D.Float(x, y, d, d));
		g2.dispose();
	}
}
