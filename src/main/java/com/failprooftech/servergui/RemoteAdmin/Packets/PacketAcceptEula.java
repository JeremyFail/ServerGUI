package com.failprooftech.servergui.RemoteAdmin.Packets;

import com.failprooftech.servergui.RemoteAdmin.Packet;

public class PacketAcceptEula extends Packet {
	
	public PacketAcceptEula(Packet packet) {
		super("acceptEula", " ");
	}
	
	public PacketAcceptEula() {
		super("acceptEula", " ");
	}
	
}