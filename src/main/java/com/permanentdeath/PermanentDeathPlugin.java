package com.permanentdeath;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.callback.Hooks; // The Hider Hook
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;


@PluginDescriptor(
        name = "Permanent Death",
        description = "Hides monster spawns permanently after you've killed them once.",
        tags = {"ironman", "restriction", "finite", "extinction", "culling", "hider", "permanent", "death"}
)
public class PermanentDeathPlugin extends Plugin
{
    private static final String CONFIG_GROUP = "permanentdeath";
    private static final String EXTINCT_KEY = "extinctNpcs";

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private PermanentDeathConfig config;

    @Inject
    private Hooks hooks;

    @Inject
    private Gson gson;

    private Set<String> extinctNpcKeys = new HashSet<>();

    private final Hooks.RenderableDrawListener drawListener = new Hooks.RenderableDrawListener()
    {
        @Override
        public boolean draw(Renderable renderable, boolean drawingUI)
        {
            return shouldDrawNpc(renderable, drawingUI);
        }
    };

    @Override
    protected void startUp() throws Exception
    {
        hooks.registerRenderableDrawListener(drawListener);
    }

    @Override
    protected void shutDown() throws Exception
    {
        hooks.unregisterRenderableDrawListener(drawListener);
    }

    private boolean shouldDrawNpc(Renderable renderable, boolean drawingUI)
    {
        if (!config.pluginEnabled())
        {
            return true;
        }


        if (renderable instanceof NPC)
        {
            NPC npc = (NPC) renderable;

            boolean isAttackable = false;
            String[] actions = npc.getComposition().getActions();

            if (actions != null)
            {
                for (String action : actions)
                {
                    if (action != null && action.equals("Attack"))
                    {
                        isAttackable = true;
                        break;
                    }
                }
            }
            if (isAttackable)
            {
                return false;
            }
        }
        return true;
    }



	/*
	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (!config.pluginEnabled())
		{
			return;
		}

		NPC npc = event.getNpc();

		if (npc.isDead())
		{
			String key = generateKey(npc);
			if (!extinctNpcKeys.contains(key))
			{
				extinctNpcKeys.add(key);
				saveData();
			}
		}
	}

	private String generateKey(NPC npc)
	{
		WorldPoint location = npc.getWorldLocation();
		return npc.getId() + "_" + location.getX() + "_" + location.getY() + "_" + location.getPlane();
	}

	private void saveData()
	{
		String json = gson.toJson(extinctNpcKeys);
		configManager.setConfiguration(CONFIG_GROUP, EXTINCT_KEY, json);
	}

	private void loadData()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, EXTINCT_KEY);
		if (json != null)
		{
			extinctNpcKeys = gson.fromJson(json, new TypeToken<HashSet<String>>(){}.getType());
		}
	}
	*/

    @Provides
    PermanentDeathConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PermanentDeathConfig.class);
    }
}