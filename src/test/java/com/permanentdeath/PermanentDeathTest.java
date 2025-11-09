package com.permanentdeath;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PermanentDeathTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PermanentDeathPlugin.class);
		RuneLite.main(args);
	}
}