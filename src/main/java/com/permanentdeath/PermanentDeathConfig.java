package com.permanentdeath;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("permanentdeath")
public interface PermanentDeathConfig extends Config
{
	@ConfigItem(
		keyName = "pluginEnabled",
		name = "Enable Plugin",
		description = "Enable single deaths on NPCs.",
        position = 1
	)
	default boolean pluginEnabled()
    {
        return true;
    }

}
