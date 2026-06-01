package com.failprooftech.servergui.RemoteAdmin.Packets;

import com.failprooftech.servergui.RemoteAdmin.Packet;

public class PacketConsole extends Packet {
	
	private String message;
	
	public PacketConsole(Packet packet) {
		super("console_send", packet.getData());
		this.message = packet.getData();
	}
	
	public PacketConsole(String message) {
		super("console_send", message);
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
	
}