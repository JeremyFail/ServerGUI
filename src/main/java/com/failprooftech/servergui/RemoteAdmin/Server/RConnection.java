package com.failprooftech.servergui.RemoteAdmin.Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalTime;

import com.failprooftech.servergui.Console;
import com.failprooftech.servergui.RemoteAdmin.Packet;
import com.failprooftech.servergui.RemoteAdmin.User;
import com.failprooftech.servergui.RemoteAdmin.PacketHandlers.ServerHandler;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketConsole;

public class RConnection extends Thread implements Console {
	
	private Socket socket;
	private RServer server;
	
	public User user = null;
	
	public LocalTime lastLogin = null;
	
	/**
	 * Server's connection to the User.
	 * @param socket The socket to the client.
	 * @param server The server managing the client.
	 */
	public RConnection(Socket socket, RServer server)
	{
		this.socket = socket;
		this.server = server;
	}
	
	@Override
	public void run()
	{
		DataInputStream input = null;
		try {
			input = new DataInputStream(socket.getInputStream());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		ServerHandler.consoles.add(this);
		
		while (socket.isConnected()) {
			try {
				try {
					String utf = input.readUTF();
					
					server.onPacketSentRaw(utf, this);
					
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
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Send a network packet to the user.
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
	
	@Override
	public void onConsolePrint(String message)
	{
		if (message.equalsIgnoreCase("")) {
			sendPacket(new PacketConsole(" "));
		}else {
			sendPacket(new PacketConsole(message));
		}
	}
	
}