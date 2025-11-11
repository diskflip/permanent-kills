package com.permanentdeath;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
        name = "Permanent Death",
        description = "Hides monster spawns permanently after you've killed them once.",
        tags = {"ironman", "restriction", "finite", "extinction", "culling", "hider", "permanent", "death"}
)
public class PermanentDeathPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(PermanentDeathPlugin.class);

    private static final String CONFIG_GROUP = "permanentdeath";
    private static final String EXTINCT_KEY = "extinctNpcs";
    private static final String KILLS_KEY = "killCounts";
    private static final String DYING_KEY = "dyingNpcs";

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
    @Inject
    private ClientToolbar clientToolbar;

    private Map<String, Integer> totalCounts = new HashMap<>();
    private Set<Integer> extinctNpcIndices = new HashSet<>();
    private Map<String, Integer> killCounts = new LinkedHashMap<>();
    private Set<Integer> dyingNpcIndices = new HashSet<>();

    private final Set<Integer> newlyDeadNpcIndices = new HashSet<>();
    private final Set<String> newlyKilledNpcNames = new HashSet<>();

    private NPC lastTarget = null;
    private PermanentDeathPanel panel;
    private NavigationButton navButton;

    private final Hooks.RenderableDrawListener drawListener = new Hooks.RenderableDrawListener() {
        @Override
        public boolean draw(Renderable renderable, boolean drawingUI) {
            return shouldDrawNpc(renderable, drawingUI);
        }
    };

    @Override
    protected void startUp() throws Exception {
        log.info("--- Permanent Death plugin starting... ---");

        loadData();

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
    protected void shutDown() throws Exception {
        log.info("--- Permanent Death plugin shutting down. ---");
        clientToolbar.removeNavigation(navButton);
        hooks.unregisterRenderableDrawListener(drawListener);
        newlyDeadNpcIndices.clear();
        newlyKilledNpcNames.clear();
        dyingNpcIndices.clear(); // Clear new set
        lastTarget = null;
    }

    private boolean shouldDrawNpc(Renderable renderable, boolean drawingUI) {
        if (!config.pluginEnabled()) {
            return true;
        }
        if (renderable instanceof NPC) {
            NPC npc = (NPC) renderable;
            if (extinctNpcIndices.contains(npc.getIndex())) {
                return false;
            }
        }
        return true;
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event) {
        if (event.getSource() == client.getLocalPlayer()) {
            if (event.getTarget() instanceof NPC) {
                lastTarget = (NPC) event.getTarget();
                log.info("TARGET: Player is now targeting NPC: {} (Index: {}, ID: {})",
                        lastTarget.getName(), lastTarget.getIndex(), lastTarget.getId());
            } else {
                lastTarget = null;
            }
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (!config.pluginEnabled() || lastTarget == null || !(event.getActor() instanceof NPC)) {
            return;
        }

        NPC deadNpc = (NPC) event.getActor();
        log.info("DEATH: ActorDeath event for: {} (Index: {}, ID: {})",
                deadNpc.getName(), deadNpc.getIndex(), deadNpc.getId());

        if (deadNpc.getIndex() == lastTarget.getIndex()) {
            int deadNpcIndex = deadNpc.getIndex();
            String deadNpcName = deadNpc.getName();

            log.info("SUCCESS: Death matches lastTarget!");

            if (!extinctNpcIndices.contains(deadNpcIndex) && !dyingNpcIndices.contains(deadNpcIndex)) {
                dyingNpcIndices.add(deadNpcIndex);
                saveDyingList();
                log.info("TRACKING: Index {} is now dying. Will hide on despawn.", deadNpcIndex);
            }


            if (deadNpcName != null && totalCounts.containsKey(deadNpcName)) {
                newlyKilledNpcNames.add(deadNpcName);
                log.info("ADDED: Name {} to newlyKilledNpcNames.", deadNpcName);
            } else {
                log.warn("Killed NPC with name {} but it's not in our monster-totals.json map!", deadNpcName);
            }

            lastTarget = null;
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        int npcIndex = npc.getIndex();

        if (dyingNpcIndices.contains(npcIndex)) {
            log.info("DESPAWN: Dying NPC {} (Index: {}) has despawned. Adding to extinct list.",
                    npc.getName(), npcIndex);

            newlyDeadNpcIndices.add(npcIndex);

            dyingNpcIndices.remove(npcIndex);
            saveDyingList();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean dataChanged = false;

        if (!newlyDeadNpcIndices.isEmpty()) {
            extinctNpcIndices.addAll(newlyDeadNpcIndices);
            newlyDeadNpcIndices.clear();
            saveExtinctList();
            dataChanged = true;
            log.info("TICK: Processed hiding list.");
        }

        if (!newlyKilledNpcNames.isEmpty()) {
            for (String name : newlyKilledNpcNames) {
                int newCount = killCounts.getOrDefault(name, 0) + 1;

                killCounts.remove(name);
                killCounts.put(name, newCount);

                panel.updateMonsterCount(name, newCount);
                log.info("TICK: Incremented kill count for {} to {}.", name, newCount);
            }
            newlyKilledNpcNames.clear();
            saveKillCounts();
            dataChanged = true;
        }

        if (dataChanged) {
            log.info("SAVE: Data saved. Total extinct: {}, Total kill entries: {}",
                    extinctNpcIndices.size(), killCounts.size());
        }
    }

    private void saveExtinctList() {
        String json = gson.toJson(extinctNpcIndices);
        configManager.setConfiguration(CONFIG_GROUP, EXTINCT_KEY, json);
    }

    private void saveKillCounts() {
        String json = gson.toJson(killCounts);
        configManager.setConfiguration(CONFIG_GROUP, KILLS_KEY, json);
    }

    private void saveDyingList() {
        String json = gson.toJson(dyingNpcIndices);
        configManager.setConfiguration(CONFIG_GROUP, DYING_KEY, json);
    }

    private void loadData() {
        String extinctJson = configManager.getConfiguration(CONFIG_GROUP, EXTINCT_KEY);
        if (extinctJson != null) {
            try {
                extinctNpcIndices = gson.fromJson(extinctJson, new TypeToken<HashSet<Integer>>(){}.getType());
                if (extinctNpcIndices == null) extinctNpcIndices = new HashSet<>();
                log.info("LOAD: Loaded {} extinct NPC indices.", extinctNpcIndices.size());
            } catch (Exception e) {
                log.warn("LOAD: Corrupt extinct list found, resetting.", e);
                extinctNpcIndices = new HashSet<>();
            }
        } else {
            log.info("LOAD: No extinct list found. Initializing new list.");
            extinctNpcIndices = new HashSet<>();
        }

        String dyingJson = configManager.getConfiguration(CONFIG_GROUP, DYING_KEY);
        Set<Integer> loadedDyingIndices = new HashSet<>();
        if (dyingJson != null) {
            try {
                loadedDyingIndices = gson.fromJson(dyingJson, new TypeToken<HashSet<Integer>>(){}.getType());
                if (loadedDyingIndices == null) loadedDyingIndices = new HashSet<>();
                log.info("LOAD: Found {} indices that were dying on last logout.", loadedDyingIndices.size());
            } catch (Exception e) {
                log.warn("LOAD: Corrupt dying list found, resetting.", e);
                loadedDyingIndices = new HashSet<>();
            }
        }

        if (!loadedDyingIndices.isEmpty()) {
            extinctNpcIndices.addAll(loadedDyingIndices);
            log.info("LOAD: Moved {} dying indices to the main extinct list.", loadedDyingIndices.size());
        }

        dyingNpcIndices = new HashSet<>();
        saveDyingList();

        String killsJson = configManager.getConfiguration(CONFIG_GROUP, KILLS_KEY);
        if (killsJson != null) {
            try {
                Type type = new TypeToken<LinkedHashMap<String, Integer>>(){}.getType();
                killCounts = gson.fromJson(killsJson, type);
                if (killCounts == null) killCounts = new LinkedHashMap<>();
                log.info("LOAD: Loaded {} monster kill count entries.", killCounts.size());
            } catch (Exception e) {
                log.warn("LOAD: Corrupt kill count map found, resetting.", e);
                killCounts = new LinkedHashMap<>();
            }
        } else {
            log.info("LOAD: No kill count map found. Initializing new map.");
            killCounts = new LinkedHashMap<>();
        }

        try (InputStream in = getClass().getResourceAsStream("/monster-totals.json")) {
            if (in == null) {
                log.error("LOAD: monster-totals.json not found in resources! Panel will be empty.");
                return;
            }
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            totalCounts = gson.fromJson(new InputStreamReader(in), type);
            log.info("LOAD: Loaded {} total counts from monster-totals.json.", totalCounts.size());
        } catch (Exception e) {
            log.error("LOAD: Failed to load monster-totals.json", e);
        }
    }

    @Provides
    PermanentDeathConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PermanentDeathConfig.class);
    }
}