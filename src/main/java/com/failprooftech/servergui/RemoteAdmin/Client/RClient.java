package com.failprooftech.servergui.RemoteAdmin.Client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import javax.swing.JOptionPane;

import com.failprooftech.servergui.RemoteAdmin.AdminWindow;
import com.failprooftech.servergui.RemoteAdmin.Packet;
import com.failprooftech.servergui.RemoteAdmin.PacketHandler;
import com.failprooftech.servergui.RemoteAdmin.Server.RServer;

public class RClient extends Thread {

	private volatile boolean running = true;
	private volatile boolean closedByShutdown = false;

	public Socket socket;
	public static ArrayList<PacketHandler> clientPacketHandlers = new ArrayList<PacketHandler>();
	public AdminWindow adminWindow;

	/**
	 * The Client.
	 * @param ip The server ip.
	 * @param adminWindow UI for the client.
	 */
	public RClient(String ip, AdminWindow adminWindow) throws UnknownHostException, IOException {
		this.socket = new Socket(ip, RServer.port);
		this.adminWindow = adminWindow;
	}

	/**
	 * Safely shut down the client (replaces deprecated Thread.stop()).
	 * Stops the read loop and closes the socket.
	 */
	public void shutdown() {
		closedByShutdown = true;
		running = false;
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		interrupt();
	}

	@Override
	public void run() {

		DataInputStream input = null;
		try {
			input = new DataInputStream(socket.getInputStream());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		while (running && socket != null && socket.isConnected()) {
			try {
				try {
					String utf = input.readUTF();
					
					if (utf != null) {
						onPacketSentRaw(utf);
					}
					
				}catch (EOFException e) {
					
				}catch (SocketException e) {
					break;
				}
				
				
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		}
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (!closedByShutdown) {
			JOptionPane.showMessageDialog(null, "You have been disconnected from the server.");
			adminWindow.dispose();
		}
	}
	
	/**
	 * Executed when recieves data.
	 * @param utf
	 */
	public void onPacketSentRaw(String utf)
	{
		Packet packet = new Packet(utf);
		System.out.println("Raw Packet Recieved");
		for (PacketHandler ph : clientPacketHandlers) {
			if (ph != null) {
				ph.onPacketRecievedClient(packet, this);
			}
		}
		
	}
	
	/**
	 * This method sends a packet to the server.
	 * @param packet The packet to send.
	 */
	public void sendPacket(Packet packet)
	{
		
		try {
			DataOutputStream output = new DataOutputStream(socket.getOutputStream());
			
			output.writeUTF(packet.getUTF());
			output.flush();
			
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}
	
}