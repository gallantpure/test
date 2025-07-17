/*
 * Copyright (c) 2019, Jacky <liangj97@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.inferno;

import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.example.EthanApiPlugin.EthanApiPlugin;
import com.google.inject.Provides;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.MenuAction;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.Constants;
import net.runelite.client.eventbus.Subscribe;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import net.runelite.client.eventbus.EventBus;
import net.runelite.api.ChatMessageType;
import net.runelite.client.plugins.PrayAgainstPlayer.RecommendedPrayerChangedEvent;
import net.runelite.client.plugins.lucidplugins.api.utils.MessageUtils;
import net.runelite.client.plugins.lucidplugins.api.utils.NpcUtils;
import net.runelite.client.plugins.inferno.displaymodes.InfernoPrayerDisplayMode;
import net.runelite.client.plugins.inferno.displaymodes.InfernoSafespotDisplayMode;
import net.runelite.client.plugins.inferno.displaymodes.InfernoWaveDisplayMode;
import net.runelite.client.plugins.inferno.displaymodes.InfernoZukShieldDisplayMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.NPCManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.apache.commons.lang3.ArrayUtils;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;

@Extension
@PluginDescriptor(
        name = "Inferno",
        enabledByDefault = false,
        description = "Inferno helper",
        tags = {"combat", "overlay", "pve", "pvm"}
)
@Slf4j
public class InfernoPlugin extends Plugin implements ExtensionPoint
{
    private static final int INFERNO_REGION = 9043;

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private InfoBoxManager infoBoxManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private NPCManager npcManager;

    @Inject
    private InfernoOverlay infernoOverlay;

    @Inject
    private EventBus eventBus;

    @Inject
    private InfernoWaveOverlay waveOverlay;

    @Inject
    private InfernoInfoBoxOverlay jadOverlay;

    @Inject
    private InfernoOverlay prayerOverlay;

    @Inject
    private AttackTimerOverlay attackTimerOverlay;

    @Inject
    private InfernoConfig config;

    @Getter(AccessLevel.PACKAGE)
    private InfernoConfig.FontStyle fontStyle = InfernoConfig.FontStyle.BOLD;
    @Getter(AccessLevel.PACKAGE)
    private int textSize = 32;

    private WorldPoint lastLocation = new WorldPoint(0, 0, 0);

    @Getter(AccessLevel.PACKAGE)
    private int currentWaveNumber;

    @Getter(AccessLevel.PACKAGE)
    private final List<InfernoNPC> infernoNpcs = new ArrayList<>();

    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, Map<InfernoNPC.Attack, Integer>> upcomingAttacks = new HashMap<>();
    @Getter(AccessLevel.PACKAGE)
    private InfernoNPC.Attack closestAttack = null;

    // ===== ENHANCED PRAYER SYSTEM: Enhanced Threat Tracking =====
    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, List<InfernoNPC>> attacksByTick = new HashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final List<InfernoNPC> simultaneousAttackers = new ArrayList<>();

    @Getter(AccessLevel.PACKAGE)
    private boolean emergencyPrayerSwitching = false;

    @Getter(AccessLevel.PACKAGE)
    private Prayer emergencyPrayer = null;

    @Getter(AccessLevel.PACKAGE)
    private final Set<WorldPoint> dangerousTiles = new HashSet<>();

    @Getter(AccessLevel.PACKAGE)
    private final Map<WorldPoint, Set<InfernoNPC>> tileThreats = new HashMap<>();
    // ============================================================

    @Getter(AccessLevel.PACKAGE)
    private final List<WorldPoint> obstacles = new ArrayList<>();

    @Getter(AccessLevel.PACKAGE)
    private boolean finalPhase = false;
    private boolean finalPhaseTick = false;
    private int ticksSinceFinalPhase = 0;
    @Getter(AccessLevel.PACKAGE)
    private NPC zukShield = null;
    private NPC zuk = null;
    private WorldPoint zukShieldLastPosition = null;
    private WorldPoint zukShieldBase = null;
    private int zukShieldCornerTicks = -2;

    private int zukShieldNegativeXCoord = -1;
    private int zukShieldPositiveXCoord = -1;
    private int zukShieldLastNonZeroDelta = 0;
    private int zukShieldLastDelta = 0;
    private int zukShieldTicksLeftInCorner = -1;

    @Getter(AccessLevel.PACKAGE)
    private InfernoNPC centralNibbler = null;

    // 0 = total safespot
    // 1 = pray melee
    // 2 = pray range
    // 3 = pray magic
    // 4 = pray melee, range
    // 5 = pray melee, magic
    // 6 = pray range, magic
    // 7 = pray all
    @Getter(AccessLevel.PACKAGE)
    private final Map<WorldPoint, Integer> safeSpotMap = new HashMap<>();
    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, List<WorldPoint>> safeSpotAreas = new HashMap<>();

    @Getter(AccessLevel.PACKAGE)
    List<InfernoBlobDeathSpot> blobDeathSpots = new ArrayList<>();

    @Getter(AccessLevel.PACKAGE)
    private long lastTick;

    private InfernoSpawnTimerInfobox spawnTimerInfoBox;

    // Add at class level for click prayer system:
    private String lastRecommendedPrayer = null;

    public static final int JAL_NIB = 7574;
    public static final int JAL_MEJRAH = 7578;
    public static final int JAL_MEJRAH_STAND = 7577;
    public static final int JAL_AK_RANGE_ATTACK = 7581;
    public static final int JAL_AK_MELEE_ATTACK = 7582;
    public static final int JAL_AK_MAGIC_ATTACK = 7583;
    public static final int JAL_IMKOT = 7597;
    public static final int JAL_XIL_MELEE_ATTACK = 7604;
    public static final int JAL_XIL_RANGE_ATTACK = 7605;
    public static final int JAL_ZEK_MAGE_ATTACK = 7610;
    public static final int JAL_ZEK_MELEE_ATTACK = 7612;
    public static final int JALTOK_JAD_MAGE_ATTACK = 7592;
    public static final int JALTOK_JAD_RANGE_ATTACK = 7593;
    public static final int TZKAL_ZUK = 7566;

    @Provides
    InfernoConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(InfernoConfig.class);
    }

    @Override
    protected void startUp()
    {
        waveOverlay.setDisplayMode(config.waveDisplay());
        waveOverlay.setWaveHeaderColor(config.getWaveOverlayHeaderColor());
        waveOverlay.setWaveTextColor(config.getWaveTextColor());

        if (isInInferno())
        {
            overlayManager.add(infernoOverlay);

            if (config.waveDisplay() != InfernoWaveDisplayMode.NONE)
            {
                overlayManager.add(waveOverlay);
            }

            overlayManager.add(jadOverlay);
            overlayManager.add(prayerOverlay);
            overlayManager.add(attackTimerOverlay);
        }
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(infernoOverlay);
        overlayManager.remove(waveOverlay);
        overlayManager.remove(jadOverlay);
        overlayManager.remove(prayerOverlay);
        overlayManager.remove(attackTimerOverlay);

        infoBoxManager.removeInfoBox(spawnTimerInfoBox);
        currentWaveNumber = -1;
    }

    // ===== CLICK PRAYER SYSTEM =====
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!isInInferno() || !"Walk here".equals(event.getMenuOption()))
        {
            return;
        }

        if (config.spawnTimerDebug())
        {
            spawnDebug("=== WALK CLICK DEBUG ===");
            spawnDebug("Checking " + safeSpotMap.size() + " safespot tiles");
        }

        // Check all safespot tiles to see if any match the clicked coordinates
        for (Map.Entry<WorldPoint, Integer> entry : safeSpotMap.entrySet())
        {
            WorldPoint safespotTile = entry.getKey();
            Integer safespotValue = entry.getValue();

            // Convert safespot world point to scene coordinates
            if (isClickedTile(safespotTile, event.getParam0(), event.getParam1()))
            {
                if (config.spawnTimerDebug())
                {
                    spawnDebug("CLICKED SAFESPOT: " + safespotTile + " with value: " + safespotValue);
                }

                processClickedSafespot(safespotTile);
                return;
            }
        }

        if (config.spawnTimerDebug())
        {
            spawnDebug("No safespot tile matched click coordinates");
        }
    }

    private boolean isClickedTile(WorldPoint safespotTile, int clickParam0, int clickParam1)
    {
        try
        {
            // For "Walk here" actions, param0 and param1 are world coordinates
            return safespotTile.getX() == clickParam0 && safespotTile.getY() == clickParam1;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private void processClickedSafespot(WorldPoint clickedTile)
    {
        Integer safespotValue = safeSpotMap.get(clickedTile);

        if (config.spawnTimerDebug())
        {
            spawnDebug("PROCESSING CLICKED TILE: " + clickedTile +
                    " with safespot value: " + safespotValue);
        }

        if (safespotValue != null && safespotValue > 0)
        {
            Prayer recommendedPrayer = getPrayerForSafespotValue(safespotValue);
            if (recommendedPrayer != null)
            {
                String recommendation = toRecommendationString(recommendedPrayer);

                // Force immediate prayer recommendation
                eventBus.post(new RecommendedPrayerChangedEvent(recommendation));
                lastRecommendedPrayer = recommendation;

                if (config.spawnTimerDebug())
                {
                    spawnDebug("PRAYER RECOMMENDATION SENT: " + recommendation);
                }
            }
            else if (config.spawnTimerDebug())
            {
                spawnDebug("NO PRAYER NEEDED for safespot value: " + safespotValue);
            }
        }
        else if (safespotValue != null && safespotValue == 0)
        {
            // Safe tile - send "none" prayer
            eventBus.post(new RecommendedPrayerChangedEvent("none"));
            lastRecommendedPrayer = "none";

            if (config.spawnTimerDebug())
            {
                spawnDebug("SAFE TILE - Prayer set to none");
            }
        }
        else if (config.spawnTimerDebug())
        {
            spawnDebug("SAFESPOT VALUE IS NULL: " + safespotValue);
        }
    }

    private Prayer getPrayerForSafespotValue(int safespotValue)
    {
        switch (safespotValue)
        {
            case 1: // Melee only
                return Prayer.PROTECT_FROM_MELEE;
            case 2: // Range only
                return Prayer.PROTECT_FROM_MISSILES;
            case 3: // Magic only
                return Prayer.PROTECT_FROM_MAGIC;
            case 4: // Melee + Range - choose based on priority
                return getPriorityPrayerForMeleeRange();
            case 5: // Melee + Magic - choose based on priority
                return getPriorityPrayerForMeleeMagic();
            case 6: // Range + Magic - choose based on priority
                return getPriorityPrayerForRangeMagic();
            case 7: // All threats - choose highest priority
                return getPriorityPrayerForAllThreats();
            default:
                return null; // Safe tile (case 0)
        }
    }

    private Prayer getPriorityPrayerForMeleeRange()
    {
        // Check current NPCs that can attack this tile to determine priority
        List<InfernoNPC> meleeThreats = new ArrayList<>();
        List<InfernoNPC> rangeThreats = new ArrayList<>();

        for (InfernoNPC npc : infernoNpcs)
        {
            if (!isPrayerHelper(npc) || npc.getNpc().isDead()) continue;

            if (npc.getType().getDefaultAttack() == InfernoNPC.Attack.MELEE)
            {
                meleeThreats.add(npc);
            }
            else if (npc.getType().getDefaultAttack() == InfernoNPC.Attack.RANGED)
            {
                rangeThreats.add(npc);
            }
        }

        // Prioritize based on immediate threats and damage potential
        if (!meleeThreats.isEmpty() && !rangeThreats.isEmpty())
        {
            // If both present, prioritize based on attack timing
            int meleeMinTicks = meleeThreats.stream().mapToInt(InfernoNPC::getTicksTillNextAttack).min().orElse(999);
            int rangeMinTicks = rangeThreats.stream().mapToInt(InfernoNPC::getTicksTillNextAttack).min().orElse(999);

            return meleeMinTicks <= rangeMinTicks ? Prayer.PROTECT_FROM_MELEE : Prayer.PROTECT_FROM_MISSILES;
        }
        else if (!meleeThreats.isEmpty())
        {
            return Prayer.PROTECT_FROM_MELEE;
        }
        else if (!rangeThreats.isEmpty())
        {
            return Prayer.PROTECT_FROM_MISSILES;
        }

        // Default to melee if no specific threats found
        return Prayer.PROTECT_FROM_MELEE;
    }

    private Prayer getPriorityPrayerForMeleeMagic()
    {
        // Similar logic for melee + magic threats
        List<InfernoNPC> meleeThreats = new ArrayList<>();
        List<InfernoNPC> magicThreats = new ArrayList<>();

        for (InfernoNPC npc : infernoNpcs)
        {
            if (!isPrayerHelper(npc) || npc.getNpc().isDead()) continue;

            if (npc.getType().getDefaultAttack() == InfernoNPC.Attack.MELEE)
            {
                meleeThreats.add(npc);
            }
            else if (npc.getType().getDefaultAttack() == InfernoNPC.Attack.MAGIC)
            {
                magicThreats.add(npc);
            }
        }

        if (!meleeThreats.isEmpty() && !magicThreats.isEmpty())
        {
            int meleeMinTicks = meleeThreats.stream().mapToInt(InfernoNPC::getTicksTillNextAttack).min().orElse(999);
            int magicMinTicks = magicThreats.stream().mapToInt(InfernoNPC::getTicksTillNextAttack).min().orElse(999);

            return meleeMinTicks <= magicMinTicks ? Prayer.PROTECT_FROM_MELEE : Prayer.PROTECT_FROM_MAGIC;
        }
        else if (!meleeThreats.isEmpty())
        {
            return Prayer.PROTECT_FROM_MELEE;
        }
        else if (!magicThreats.isEmpty())
        {
            return Prayer.PROTECT_FROM_MAGIC;
        }

        return Prayer.PROTECT_FROM_MAGIC; // Default to magic for blobs
    }

    private Prayer getPriorityPrayerForRangeMagic()
    {
        // Similar logic for range + magic threats
        List<InfernoNPC> rangeThreats = new ArrayList<>();
        List<InfernoNPC> magicThreats = new ArrayList<>();

        for (InfernoNPC npc : infernoNpcs)
        {
            if (!isPrayerHelper(npc) || npc.getNpc().isDead()) continue;

            if (npc.getType().getDefaultAttack() == InfernoNPC.Attack.RANGED)
            {
                rangeThreats.add(npc);
            }
            else if (npc.getType().getDefaultAttack() == InfernoNPC.Attack.MAGIC)
            {
                magicThreats.add(npc);
            }
        }

        if (!rangeThreats.isEmpty() && !magicThreats.isEmpty())
        {
            int rangeMinTicks = rangeThreats.stream().mapToInt(InfernoNPC::getTicksTillNextAttack).min().orElse(999);
            int magicMinTicks = magicThreats.stream().mapToInt(InfernoNPC::getTicksTillNextAttack).min().orElse(999);

            return rangeMinTicks <= magicMinTicks ? Prayer.PROTECT_FROM_MISSILES : Prayer.PROTECT_FROM_MAGIC;
        }
        else if (!rangeThreats.isEmpty())
        {
            return Prayer.PROTECT_FROM_MISSILES;
        }
        else if (!magicThreats.isEmpty())
        {
            return Prayer.PROTECT_FROM_MAGIC;
        }

        return Prayer.PROTECT_FROM_MAGIC; // Default to magic
    }

    private Prayer getPriorityPrayerForAllThreats()
    {
        // For all threats, prioritize based on immediate danger and damage
        Prayer emergencyPrayer = calculateEmergencyPrayer(new ArrayList<>(infernoNpcs.stream()
                .filter(npc -> isPrayerHelper(npc) && !npc.getNpc().isDead())
                .collect(java.util.stream.Collectors.toList())));

        return emergencyPrayer != null ? emergencyPrayer : Prayer.PROTECT_FROM_MAGIC;
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event)
    {
        if (!"inferno".equals(event.getGroup()))
        {
            return;
        }

        if (event.getKey().endsWith("color"))
        {
            waveOverlay.setWaveHeaderColor(config.getWaveOverlayHeaderColor());
            waveOverlay.setWaveTextColor(config.getWaveTextColor());
        }
        else if ("waveDisplay".equals(event.getKey()))
        {
            overlayManager.remove(waveOverlay);

            waveOverlay.setDisplayMode(config.waveDisplay());

            if (isInInferno() && config.waveDisplay() != InfernoWaveDisplayMode.NONE)
            {
                overlayManager.add(waveOverlay);
            }
        }
    }

    @Subscribe
    private void onGameTick(GameTick event)
    {
        if (!isInInferno())
        {
            return;
        }

        lastTick = System.currentTimeMillis();

        upcomingAttacks.clear();
        calculateUpcomingAttacks();

        // ===== ENHANCED PRAYER SYSTEM: Enhanced Prayer Priority Calculation =====
        if (config.showAttackTimerOverlay() || config.proactiveThreatDetection())
        {
            calculateEnhancedPrayerPriority();
        }
        // ========================================================================

        closestAttack = null;
        calculateClosestAttack();

        // ===== ENHANCED PRAYER SYSTEM: Proactive Threat Detection =====
        if (config.proactiveThreatDetection())
        {
            detectMovementThreats();
        }
        // ==============================================================

        doPraying();

        safeSpotMap.clear();
        calculateSafespots();
        safeSpotAreas.clear();
        calculateSafespotAreas();

        obstacles.clear();
        calculateObstacles();

        centralNibbler = null;
        calculateCentralNibbler();

        calculateSpawnTimerInfobox();

        manageBlobDeathLocations();

        //if finalPhaseTick, we will skip incrementing because we already did it in onNpcSpawned
        if (finalPhaseTick)
        {
            finalPhaseTick = false;
        }
        else if (finalPhase)
        {
            ticksSinceFinalPhase++;
        }
    }

    // ===== ENHANCED PRAYER SYSTEM: Enhanced Prayer Priority Calculation =====
    private void calculateEnhancedPrayerPriority()
    {
        attacksByTick.clear();
        simultaneousAttackers.clear();

        // Group attacks by tick
        for (InfernoNPC npc : infernoNpcs)
        {
            if (npc.getTicksTillNextAttack() > 0 && isPrayerHelper(npc))
            {
                attacksByTick.computeIfAbsent(npc.getTicksTillNextAttack(), k -> new ArrayList<>())
                        .add(npc);
            }
        }

        // Identify simultaneous attacks within threshold
        int threshold = config.simultaneousAttackThreshold();
        for (Map.Entry<Integer, List<InfernoNPC>> entry : attacksByTick.entrySet())
        {
            int tick = entry.getKey();
            List<InfernoNPC> npcsOnTick = entry.getValue();

            // Check for NPCs attacking on same tick
            if (npcsOnTick.size() > 1)
            {
                simultaneousAttackers.addAll(npcsOnTick);
            }

            // Check for NPCs attacking within threshold of each other
            for (int i = 1; i <= threshold; i++)
            {
                List<InfernoNPC> nearbyTickNPCs = attacksByTick.get(tick + i);
                if (nearbyTickNPCs != null && !nearbyTickNPCs.isEmpty())
                {
                    if (npcsOnTick.size() > 0)
                    {
                        simultaneousAttackers.addAll(npcsOnTick);
                        simultaneousAttackers.addAll(nearbyTickNPCs);
                    }
                }
            }
        }
    }
    // ========================================================================

    // ===== ENHANCED PRAYER SYSTEM: Proactive Threat Detection =====
    private void detectMovementThreats()
    {
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        dangerousTiles.clear();
        tileThreats.clear();

        // Check adjacent tiles for new threats
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                if (dx == 0 && dy == 0) continue; // Skip current position

                WorldPoint adjacentTile = playerPos.dx(dx).dy(dy);
                detectThreatsForPosition(adjacentTile);
            }
        }
    }

    private void detectThreatsForPosition(WorldPoint position)
    {
        Set<InfernoNPC> newThreats = new HashSet<>();
        Set<InfernoNPC> currentThreats = getCurrentThreats();

        for (InfernoNPC npc : infernoNpcs)
        {
            if (!isPrayerHelper(npc)) continue;

            // Check if this NPC can attack the given position but not current position
            boolean canAttackPosition = npc.canAttack(client, position);
            boolean canAttackCurrent = currentThreats.contains(npc);

            if (canAttackPosition && !canAttackCurrent)
            {
                newThreats.add(npc);
            }
        }

        if (!newThreats.isEmpty())
        {
            dangerousTiles.add(position);
            tileThreats.put(position, newThreats);

            // Check if player is about to move to a dangerous position
            if (config.emergencyPrayerSwitching())
            {
                handleMovementThreatChange(newThreats);
            }
        }
    }

    private Set<InfernoNPC> getCurrentThreats()
    {
        Set<InfernoNPC> currentThreats = new HashSet<>();
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();

        for (InfernoNPC npc : infernoNpcs)
        {
            if (isPrayerHelper(npc) && npc.canAttack(client, playerPos))
            {
                currentThreats.add(npc);
            }
        }

        return currentThreats;
    }

    private void handleMovementThreatChange(Set<InfernoNPC> newThreats)
    {
        Prayer emergency = calculateEmergencyPrayer(new ArrayList<>(newThreats));
        if (emergency != null && emergency != emergencyPrayer)
        {
            emergencyPrayer = emergency;
            emergencyPrayerSwitching = true;

            // Send emergency prayer recommendation immediately
            String recommendation = toRecommendationString(emergency);
            eventBus.post(new RecommendedPrayerChangedEvent(recommendation));
            lastRecommendedPrayer = recommendation;
        }
    }

    private Prayer calculateEmergencyPrayer(List<InfernoNPC> newThreats)
    {
        if (newThreats.isEmpty()) return null;

        // Count threat types and their damage potential
        int meleeThreats = 0, rangedThreats = 0, magicThreats = 0;
        int meleeDamage = 0, rangedDamage = 0, magicDamage = 0;

        for (InfernoNPC npc : newThreats)
        {
            InfernoNPC.Attack attackType = npc.getType().getDefaultAttack();
            int damage = getMaxDamage(npc.getType());

            switch (attackType)
            {
                case MELEE:
                    meleeThreats++;
                    meleeDamage += damage;
                    break;
                case RANGED:
                    rangedThreats++;
                    rangedDamage += damage;
                    break;
                case MAGIC:
                    magicThreats++;
                    magicDamage += damage;
                    break;
            }
        }

        // Return prayer that blocks the highest damage
        if (meleeDamage >= rangedDamage && meleeDamage >= magicDamage)
        {
            return Prayer.PROTECT_FROM_MELEE;
        }
        else if (rangedDamage >= magicDamage)
        {
            return Prayer.PROTECT_FROM_MISSILES;
        }
        else
        {
            return Prayer.PROTECT_FROM_MAGIC;
        }
    }

    private int getMaxDamage(InfernoNPC.Type type)
    {
        // Approximate max damage values for different NPC types
        switch (type)
        {
            case JAD:
                return 97;
            case ZUK:
                return 120;
            case MAGE:
                return 45;
            case RANGER:
                return 40;
            case MELEE:
                return 35;
            case BAT:
                return 15;
            case BLOB:
                return 20;
            default:
                return 30;
        }
    }
    // ==============================================================

    // Enhanced doPraying with emergency switching and multi-threat handling
    private void doPraying()
    {
        if (!config.autoPray() && !config.oneTickPray())
        {
            // Send "none" only if needed
            if (!Objects.equals(lastRecommendedPrayer, "none"))
            {
                eventBus.post(new RecommendedPrayerChangedEvent("none"));
                lastRecommendedPrayer = "none";
            }
            emergencyPrayerSwitching = false;
            return;
        }

        Prayer bestPrayer = null;

        // ===== ENHANCED PRAYER SYSTEM: Multi-Threat Handling =====
        if (config.showAttackTimerOverlay() && multipleNPCsAttackingThisTick())
        {
            bestPrayer = calculateCombinedThreatPrayer();
        }
        else
        {
            // Original prayer calculation logic
            for (Integer tick : getUpcomingAttacks().keySet())
            {
                if (tick != 1)
                    continue;

                final Map<InfernoNPC.Attack, Integer> attackPriority = getUpcomingAttacks().get(tick);
                int bestPriority = Integer.MAX_VALUE;
                InfernoNPC.Attack bestAttack = null;

                for (Map.Entry<InfernoNPC.Attack, Integer> attackEntry : attackPriority.entrySet())
                {
                    if (attackEntry.getValue() < bestPriority)
                    {
                        bestAttack = attackEntry.getKey();
                        bestPriority = attackEntry.getValue();
                    }
                }
                if (bestAttack != null)
                    bestPrayer = bestAttack.getPrayer();
            }
        }
        // ============================================================

        if (config.offTickMeleeJad() && bestPrayer == null && NpcUtils.getNearestNpc("JalTok-Jad") != null)
            bestPrayer = Prayer.PROTECT_FROM_MELEE;

        // Reset emergency switching if we're not in emergency mode
        if (!emergencyPrayerSwitching)
        {
            emergencyPrayer = null;
        }

        // Flicking: alternate between bestPrayer and "none" each tick
        String recommendation = toRecommendationString(bestPrayer);
        if (config.oneTickPray())
        {
            int tickCount = client.getTickCount();
            recommendation = (tickCount % 2 == 0) ? recommendation : "none";
        }

        if (!Objects.equals(lastRecommendedPrayer, recommendation))
        {
            eventBus.post(new RecommendedPrayerChangedEvent(recommendation));
            lastRecommendedPrayer = recommendation;
        }

        // Reset emergency switching after processing
        emergencyPrayerSwitching = false;
    }

    // ===== ENHANCED PRAYER SYSTEM: Multi-Threat Prayer Calculation =====
    private boolean multipleNPCsAttackingThisTick()
    {
        List<InfernoNPC> attackingThisTick = attacksByTick.get(1);
        return attackingThisTick != null && attackingThisTick.size() > 1;
    }

    private Prayer calculateCombinedThreatPrayer()
    {
        List<InfernoNPC> attackingThisTick = attacksByTick.get(1);
        if (attackingThisTick == null || attackingThisTick.isEmpty())
        {
            return null;
        }

        // Calculate total damage for each prayer type
        int meleeDamage = 0, rangedDamage = 0, magicDamage = 0;

        for (InfernoNPC npc : attackingThisTick)
        {
            InfernoNPC.Attack attackType = npc.getNextAttack();
            int damage = getMaxDamage(npc.getType());

            switch (attackType)
            {
                case MELEE:
                    meleeDamage += damage;
                    break;
                case RANGED:
                    rangedDamage += damage;
                    break;
                case MAGIC:
                    magicDamage += damage;
                    break;
            }
        }

        // Return prayer that blocks the most damage
        if (meleeDamage >= rangedDamage && meleeDamage >= magicDamage)
        {
            return Prayer.PROTECT_FROM_MELEE;
        }
        else if (rangedDamage >= magicDamage)
        {
            return Prayer.PROTECT_FROM_MISSILES;
        }
        else
        {
            return Prayer.PROTECT_FROM_MAGIC;
        }
    }
    // =====================================================================

    // Add a helper to translate Prayer enum to string
    private String toRecommendationString(Prayer prayer)
    {
        if (prayer == null) return "none";
        switch (prayer)
        {
            case PROTECT_FROM_MAGIC: return "protect_from_magic";
            case PROTECT_FROM_MISSILES: return "protect_from_missiles";
            case PROTECT_FROM_MELEE: return "protect_from_melee";
            default: return "none";
        }
    }

    @Subscribe
    private void onNpcSpawned(NpcSpawned event)
    {
        if (!isInInferno())
        {
            return;
        }

        final int npcId = event.getNpc().getId();

        if (npcId == NpcID.ANCESTRAL_GLYPH)
        {
            zukShield = event.getNpc();
            return;
        }

        final InfernoNPC.Type infernoNPCType = InfernoNPC.Type.typeFromId(npcId);

        if (infernoNPCType == null)
        {
            return;
        }

        switch (infernoNPCType)
        {
            case BLOB:
                // Blobs need to be added to the end of the list because the prayer for their detection tick
                // will be based on the upcoming attacks of other NPC's
                infernoNpcs.add(new InfernoNPC(event.getNpc()));
                return;
            case MAGE:
                if (zuk != null && spawnTimerInfoBox != null)
                {
                    spawnTimerInfoBox.reset();
                    spawnTimerInfoBox.run();
                }
                break;
            case ZUK:
                finalPhase = false;
                zukShieldCornerTicks = -2;
                zukShieldLastPosition = null;
                zukShieldBase = null;
                log.debug("[INFERNO] Zuk spawn detected, not in final phase");

                if (config.spawnTimerInfobox())
                {
                    zuk = event.getNpc();

                    if (spawnTimerInfoBox != null)
                    {
                        infoBoxManager.removeInfoBox(spawnTimerInfoBox);
                    }

                    spawnTimerInfoBox = new InfernoSpawnTimerInfobox(itemManager.getImage(ItemID.TZREKZUK), this);
                    infoBoxManager.addInfoBox(spawnTimerInfoBox);
                }
                break;
            case HEALER_ZUK:
                finalPhase = true;
                ticksSinceFinalPhase = 1;
                finalPhaseTick = true;
                for (InfernoNPC infernoNPC : infernoNpcs)
                {
                    if (infernoNPC.getType() == InfernoNPC.Type.ZUK)
                    {
                        infernoNPC.setTicksTillNextAttack(-1);
                    }
                }
                log.debug("[INFERNO] Final phase detected!");
                break;
        }

        infernoNpcs.add(0, new InfernoNPC(event.getNpc()));
    }

    @Subscribe
    private void onNpcDespawned(NpcDespawned event)
    {
        if (!isInInferno())
        {
            return;
        }

        int npcId = event.getNpc().getId();

        switch (npcId)
        {
            case NpcID.ANCESTRAL_GLYPH:
                zukShield = null;
                return;
            case NpcID.TZKALZUK:
                zuk = null;

                if (spawnTimerInfoBox != null)
                {
                    infoBoxManager.removeInfoBox(spawnTimerInfoBox);
                }

                spawnTimerInfoBox = null;
                break;
            default:
                break;
        }

        infernoNpcs.removeIf(infernoNPC -> infernoNPC.getNpc() == event.getNpc());
    }

    @Subscribe
    private void onAnimationChanged(AnimationChanged event)
    {
        if (!isInInferno())
        {
            return;
        }

        if (event.getActor() instanceof NPC)
        {
            final NPC npc = (NPC) event.getActor();
            final int animId = EthanApiPlugin.getAnimation(npc);
            if (ArrayUtils.contains(InfernoNPC.Type.NIBBLER.getNpcIds(), npc.getId())
                    && animId == 7576)
            {
                infernoNpcs.removeIf(infernoNPC -> infernoNPC.getNpc() == npc);
            }

            if (config.indicateBlobDeathLocation() && InfernoNPC.Type.typeFromId(npc.getId()) == InfernoNPC.Type.BLOB && animId == InfernoBlobDeathSpot.BLOB_DEATH_ANIMATION)
            {
                // Remove from list so the ticks overlay doesn't compete
                // with the tile overlay.
                infernoNpcs.removeIf(infernoNPC -> infernoNPC.getNpc() == npc);
                blobDeathSpots.add(new InfernoBlobDeathSpot(npc.getLocalLocation()));
            }
        }
    }

    @Subscribe
    private void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        if (!isInInferno())
        {
            infernoNpcs.clear();

            currentWaveNumber = -1;

            overlayManager.remove(infernoOverlay);
            overlayManager.remove(waveOverlay);
            overlayManager.remove(jadOverlay);
            overlayManager.remove(prayerOverlay);
            overlayManager.remove(attackTimerOverlay);

            zukShield = null;
            zuk = null;

            if (spawnTimerInfoBox != null)
            {
                infoBoxManager.removeInfoBox(spawnTimerInfoBox);
            }

            spawnTimerInfoBox = null;
        }
        else if (currentWaveNumber == -1)
        {
            infernoNpcs.clear();

            currentWaveNumber = 1;

            overlayManager.add(infernoOverlay);
            overlayManager.add(jadOverlay);
            overlayManager.add(prayerOverlay);
            overlayManager.add(attackTimerOverlay);

            if (config.waveDisplay() != InfernoWaveDisplayMode.NONE)
            {
                overlayManager.add(waveOverlay);
            }
        }
    }

    @Subscribe
    private void onChatMessage(ChatMessage event)
    {
        if (!isInInferno() || event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }

        String message = event.getMessage();

        if (event.getMessage().contains("Wave:"))
        {
            message = message.substring(message.indexOf(": ") + 2);
            currentWaveNumber = Integer.parseInt(message.substring(0, message.indexOf('<')));
        }
    }

    private boolean isInInferno()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return false;
        }
        LocalPoint localLocation = localPlayer.getLocalLocation();
        if (localLocation == null)
        {
            return false;
        }
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localLocation);
        return worldPoint.getRegionID() == INFERNO_REGION;
    }

    int getNextWaveNumber()
    {
        return currentWaveNumber == -1 || currentWaveNumber == 69 ? -1 : currentWaveNumber + 1;
    }

    private void calculateUpcomingAttacks()
    {
        for (InfernoNPC infernoNPC : infernoNpcs)
        {
            infernoNPC.gameTick(client, lastLocation, finalPhase, ticksSinceFinalPhase);

            if (infernoNPC.getType() == InfernoNPC.Type.ZUK && zukShieldCornerTicks == -1)
            {
                infernoNPC.updateNextAttack(InfernoNPC.Attack.UNKNOWN, 12); // TODO: Could be 10 or 11. Test!
                zukShieldCornerTicks = 0;
            }

            if (infernoNPC.getType() == InfernoNPC.Type.RANGER || infernoNPC.getType() == InfernoNPC.Type.MAGE)
            {
                if (infernoNPC.getNpc().isDead())
                {
                    continue;
                }
            }

            // Map all upcoming attacks and their priority + determine which NPC is about to attack next
            if (infernoNPC.getTicksTillNextAttack() > 0 && isPrayerHelper(infernoNPC)
                    && (infernoNPC.getNextAttack() != InfernoNPC.Attack.UNKNOWN
                    || (config.indicateBlobDetectionTick() && infernoNPC.getType() == InfernoNPC.Type.BLOB
                    && infernoNPC.getTicksTillNextAttack() >= 4)))
            {
                upcomingAttacks.computeIfAbsent(infernoNPC.getTicksTillNextAttack(), k -> new HashMap<>());

                if (config.indicateBlobDetectionTick() && infernoNPC.getType() == InfernoNPC.Type.BLOB
                        && infernoNPC.getTicksTillNextAttack() >= 4)
                {
                    upcomingAttacks.computeIfAbsent(infernoNPC.getTicksTillNextAttack() - 3, k -> new HashMap<>());
                    upcomingAttacks.computeIfAbsent(infernoNPC.getTicksTillNextAttack() - 4, k -> new HashMap<>());

                    // If there's already a magic attack on the detection tick, group them
                    if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).containsKey(InfernoNPC.Attack.MAGIC))
                    {
                        if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).get(InfernoNPC.Attack.MAGIC) > InfernoNPC.Type.BLOB.getPriority())
                        {
                            upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.MAGIC, InfernoNPC.Type.BLOB.getPriority());
                        }
                    }
                    // If there's already a ranged attack on the detection tick, group them
                    else if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).containsKey(InfernoNPC.Attack.RANGED))
                    {
                        if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).get(InfernoNPC.Attack.RANGED) > InfernoNPC.Type.BLOB.getPriority())
                        {
                            upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.RANGED, InfernoNPC.Type.BLOB.getPriority());
                        }
                    }
                    // If there's going to be a magic attack on the blob attack tick, pray range on the detect tick so magic is prayed on the attack tick
                    else if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).containsKey(InfernoNPC.Attack.MAGIC)
                            || upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 4).containsKey(InfernoNPC.Attack.MAGIC))
                    {
                        if (!upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).containsKey(InfernoNPC.Attack.RANGED)
                                || upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).get(InfernoNPC.Attack.RANGED) > InfernoNPC.Type.BLOB.getPriority())
                        {
                            upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.RANGED, InfernoNPC.Type.BLOB.getPriority());
                        }
                    }
                    // If there's going to be a ranged attack on the blob attack tick, pray magic on the detect tick so range is prayed on the attack tick
                    else if (upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).containsKey(InfernoNPC.Attack.RANGED)
                            || upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 4).containsKey(InfernoNPC.Attack.RANGED))
                    {
                        if (!upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).containsKey(InfernoNPC.Attack.MAGIC)
                                || upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).get(InfernoNPC.Attack.MAGIC) > InfernoNPC.Type.BLOB.getPriority())
                        {
                            upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.MAGIC, InfernoNPC.Type.BLOB.getPriority());
                        }
                    }
                    // If there's no magic or ranged attack on the detection tick, create a magic pray blob
                    else
                    {
                        upcomingAttacks.get(infernoNPC.getTicksTillNextAttack() - 3).put(InfernoNPC.Attack.MAGIC, InfernoNPC.Type.BLOB.getPriority());
                    }
                }
                else
                {
                    final InfernoNPC.Attack attack = infernoNPC.getNextAttack();
                    final int priority = infernoNPC.getType().getPriority();

                    if (!upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).containsKey(attack)
                            || upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).get(attack) > priority)
                    {
                        upcomingAttacks.get(infernoNPC.getTicksTillNextAttack()).put(attack, priority);
                    }
                }
            }
        }
    }

    private void calculateClosestAttack()
    {
        if (config.prayerDisplayMode() == InfernoPrayerDisplayMode.PRAYER_TAB
                || config.prayerDisplayMode() == InfernoPrayerDisplayMode.BOTH)
        {
            int closestTick = 999;
            int closestPriority = 999;

            for (Integer tick : upcomingAttacks.keySet())
            {
                final Map<InfernoNPC.Attack, Integer> attackPriority = upcomingAttacks.get(tick);

                for (InfernoNPC.Attack currentAttack : attackPriority.keySet())
                {
                    final int currentPriority = attackPriority.get(currentAttack);
                    if (tick < closestTick || (tick == closestTick && currentPriority < closestPriority))
                    {
                        closestAttack = currentAttack;
                        closestPriority = currentPriority;
                        closestTick = tick;
                    }
                }
            }
        }
    }

    // REMOVED markOptimalSafespots() method - was causing safespots to disappear

    private void calculateSafespots()
    {
        if (currentWaveNumber < 69)
        {
            if (config.safespotDisplayMode() != InfernoSafespotDisplayMode.OFF)
            {
                int checkSize = (int) Math.floor(config.safespotsCheckSize() / 2.0);

                for (int x = -checkSize; x <= checkSize; x++)
                {
                    for (int y = -checkSize; y <= checkSize; y++)
                    {
                        final WorldPoint checkLoc = client.getLocalPlayer().getWorldLocation().dx(x).dy(y);

                        if (obstacles.contains(checkLoc))
                        {
                            continue;
                        }

                        for (InfernoNPC infernoNPC : infernoNpcs)
                        {
                            if (!isNormalSafespots(infernoNPC))
                            {
                                continue;
                            }

                            if (!safeSpotMap.containsKey(checkLoc))
                            {
                                safeSpotMap.put(checkLoc, 0);
                            }

                            if (infernoNPC.canAttack(client, checkLoc)
                                    || infernoNPC.canMoveToAttack(client, checkLoc, obstacles))
                            {
                                if (infernoNPC.getType().getDefaultAttack() == InfernoNPC.Attack.MELEE)
                                {
                                    if (safeSpotMap.get(checkLoc) == 0)
                                    {
                                        safeSpotMap.put(checkLoc, 1);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 2)
                                    {
                                        safeSpotMap.put(checkLoc, 4);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 3)
                                    {
                                        safeSpotMap.put(checkLoc, 5);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 6)
                                    {
                                        safeSpotMap.put(checkLoc, 7);
                                    }
                                }

                                if (infernoNPC.getType().getDefaultAttack() == InfernoNPC.Attack.MAGIC
                                        || (infernoNPC.getType() == InfernoNPC.Type.BLOB
                                        && safeSpotMap.get(checkLoc) != 2 && safeSpotMap.get(checkLoc) != 4))
                                {
                                    if (safeSpotMap.get(checkLoc) == 0)
                                    {
                                        safeSpotMap.put(checkLoc, 3);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 1)
                                    {
                                        safeSpotMap.put(checkLoc, 5);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 2)
                                    {
                                        safeSpotMap.put(checkLoc, 6);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 5)
                                    {
                                        safeSpotMap.put(checkLoc, 7);
                                    }
                                }

                                if (infernoNPC.getType().getDefaultAttack() == InfernoNPC.Attack.RANGED
                                        || (infernoNPC.getType() == InfernoNPC.Type.BLOB
                                        && safeSpotMap.get(checkLoc) != 3 && safeSpotMap.get(checkLoc) != 5))
                                {
                                    if (safeSpotMap.get(checkLoc) == 0)
                                    {
                                        safeSpotMap.put(checkLoc, 2);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 1)
                                    {
                                        safeSpotMap.put(checkLoc, 4);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 3)
                                    {
                                        safeSpotMap.put(checkLoc, 6);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 4)
                                    {
                                        safeSpotMap.put(checkLoc, 7);
                                    }
                                }

                                if (infernoNPC.getType() == InfernoNPC.Type.JAD
                                        && infernoNPC.getNpc().getWorldArea().isInMeleeDistance(checkLoc))
                                {
                                    if (safeSpotMap.get(checkLoc) == 0)
                                    {
                                        safeSpotMap.put(checkLoc, 1);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 2)
                                    {
                                        safeSpotMap.put(checkLoc, 4);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 3)
                                    {
                                        safeSpotMap.put(checkLoc, 5);
                                    }
                                    else if (safeSpotMap.get(checkLoc) == 6)
                                    {
                                        safeSpotMap.put(checkLoc, 7);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        else if (currentWaveNumber == 69 && zukShield != null)
        {
            final WorldPoint zukShieldCurrentPosition = zukShield.getWorldLocation();

            if (zukShieldLastPosition != null && zukShieldLastPosition.getX() != zukShieldCurrentPosition.getX() && zukShieldCornerTicks == -2)
            {
                zukShieldBase = zukShieldLastPosition;
                zukShieldCornerTicks = -1;
            }

            if (zukShieldLastPosition != null)
            {
                int zukShieldDelta = zukShieldCurrentPosition.getX() - zukShieldLastPosition.getX();

                //if zuk shield moved, update zukShieldLastNonZeroDelta to show the direction
                if (zukShieldDelta != 0)
                {
                    zukShieldLastNonZeroDelta = zukShieldDelta;
                }

                //reset corner ticks when the shield started to move out of the corner
                if (zukShieldLastDelta == 0 && zukShieldDelta != 0)
                {
                    zukShieldTicksLeftInCorner = 4;
                }

                //if zuk shield did not move, also set the negative/positive XCoords for the shield
                if (zukShieldDelta == 0)
                {
                    if (zukShieldLastNonZeroDelta > 0)
                    {
                        zukShieldPositiveXCoord = zukShieldCurrentPosition.getX();
                    }
                    else if (zukShieldLastNonZeroDelta < 0)
                    {
                        zukShieldNegativeXCoord = zukShieldCurrentPosition.getX();
                    }

                    //if zukShieldCorner Ticks > 0, decrement it
                    if (zukShieldTicksLeftInCorner > 0)
                    {
                        zukShieldTicksLeftInCorner--;
                    }
                }

                zukShieldLastDelta = zukShieldDelta;
            }

            zukShieldLastPosition = zukShieldCurrentPosition;

            if (config.safespotDisplayMode() != InfernoSafespotDisplayMode.OFF)
            {
                if ((finalPhase && config.safespotsZukShieldAfterHealers() == InfernoZukShieldDisplayMode.LIVE)
                        || (!finalPhase && config.safespotsZukShieldBeforeHealers() == InfernoZukShieldDisplayMode.LIVE))
                {
                    drawZukSafespot(zukShield.getWorldLocation().getX(), zukShield.getWorldLocation().getY(), 0);
                }

                if ((finalPhase && config.safespotsZukShieldAfterHealers() == InfernoZukShieldDisplayMode.LIVEPLUSPREDICT)
                        || (!finalPhase && config.safespotsZukShieldBeforeHealers() == InfernoZukShieldDisplayMode.LIVEPLUSPREDICT))
                {
                    //draw the normal live safespot
                    drawZukSafespot(zukShield.getWorldLocation().getX(), zukShield.getWorldLocation().getY(), 0);

                    drawZukPredictedSafespot();
                }
                else if ((finalPhase && config.safespotsZukShieldAfterHealers() == InfernoZukShieldDisplayMode.PREDICT)
                        || (!finalPhase && config.safespotsZukShieldBeforeHealers() == InfernoZukShieldDisplayMode.PREDICT))
                {
                    drawZukPredictedSafespot();
                }
            }
        }
    }

    private void drawZukPredictedSafespot()
    {
        final WorldPoint zukShieldCurrentPosition = zukShield.getWorldLocation();
        //only do this if both xcoords defined.
        if (zukShieldPositiveXCoord != -1 && zukShieldNegativeXCoord != -1)
        {
            int nextShieldXCoord = zukShieldCurrentPosition.getX();

            //calculate the next zuk shield position
            for (InfernoNPC infernoNPC : infernoNpcs)
            {
                if (infernoNPC.getType() == InfernoNPC.Type.ZUK)
                {
                    int ticksTilZukAttack = finalPhase ? infernoNPC.getTicksTillNextAttack() : infernoNPC.getTicksTillNextAttack() - 1;

                    if (ticksTilZukAttack < 1)
                    {
                        if (finalPhase)
                        {
                            //if ticksTilZukAttack < 1 and finalPhase, must be due to finalPhase. don't render predicted safepot until next attack.
                            return;
                        }
                        else
                        {
                            //safe to start to render the next safespot
                            ticksTilZukAttack = 10;
                        }
                    }

                    //if zuk shield moving in positive direction
                    if (zukShieldLastNonZeroDelta > 0)
                    {
                        nextShieldXCoord += ticksTilZukAttack;

                        //nextShieldPosition appears to be past the rightmost spot, must adjust
                        if (nextShieldXCoord > zukShieldPositiveXCoord)
                        {
                            //reduce by number of ticks spent in corner
                            nextShieldXCoord -= zukShieldTicksLeftInCorner;

                            //nextShieldPosition is LT or equal to the rightmost spot
                            if (nextShieldXCoord <= zukShieldPositiveXCoord)
                            {
                                //shield should be at that spot
                                nextShieldXCoord = zukShieldPositiveXCoord;
                            }
                            else
                            {
                                //nextShieldPosition is right of the rightmost spot still
                                nextShieldXCoord = zukShieldPositiveXCoord - nextShieldXCoord + zukShieldPositiveXCoord;
                            }
                        }
                    }
                    else
                    {
                        //moving in negative direction
                        nextShieldXCoord -= ticksTilZukAttack;

                        //nextShieldPosition appears to be past the leftmost spot, must adjust
                        if (nextShieldXCoord < zukShieldNegativeXCoord)
                        {
                            //add by number of ticks spent in corner
                            nextShieldXCoord += zukShieldTicksLeftInCorner;

                            //nextShieldPosition is GT or equal to the leftmost spot
                            if (nextShieldXCoord >= zukShieldNegativeXCoord)
                            {
                                //shield should be at that spot
                                nextShieldXCoord = zukShieldNegativeXCoord;
                            }
                            else
                            {
                                //nextShieldPosition is left of the leftmost spot still
                                nextShieldXCoord = zukShieldNegativeXCoord - nextShieldXCoord + zukShieldNegativeXCoord;
                            }
                        }
                    }
                }
            }

            //draw the predicted safespot
            drawZukSafespot(nextShieldXCoord, zukShield.getWorldLocation().getY(), 2);
        }
    }

    private void drawZukSafespot(int xCoord, int yCoord, int colorSafeSpotId)
    {
        for (int x = xCoord - 1; x <= xCoord + 3; x++)
        {
            for (int y = yCoord - 4; y <= yCoord - 2; y++)
            {
                safeSpotMap.put(new WorldPoint(x, y, client.getTopLevelWorldView().getPlane()), colorSafeSpotId);
            }
        }
    }

    private void calculateSafespotAreas()
    {
        if (config.safespotDisplayMode() == InfernoSafespotDisplayMode.AREA)
        {
            for (WorldPoint worldPoint : safeSpotMap.keySet())
            {
                if (!safeSpotAreas.containsKey(safeSpotMap.get(worldPoint)))
                {
                    safeSpotAreas.put(safeSpotMap.get(worldPoint), new ArrayList<>());
                }

                safeSpotAreas.get(safeSpotMap.get(worldPoint)).add(worldPoint);
            }
        }

        lastLocation = client.getLocalPlayer().getWorldLocation();
    }

    private void calculateObstacles()
    {
        for (NPC npc : client.getTopLevelWorldView().npcs())
        {
            obstacles.addAll(npc.getWorldArea().toWorldPointList());
        }
    }

    private void manageBlobDeathLocations()
    {
        if (config.indicateBlobDeathLocation())
        {
            blobDeathSpots.forEach(InfernoBlobDeathSpot::decrementTick);
            blobDeathSpots.removeIf(InfernoBlobDeathSpot::isDone);
        }
    }

    private void calculateCentralNibbler()
    {
        InfernoNPC bestNibbler = null;
        int bestAmountInArea = 0;
        int bestDistanceToPlayer = 999;

        for (InfernoNPC infernoNPC : infernoNpcs)
        {
            if (infernoNPC.getType() != InfernoNPC.Type.NIBBLER)
            {
                continue;
            }

            int amountInArea = 0;
            final int distanceToPlayer = infernoNPC.getNpc().getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation());

            for (InfernoNPC checkNpc : infernoNpcs)
            {
                if (checkNpc.getType() != InfernoNPC.Type.NIBBLER
                        || checkNpc.getNpc().getWorldArea().distanceTo(infernoNPC.getNpc().getWorldArea()) > 1)
                {
                    continue;
                }

                amountInArea++;
            }

            if (amountInArea > bestAmountInArea
                    || (amountInArea == bestAmountInArea && distanceToPlayer < bestDistanceToPlayer))
            {
                bestNibbler = infernoNPC;

                // update tracked values
                bestAmountInArea = amountInArea;
                bestDistanceToPlayer = distanceToPlayer;
            }
        }

        if (bestNibbler != null)
        {
            centralNibbler = bestNibbler;
        }
    }

    private void calculateSpawnTimerInfobox()
    {
        if (zuk == null || finalPhase || spawnTimerInfoBox == null)
        {
            return;
        }

        final int pauseHp = 600;
        final int resumeHp = 480;

        int hp = calculateNpcHp(zuk.getHealthRatio(), zuk.getHealthScale(), 1200);

        if (hp <= 0)
        {
            return;
        }

        if (spawnTimerInfoBox.isRunning())
        {
            if (hp >= resumeHp && hp < pauseHp)
            {
                spawnTimerInfoBox.pause();
            }
        }
        else
        {
            if (hp < resumeHp)
            {
                spawnTimerInfoBox.run();
            }
        }
    }

    boolean isIndicateNpcPosition(InfernoNPC infernoNPC)
    {
        switch (infernoNPC.getType())
        {
            case BAT:
                return config.indicateNpcPositionBat();
            case BLOB:
                return config.indicateNpcPositionBlob();
            case MELEE:
                return config.indicateNpcPositionMeleer();
            case RANGER:
                return config.indicateNpcPositionRanger();
            case MAGE:
                return config.indicateNpcPositionMage();
            default:
                return false;
        }
    }

    boolean isTicksOnNpc(InfernoNPC infernoNPC)
    {
        switch (infernoNPC.getType())
        {
            case BAT:
                return config.ticksOnNpcBat();
            case BLOB:
                return config.ticksOnNpcBlob();
            case MELEE:
                return config.ticksOnNpcMeleer();
            case RANGER:
                return config.ticksOnNpcRanger();
            case MAGE:
                return config.ticksOnNpcMage();
            case HEALER_JAD:
                return config.ticksOnNpcHealerJad();
            case JAD:
                return config.ticksOnNpcJad();
            case ZUK:
                return config.ticksOnNpcZuk();
            default:
                return false;
        }
    }

    private static int calculateNpcHp(int ratio, int health, int maxHp)
    {
        // See OpponentInfo Plugin
        // Copyright (c) 2016-2018, Adam <Adam@sigterm.info>
        // Copyright (c) 2018, Jordan Atwood <jordan.atwood423@gmail.com>

        if (ratio < 0 || health <= 0 || maxHp == -1)
        {
            return -1;
        }

        int exactHealth = 0;

        if (ratio > 0)
        {
            int minHealth = 1;
            int maxHealth;

            if (health > 1)
            {
                if (ratio > 1)
                {
                    minHealth = (maxHp * (ratio - 1) + health - 2) / (health - 1);
                }

                maxHealth = (maxHp * ratio - 1) / (health - 1);

                if (maxHealth > maxHp)
                {
                    maxHealth = maxHp;
                }
            }
            else
            {
                maxHealth = maxHp;
            }

            exactHealth = (minHealth + maxHealth + 1) / 2;
        }

        return exactHealth;
    }

    boolean isNormalSafespots(InfernoNPC infernoNPC)
    {
        switch (infernoNPC.getType())
        {
            case BAT:
                return config.safespotsBat();
            case BLOB:
                return config.safespotsBlob();
            case MELEE:
                return config.safespotsMeleer();
            case RANGER:
                return config.safespotsRanger();
            case MAGE:
                return config.safespotsMage();
            case HEALER_JAD:
                return config.safespotsHealerJad();
            case JAD:
                return config.safespotsJad();
            default:
                return false;
        }
    }

    boolean isPrayerHelper(InfernoNPC infernoNPC)
    {
        switch (infernoNPC.getType())
        {
            case BAT:
                return config.prayerBat();
            case BLOB:
                return config.prayerBlob();
            case MELEE:
                return config.prayerMeleer();
            case RANGER:
                return config.prayerRanger();
            case MAGE:
                return config.prayerMage();
            case HEALER_JAD:
                return config.prayerHealerJad();
            case JAD:
                return config.prayerJad();
            default:
                return false;
        }
    }

    void spawnDebug(String msg)
    {
        if (config.spawnTimerDebug())
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
        }
    }

    Color getWaveOverlayHeaderColor()
    {
        return config.getWaveOverlayHeaderColor();
    }

    Color getWaveTextColor()
    {
        return config.getWaveTextColor();
    }

    // ===== ENHANCED PRAYER SYSTEM: Getter methods for overlays =====
    public boolean isEmergencyPrayerActive()
    {
        return emergencyPrayerSwitching;
    }

    public Prayer getEmergencyPrayer()
    {
        return emergencyPrayer;
    }

    public boolean hasDangerousTiles()
    {
        return !dangerousTiles.isEmpty();
    }

    public Set<WorldPoint> getDangerousTiles()
    {
        return dangerousTiles;
    }

    public Map<WorldPoint, Set<InfernoNPC>> getTileThreats()
    {
        return tileThreats;
    }

    public boolean hasSimultaneousAttackers()
    {
        return !simultaneousAttackers.isEmpty();
    }

    public List<InfernoNPC> getSimultaneousAttackers()
    {
        return simultaneousAttackers;
    }

    public Map<Integer, List<InfernoNPC>> getAttacksByTick()
    {
        return attacksByTick;
    }
    // ================================================================
}