package com.failprooftech.servergui.RemoteAdmin;

import com.failprooftech.servergui.RemoteAdmin.Client.RClient;
import com.failprooftech.servergui.RemoteAdmin.Server.RConnection;
import com.failprooftech.servergui.RemoteAdmin.Server.RServer;

public interface PacketHandler {
	
	public void onPacketRecievedServer(Packet packet, RConnection connection, RServer server);

	public void onPacketRecievedClient(Packet packet, RClient client);
	
}