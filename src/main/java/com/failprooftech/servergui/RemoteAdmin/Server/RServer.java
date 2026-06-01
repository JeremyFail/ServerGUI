package com.failprooftech.servergui.RemoteAdmin.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.util.ArrayList;

import com.failprooftech.servergui.Module;
import com.failprooftech.servergui.ModuleManager;
import com.failprooftech.servergui.RemoteAdmin.Packet;
import com.failprooftech.servergui.RemoteAdmin.PacketHandler;
import com.failprooftech.servergui.RemoteAdmin.User;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketLogin;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketMailRefresh;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketMessageBox;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketMotd;

public class RServer extends Thread {

	public static final int port = 6289;

	public boolean run = true;
	public ArrayList<RConnection> connections = new ArrayList<>();

	public static ArrayList<PacketHandler> serverPacketHandlers = new ArrayList<PacketHandler>();
	public ServerSocket ss;

	@Override
	public void run()
	{
		try {
			ss = new ServerSocket(port);

			while (run) {
				Socket socket = ss.accept();
				System.out.println("Accepted Socket");
				RConnection connection = new RConnection(socket, this);
				connections.add(connection);
				connection.start();
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Safely shut down the server (replaces deprecated Thread.stop()).
	 * Stops accepting new connections and interrupts the thread.
	 */
	public void shutdown() {
		run = false;
		try {
			if (ss != null && !ss.isClosed()) {
				ss.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		interrupt();
	}

	/**
	 * On data sent to the server.
	 * @param utf The raw data.
	 * @param connection The client
	 */
	public void onPacketSentRaw(String utf, RConnection connection)
	{
		Packet packet = new Packet(utf);
		System.out.println("Raw Packet Recieved.");

		if (connection.user != null) {

			for (PacketHandler ph : serverPacketHandlers) {
				ph.onPacketRecievedServer(packet, connection, this);
			}

		}else {

			if (packet.getPacketName().equalsIgnoreCase("login")) {

				PacketLogin login = new PacketLogin(packet);

				if (login.isFunctionalPacket()) {
					
					String username = login.getUsername();
					String password = login.getPassword();

					User user = User.query(username, password);
					
					if (connection.lastLogin != null) {
						
						// Credit to SMI (Samaritan Ministries)
						// Anti-bruteforce: One login per second.
						
						if (!LocalTime.now().isAfter(connection.lastLogin.plusSeconds(1))) {
							user = null;
							System.out.println("It hasen't been a second yet. Cancelling Login " + connection.lastLogin);
						}
						
					}
					
					if (user != null) {
						connection.user = user;
						connection.sendPacket(new PacketMotd("Welcome!", "This is the motd."));
						connection.sendPacket(new PacketMailRefresh(user.mail));
						
						String text = "The server's modules are:\n";
						
						for (Module module : ModuleManager.modules) {
							
							if (module.getWebsite() != null) {
								text = text + "\"" + module.getName() + "\". You can get it at " + module.getWebsite() + "\n";
							}else {
								text = text + "\"" + module.getName() + "\". No website is specified.\n";
							}
							
						}
						
						connection.sendPacket(new PacketMessageBox(text));
						
					}else {
						connection.lastLogin = LocalTime.now();
					}
					
				}

			}

		}

	}

}