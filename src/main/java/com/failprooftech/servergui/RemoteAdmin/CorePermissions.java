package com.failprooftech.servergui.RemoteAdmin;

public class CorePermissions {
	
	public static final Permission CONSOLE_READ = new Permission("Console Read", "servergui.core.console.read");
	public static final Permission CONSOLE_SEND = new Permission("Console Send", "servergui.core.console.send");
	
	public static final Permission CHAT_READ = new Permission("Chat Read", "servergui.core.chat.read");
	public static final Permission CHAT_SEND = new Permission("Chat Send", "servergui.core.chat.send");
	
	public static final Permission ADMIN = new Permission("Server Admin", "servergui.core.server.admin");
	public static final Permission ALLOW_BOT = new Permission("Allow Bots", "servergui.core.server.allowbot");
	
}