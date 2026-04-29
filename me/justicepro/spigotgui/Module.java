package me.justicepro.spigotgui;

import java.io.IOException;
import java.io.InputStream;

import javax.swing.JMenuItem;

import me.justicepro.spigotgui.Api.ServerType;
import me.justicepro.spigotgui.Core.SpigotGUI;
import me.justicepro.spigotgui.RemoteAdmin.Permission;
import me.justicepro.spigotgui.RemoteAdmin.User;
import me.justicepro.spigotgui.RemoteAdmin.Client.RClient;

public class Module {
	
	private String name;
	private ServerType serverType = ServerType.Unknown;
	
	/**
	 * Modules for ServerGUI
	 * @param name The module name
	 */
	public Module(String name) {
		this.name = name;
	}
	
	/**
	 * @return The module name.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Has bukkit been detected
	 * 
	 * @return If bukkit has been detected.
	 */
	public boolean isBukkit() {
		return serverType.isBukkit();
	}
	
	/**
	 * @param message Raw Console Printed
	 */
	public void onConsolePrintRaw(String message) {
		
		if (message.split("INFO]: ").length > 1) {
			onConsolePrint(message.split("INFO]: ")[1]);
		}
		
		if (message.split("INFO] ").length > 1) {
			onConsolePrint(message.split("INFO] ")[1]);
		}
		
	}
	
	/**
	 * @return The type of server
	 */
	public ServerType getServerType() {
		return serverType;
	}
	
	/**
	 * @param message Message to send to console
	 * Messages to console that have "INFO]: " and removes them.
	 */
	public void onConsolePrint(String message) {
		
		if (message.startsWith("Starting minecraft server version ")) {
			onVersionDetected(message.split("Starting minecraft server version ")[1]);
			onServerInit();
		}
		
		if (message.startsWith("This server is running CraftBukkit ")) {
			onBukkitVersionDetected(message.split("This server is running CraftBukkit version ")[1].split(" ")[0]);
		}
		
		if (message.startsWith("[Sponge]: This server is running Sponge")) {
			System.out.println(message);
			onSpongeVersionDetected(message.split("running ")[1].split(" version")[0]);
		}

		if (message.startsWith("[bootstrap] Loading Paper")) {
			// Version example: [bootstrap] Loading Paper 1.21.11-55-main@b36b49a (2025-12-26T16:47:57Z) for Minecraft 1.21.11
			onPaperVersionDetected(message.split("Loading Paper ")[1].split(" ")[0]);
		}

		// Single handler for all server types: detect format at runtime (e.g. TextComponent in disconnect reason)
		if (message.startsWith("<") && message.contains("> ")) {
			try {
				String afterBracket = message.split("<", 2)[1];
				String player = afterBracket.split("> ")[0];
				String chatMsg = afterBracket.split("> ", 2)[1];
				onChatMessage(chatMsg, player);
			} catch (Exception ignore) {}
		}
		if (message.contains("issued server command: /")) {
			try {
				String cmd = message.split(" issued server command: /", 2)[1];
				String player = message.split(" issued server command: /")[0].trim();
				onCommandSent(cmd, player);
			} catch (Exception ignore) {}
		}
		if (message.contains(" logged in with entity id ")) {
			try {
				String name = message.split("\\[/")[0].trim();
				String ip = message.split("\\[/")[1].split(":")[0].trim();
				onPlayerJoin(name, ip);
			} catch (Exception ignore) {}
		}
		if (message.contains(" lost connection: ")) {
			try {
				String[] parts = message.split(" lost connection: ", 2);
				String player = parts[0].trim();
				String reason = parts.length > 1 ? parts[1] : "";
				if (reason.contains("TextComponent\\{text='")) {
					try {
						reason = reason.split("TextComponent\\\\\\{text='")[1].split("'")[0];
					} catch (Exception e2) {}
				}
				onPlayerLeave(player, reason);
			} catch (Exception ignore) {}
		}
		if (message.startsWith("Done ") && (message.endsWith("! For help, type \"help\" or \"?\"") || message.endsWith("! For help, type \"help\""))) {
			onServerReady();
		}
	}

	/**
     * When a Vanilla version has been detected.
	 * 
     * @param version The version string
     */
	public void onVersionDetected(String version) {
		// We don't really care about the version right now, we just need to know when it's detected
	}
	
	/**
	 * When a Bukkit version has been detected.
	 * 
	 * @param version The version string.
	 */
	public void onBukkitVersionDetected(String version) {
		if (version != null && version.toLowerCase().contains("spigot")) {
			serverType = ServerType.Spigot;
		} else {
			serverType = ServerType.Bukkit;
		}
	}

	/**
	 * When a Paper version has been detected.
	 * 
	 * @param version The version string.
	 */
	public void onPaperVersionDetected(String version) {
		serverType = ServerType.Paper;
	}
	
	/**
	 * When a Sponge version has been detected.
	 * 
	 * @param version The version string.
	 */
	public void onSpongeVersionDetected(String version) {
		
		if (version.equalsIgnoreCase("SpongeVanilla")) {
			serverType = ServerType.SpongeVanilla;
		} else if (version.equalsIgnoreCase("SpongeForge")) {
			serverType = ServerType.SpongeForge;
		}
		
	}
	
	/**
	 * When a chat message is printed to console. 
	 * Note that this is not guaranteed to be perfectly accurate due to the variety of formats across server types and plugins, 
	 * but it should work for basic messages from vanilla Minecraft and Bukkit/Spigot/Paper.
	 * 
	 * @param message Message Sent
	 * @param player The Player that sent the message
	 */
	public void onChatMessage(String message, String player) {}
	
	/**
	 * When the server is ready.
	 */
	public void onServerReady() {}
	
	/**
	 * When the server is initiated.
	 */
	public void onServerInit() {}
	
	/**
	 * When a command is sent to console by a player. 
	 * Note that the player may be empty if the server doesn't provide it, and the message may 
	 * be just the command without the leading slash.
	 * 
	 * @param message Command Sent
	 * @param player The Player that Sent the Command
	 * Bukkit and Spigot
	 */
	public void onCommandSent(String message, String player) {}
	
	// [09:07:22 INFO]: JusticePro[/127.0.0.1:51536] logged in with entity id 208 at ([world]-134.42308268456057, 72.0, 212.69999998807907)
	/**
	 * When a player joins the server. Note that the IP may be empty if the server doesn't provide it.
	 * 
	 * @param player The Player that joined.
	 * @param ip The ip of the player.
	 */
	public void onPlayerJoin(String player, String ip) {}
	
	// [11:00:47 INFO]: JusticePro lost connection: We're Under Maintenance
	/**
	 * When a player leaves the server, either by disconnecting or being kicked. 
	 * Note that the reason may be empty if the server doesn't provide one.
	 * 
	 * @param player The Player that left.
	 * @param reason The reason of disconnect
	 */
	public void onPlayerLeave(String player, String reason) {}
	
	/**
	 * Sends a command to the server console. Note that this may throw a ProcessException if the server 
	 * process isn't available (e.g. before it's detected or after it closes).
	 * 
	 * @param command Command to Send
	 * @throws ProcessException
	 */
	public void sendCommand(String command) throws ProcessException {
		
		if (SpigotGUI.server != null) {
			SpigotGUI.server.sendCommand(command);
		}else {
			throw new ProcessException();
		}
		
	}
	
	/**
	 * When the server closes.
	 */
	public void onServerClosed() {
		serverType = ServerType.Unknown;
	}
	
	/**
	 * When the module is loaded.
	 */
	public void init() {
		
		if (getPermissions() != null) {
			
			for (Permission permission : getPermissions()) {
				User.registeredPermissions.add(permission);
			}
			
		}
		
	}
	
	/**
	 * Menu Items on Module Right Click
	 */
	public JMenuItem[] getMenuItems() {
		return null;
	}

	/**
	 * Menu Items on Module Right Click when in remote admin.
	 */
	public JMenuItem[] getRemoteMenuItems(RClient client) {
		return null;
	}

	/**
	 * Custom Panel for Main Window
	 * 
	 * @return The page for your module.
	 */
	public JModulePanel getPage() {
		return null;
	}
	
	/**
	 * Custom Panel for Remote Admin Window
	 * 
	 * @param client The active client.
	 * @return The remote admin page for your module.
	 */
	public JRemoteModulePanel getRemotePage(RClient client) {
		return null;
	}
	
	/**
	 * Custom Panel for Remote Admin Window
	 * 
	 * @return The server manager page for your module.
	 */
	public JModulePanel getRemoteManagePage() {
		return null;
	}
	
	/**
	 * Registered Permissions
	 * 
	 * @return The registered permissions.
	 */
	public Permission[] getPermissions() {
		return null;
	}
	
	/**
	 * @return The website that displays to download the module.
	 */
	public String getWebsite() {
		return null;
	}
	
	/**
	 * @return The instruction manual that'll show up in the help menu.
	 */
	public String getManual() {
		return null;
	}
	
	/**
	 * Get the content from an input stream and closes the stream.
	 * @param input The stream to read.
	 * @return The content.
	 */
	public static String getContent(InputStream input) throws IOException {
		String content = "";
		
		int b1;
		while ((b1 = input.read()) != -1) {
			content = content + (char)b1;
		}
		
		input.close();
		
		return content;
	}
	
}