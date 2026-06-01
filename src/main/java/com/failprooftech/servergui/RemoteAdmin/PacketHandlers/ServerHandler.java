package com.failprooftech.servergui.RemoteAdmin.PacketHandlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.util.ArrayList;

import com.failprooftech.servergui.Console;
import com.failprooftech.servergui.Module;
import com.failprooftech.servergui.ProcessException;
import com.failprooftech.servergui.Core.ServerGUI;
import com.failprooftech.servergui.RemoteAdmin.CorePermissions;
import com.failprooftech.servergui.RemoteAdmin.Mail;
import com.failprooftech.servergui.RemoteAdmin.Packet;
import com.failprooftech.servergui.RemoteAdmin.PacketHandler;
import com.failprooftech.servergui.RemoteAdmin.User;
import com.failprooftech.servergui.RemoteAdmin.Client.RClient;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketAcceptEula;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketConsole;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketMail;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketMailRefresh;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketMessageBox;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketQueryMail;
import com.failprooftech.servergui.RemoteAdmin.Packets.PacketServerStart;
import com.failprooftech.servergui.RemoteAdmin.Packets.SendMessage;
import com.failprooftech.servergui.RemoteAdmin.Server.RConnection;
import com.failprooftech.servergui.RemoteAdmin.Server.RServer;

public class ServerHandler extends Module implements PacketHandler {

	public static ArrayList<Console> consoles = new ArrayList<>();
	private static LocalTime lastCommand;

	/**
	 * Packet-Handler for the server.
	 */
	public ServerHandler()
	{
		super("Remote Admin");
	}

	@Override
	public void onPacketRecievedServer(Packet packet, RConnection connection, RServer server)
	{

		if (packet.getPacketName().equalsIgnoreCase("serverchat_send")) {
			SendMessage sm = new SendMessage(packet);
			if (connection.user.hasPermission(CorePermissions.CHAT_SEND)) {
				
				if (sm.getMessage().startsWith("/") && sm.getMessage().length() >= 2) {
					String command = sm.getMessage().substring(1);

					if (command.equalsIgnoreCase("help")) {
						connection.sendPacket(new SendMessage("Commands:\n/help - Show this help menu\n/list - List users."));
					}

					if (command.equalsIgnoreCase("list")) {
						String users = "";

						for (User user : User.users) {
							
							if (user.isOnline(server)) {
								users += "[Online] " + user.username + "\n";
							}else {
								users += "[Offline] " + user.username + "\n";
							}
							
						}

						users = users.substring(0, users.length() - 1);

						connection.sendPacket(new SendMessage(users));

					}

				}else {
					for (RConnection c : server.connections) {
						if (c.user.hasPermission(CorePermissions.CHAT_READ)) {
							c.sendPacket(new SendMessage(connection.user.username + ": " + sm.getMessage()));
						}
					}

				}

			}

		}

		if (packet.getPacketName().equalsIgnoreCase("console_send")) {
			PacketConsole console = new PacketConsole(packet);

			try {

				if (connection.user.hasPermission(CorePermissions.ALLOW_BOT)) {

					// Credit to SMI (Samaritan Ministries)
					if (!LocalTime.now().isAfter(lastCommand.plusSeconds(1)) && connection.user.hasPermission(CorePermissions.ALLOW_BOT)) {
						System.out.println("It hasen't been a second yet. Cancelling Command");
					}else {
						sendCommand(console.getMessage());
						lastCommand = LocalTime.now();
					}

				}

			} catch (ProcessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		if (packet.getPacketName().equalsIgnoreCase("mail")) {
			PacketMail mail = new PacketMail(packet);

			if (mail.isFunctionalPacket()) {

				User user = User.queryIgnoreCase(mail.getTo());

				if (user != null) {

					for (RConnection rconnection : server.connections) {

						if (rconnection.user.username.equalsIgnoreCase(user.username)) {
							Mail mail2 = new Mail(mail.getSubject(), "Mail by " + connection.user.username + "\n" + mail.getBody());
							user.addMail(mail2);
							try {
								User.saveUsers();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							rconnection.sendPacket(new PacketMailRefresh(user.mail));
						}

					}

				}else {
					connection.user.addMail(new Mail("Mail not sent.", "Couldn't send mail: user was not found."));
					try {
						User.saveUsers();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					connection.sendPacket(new PacketMailRefresh(connection.user.mail));
				}

			}else {
				connection.user.addMail(new Mail("Mail not sent.", "Couldn't send mail: some inputs were not filled out."));
				try {
					User.saveUsers();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				connection.sendPacket(new PacketMailRefresh(connection.user.mail));
			}

		}

		if (packet.getPacketName().equalsIgnoreCase("mail_query")) {
			PacketQueryMail queryMail = new PacketQueryMail(packet);

			if (queryMail.isNumber()) {
				int id = Integer.parseInt(queryMail.getId());
				PacketMail packetMail = new PacketMail(" ", connection.user.mail.get(id).getSubject(), connection.user.mail.get(id).getBody());
				connection.sendPacket(packetMail);
			}

		}

		if (packet.getPacketName().equalsIgnoreCase("server_start")) {
			PacketServerStart serverStart = new PacketServerStart(packet);

			if (serverStart.isFunctionalPacket()) {

				if (connection.user.hasPermission(CorePermissions.ADMIN)) {
					System.out.println("Server_Start is Functional");

					File eula = new File("eula.txt");

					if (!eula.exists()) {

						if (serverStart.doesAcceptEula()) {
							try {
								Files.copy(getClass().getResourceAsStream("/eula.txt"), eula.toPath(), StandardCopyOption.REPLACE_EXISTING);
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}else {
							PacketAcceptEula acceptEula = new PacketAcceptEula();
							connection.sendPacket(acceptEula);
						}

					}

					if (eula.exists()) {

						if (ServerGUI.jarFile == null) {

							File file = new File("server.jar");

							if (file.exists()) {
								ServerGUI.jarFile = file;
							}

						}

						if (ServerGUI.server != null) {
							System.out.println("Server_Start is not null");
							if (!ServerGUI.server.isRunning() && ServerGUI.jarFile != null) {
								System.out.println("Server_Starting");
								try {
									ServerGUI.instance.startServer(serverStart.getArguments(), serverStart.getSwitches());
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

							}

						}else {
							System.out.println("Server_Start is null");
							if (ServerGUI.jarFile != null) {
								System.out.println("Server_Starting");
								try {
									ServerGUI.instance.startServer(serverStart.getArguments(), serverStart.getSwitches());
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

							}

						}

					}

				}else {
					connection.sendPacket(new PacketMessageBox("You don't have server control permissions."));
				}

			}else {
				System.out.println("Server_Start is not functional");
			}

		}

	}

	@Override
	public void onPacketRecievedClient(Packet packet, RClient client) {}

	@Override
	public void onConsolePrintRaw(String message) {
		super.onConsolePrintRaw(message);

		for (Console console : consoles) {
			console.onConsolePrint(message);
		}

	}

	@Override
	public void init() {
		try {
			User.users = User.loadUsers();
		} catch (ClassNotFoundException | IOException e) {
			User.users = new ArrayList<>();
			try {
				User.saveUsers();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

}