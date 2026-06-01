package com.failprooftech.servergui.Api.Player;

import com.failprooftech.servergui.Module;
import com.failprooftech.servergui.ProcessException;
import com.failprooftech.servergui.Api.ServerType;

public class Player {
	
	/**
	 * The player's name.
	 */
	private String name;
	
	/**
	 * The type of the server.
	 */
	private ServerType type;
	
	/**
	 * Module to send commands through.
	 */
	private Module module;
	
	
	/**
	 * The 'Player' class is for making player commands easier for several versions.
	 * @param name The player's name.
	 * @param version The version of the server.
	 * @param type The type of the server.
	 * @param module Module to send commands through.
	 */
	public Player(String name, ServerType type, Module module) {
		this.name = name;
		this.type = type;
		this.module = module;
	}
	
	/**
	 * @return The player's name.
	 */
	public String getName() {
		return name;
	}

	/**	
	 * @return The type of the server.
	 */
	public ServerType getType() {
		return type;
	}
	
	/**
	 * Set the gamemode for the player.
	 * @param gamemode The gamemode for the player.
	 * @throws ProcessException 
	 */
	public void setGamemode(Gamemode gamemode) throws ProcessException {
		module.sendCommand("gamemode " + gamemode.getFull() + " " + getName());
	}
	
}