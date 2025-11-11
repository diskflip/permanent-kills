package com.permanentdeath;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.api.coords.WorldPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
        name = "Permanent Death",
        description = "Hides monster spawns permanently after you've killed them once.",
        tags = {"ironman", "restriction", "finite", "extinction", "culling", "hider", "permanent", "death"}
)
public class PermanentDeathPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(PermanentDeathPlugin.class);
    private static final String CONFIG_GROUP = "permanentdeath";
    private static final String EXTINCT_KEY = "extinctNpcs";
    private static final String KILLS_KEY = "killCounts";
    private static final String DYING_KEY = "dyingNpcs";
    private static final String EXTINCT_ORES_KEY = "extinctOres";

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private PermanentDeathConfig config;
    @Inject private Hooks hooks;
    @Inject private Gson gson;
    @Inject private ClientToolbar clientToolbar;

    private Map<String, Integer> totalCounts = new HashMap<>();
    private Set<Integer> extinctNpcIndices = new HashSet<>();
    private Map<String, Integer> killCounts = new LinkedHashMap<>();
    private Set<Integer> dyingNpcIndices = new HashSet<>();
    private final Set<Integer> newlyDeadNpcIndices = new HashSet<>();
    private final Set<String> newlyKilledNpcNames = new HashSet<>();
    private Set<String> extinctOreLocations = new HashSet<>();

    private NPC lastTarget = null;
    private PermanentDeathPanel panel;
    private NavigationButton navButton;

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
        loadData();
        loadExtinctOres();
        panel = new PermanentDeathPanel(totalCounts);
        Set<String> allNames = new TreeSet<>(totalCounts.keySet());
        List<String> killedNames = new ArrayList<>(killCounts.keySet());
        Collections.reverse(killedNames);
        Set<String> sortedNames = new LinkedHashSet<>();
        sortedNames.addAll(killedNames);
        allNames.removeAll(sortedNames);
        sortedNames.addAll(allNames);
        panel.buildList(sortedNames, killCounts);
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Finite Tracker")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
        hooks.registerRenderableDrawListener(drawListener);
    }

    @Override
    protected void shutDown() throws Exception
    {
        clientToolbar.removeNavigation(navButton);
        hooks.unregisterRenderableDrawListener(drawListener);
        newlyDeadNpcIndices.clear();
        newlyKilledNpcNames.clear();
        dyingNpcIndices.clear();
        lastTarget = null;
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
            if (extinctNpcIndices.contains(npc.getIndex()))
            {
                return false;
            }
        }
        return true;
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (event.getSource() == client.getLocalPlayer())
        {
            if (event.getTarget() instanceof NPC)
            {
                lastTarget = (NPC) event.getTarget();
            }
            else
            {
                lastTarget = null;
            }
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (!config.pluginEnabled() || lastTarget == null || !(event.getActor() instanceof NPC))
        {
            return;
        }

        NPC deadNpc = (NPC) event.getActor();
        if (deadNpc.getIndex() == lastTarget.getIndex())
        {
            int deadNpcIndex = deadNpc.getIndex();
            String deadNpcName = deadNpc.getName();

            if (!extinctNpcIndices.contains(deadNpcIndex) && !dyingNpcIndices.contains(deadNpcIndex))
            {
                dyingNpcIndices.add(deadNpcIndex);
                saveDyingList();
            }

            if (deadNpcName != null && totalCounts.containsKey(deadNpcName))
            {
                newlyKilledNpcNames.add(deadNpcName);
            }

            lastTarget = null;
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        NPC npc = event.getNpc();
        int npcIndex = npc.getIndex();

        if (dyingNpcIndices.contains(npcIndex))
        {
            newlyDeadNpcIndices.add(npcIndex);
            dyingNpcIndices.remove(npcIndex);
            saveDyingList();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        boolean dataChanged = false;

        if (!newlyDeadNpcIndices.isEmpty())
        {
            extinctNpcIndices.addAll(newlyDeadNpcIndices);
            newlyDeadNpcIndices.clear();
            saveExtinctList();
            dataChanged = true;
        }

        if (!newlyKilledNpcNames.isEmpty())
        {
            for (String name : newlyKilledNpcNames)
            {
                int newCount = killCounts.getOrDefault(name, 0) + 1;
                killCounts.remove(name);
                killCounts.put(name, newCount);
                panel.updateMonsterCount(name, newCount);
            }
            newlyKilledNpcNames.clear();
            saveKillCounts();
            dataChanged = true;
        }

        if (dataChanged)
        {
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        if (!config.pluginEnabled())
            return;

        GameObject obj = event.getGameObject();
        String name = client.getObjectDefinition(obj.getId()).getName();
        if (name == null)
            return;

        WorldPoint wp = WorldPoint.fromLocalInstance(client, obj.getLocalLocation());
        String key = wp.getX() + "," + wp.getY() + "," + wp.getPlane();

        if (name.toLowerCase().contains("rock") && !name.toLowerCase().contains("depleted"))
        {
            if (!extinctOreLocations.contains(key))
            {
                extinctOreLocations.add(key);
                saveExtinctOres();
            }
        }

        if (name.toLowerCase().contains("depleted") && extinctOreLocations.contains(key))
        {
            client.getScene().removeGameObject(obj);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (!config.pluginEnabled())
            return;

        GameObject obj = event.getGameObject();
        String name = client.getObjectDefinition(obj.getId()).getName();
        if (name == null)
            return;

        WorldPoint wp = WorldPoint.fromLocalInstance(client, obj.getLocalLocation());
        String key = wp.getX() + "," + wp.getY() + "," + wp.getPlane();

        if (name.toLowerCase().contains("rock") && !name.toLowerCase().contains("depleted"))
        {
            if (extinctOreLocations.contains(key))
            {
                client.getScene().removeGameObject(obj);
            }
        }
    }

    private void saveExtinctList()
    {
        String json = gson.toJson(extinctNpcIndices);
        configManager.setConfiguration(CONFIG_GROUP, EXTINCT_KEY, json);
    }

    private void saveKillCounts()
    {
        String json = gson.toJson(killCounts);
        configManager.setConfiguration(CONFIG_GROUP, KILLS_KEY, json);
    }

    private void saveDyingList()
    {
        String json = gson.toJson(dyingNpcIndices);
        configManager.setConfiguration(CONFIG_GROUP, DYING_KEY, json);
    }

    private void saveExtinctOres()
    {
        String json = gson.toJson(extinctOreLocations);
        configManager.setConfiguration(CONFIG_GROUP, EXTINCT_ORES_KEY, json);
    }

    private void loadExtinctOres()
    {
        String json = configManager.getConfiguration(CONFIG_GROUP, EXTINCT_ORES_KEY);
        if (json != null)
        {
            extinctOreLocations = gson.fromJson(json, new TypeToken<HashSet<String>>(){}.getType());
            if (extinctOreLocations == null) extinctOreLocations = new HashSet<>();
        }
        else
        {
            extinctOreLocations = new HashSet<>();
        }
    }

    private void loadData()
    {
        String extinctJson = configManager.getConfiguration(CONFIG_GROUP, EXTINCT_KEY);
        if (extinctJson != null)
        {
            try
            {
                extinctNpcIndices = gson.fromJson(extinctJson, new TypeToken<HashSet<Integer>>(){}.getType());
                if (extinctNpcIndices == null) extinctNpcIndices = new HashSet<>();
            }
            catch (Exception e)
            {
                extinctNpcIndices = new HashSet<>();
            }
        }
        else
        {
            extinctNpcIndices = new HashSet<>();
        }

        String dyingJson = configManager.getConfiguration(CONFIG_GROUP, DYING_KEY);
        Set<Integer> loadedDyingIndices = new HashSet<>();
        if (dyingJson != null)
        {
            try
            {
                loadedDyingIndices = gson.fromJson(dyingJson, new TypeToken<HashSet<Integer>>(){}.getType());
                if (loadedDyingIndices == null) loadedDyingIndices = new HashSet<>();
            }
            catch (Exception e)
            {
                loadedDyingIndices = new HashSet<>();
            }
        }

        if (!loadedDyingIndices.isEmpty())
        {
            extinctNpcIndices.addAll(loadedDyingIndices);
        }

        dyingNpcIndices = new HashSet<>();
        saveDyingList();

        String killsJson = configManager.getConfiguration(CONFIG_GROUP, KILLS_KEY);
        if (killsJson != null)
        {
            try
            {
                Type type = new TypeToken<LinkedHashMap<String, Integer>>(){}.getType();
                killCounts = gson.fromJson(killsJson, type);
                if (killCounts == null) killCounts = new LinkedHashMap<>();
            }
            catch (Exception e)
            {
                killCounts = new LinkedHashMap<>();
            }
        }
        else
        {
            killCounts = new LinkedHashMap<>();
        }

        try (InputStream in = getClass().getResourceAsStream("/monster-totals.json"))
        {
            if (in == null)
            {
                return;
            }
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            totalCounts = gson.fromJson(new InputStreamReader(in), type);
        }
        catch (Exception e)
        {
        }
    }

    @Provides
    PermanentDeathConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PermanentDeathConfig.class);
    }
}
