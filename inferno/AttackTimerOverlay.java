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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Singleton
public class AttackTimerOverlay extends Overlay
{
    private final InfernoPlugin plugin;
    private final InfernoConfig config;
    private final Client client;
    private final PanelComponent panelComponent;

    @Setter(AccessLevel.PACKAGE)
    private Color attackHeaderColor = Color.WHITE;

    @Setter(AccessLevel.PACKAGE)
    private Color attackTextColor = Color.WHITE;

    // Color palette for different simultaneous groups
    private final Color[] SIMULTANEOUS_COLORS = {
            Color.RED,
            Color.ORANGE,
            Color.YELLOW,
            Color.MAGENTA,
            Color.CYAN,
            new Color(255, 100, 100),
            new Color(255, 165, 0),
            new Color(255, 192, 203)
    };

    @Inject
    AttackTimerOverlay(final InfernoPlugin plugin, final InfernoConfig config, final Client client)
    {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        this.panelComponent = new PanelComponent();
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.HIGH);
        panelComponent.setPreferredSize(new Dimension(160, 0));
    }

    @Override
    public Dimension render(final Graphics2D graphics)
    {
        if (!config.showAttackTimerOverlay())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        // Add title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Attack Timers")
                .color(attackHeaderColor)
                .build());

        // Add attack timer info
        addAttackTimers();

        // Add recommended prayer
        addRecommendedPrayer();

        return panelComponent.render(graphics);
    }

    private void addAttackTimers()
    {
        // Get all NPCs and group by type
        Map<InfernoNPC.Type, List<InfernoNPC>> npcsByType = new HashMap<>();

        for (InfernoNPC npc : plugin.getInfernoNpcs())
        {
            if (npc.getTicksTillNextAttack() > 0)
            {
                npcsByType.computeIfAbsent(npc.getType(), k -> new ArrayList<>()).add(npc);
            }
        }

        // Find which specific NPCs will attack simultaneously
        Map<InfernoNPC, SimultaneousGroup> simultaneousNPCs = findSimultaneousAttackers(npcsByType);

        // Display in priority order: Jad, Mager, Ranger, Meleer, Blob, Bat
        InfernoNPC.Type[] displayOrder = {
                InfernoNPC.Type.JAD,
                InfernoNPC.Type.MAGE,
                InfernoNPC.Type.RANGER,
                InfernoNPC.Type.MELEE,
                InfernoNPC.Type.BLOB,
                InfernoNPC.Type.BAT
        };

        for (InfernoNPC.Type type : displayOrder)
        {
            List<InfernoNPC> npcs = npcsByType.get(type);

            // Only show if NPCs of this type exist and have attack timers
            if (npcs != null && !npcs.isEmpty())
            {
                String typeName = getNPCTypeName(type);

                // Check if we need to split into multiple lines due to different simultaneous groups
                if (hasMultipleGroups(npcs, simultaneousNPCs))
                {
                    // Create separate lines for different simultaneous groups
                    addMultiGroupNPCLines(typeName, npcs, simultaneousNPCs);
                }
                else
                {
                    // All NPCs have the same status, display on single line
                    StringBuilder rightText = new StringBuilder();
                    Color lineColor = attackTextColor;
                    SimultaneousGroup sharedGroup = null;

                    for (int i = 0; i < npcs.size(); i++)
                    {
                        InfernoNPC npc = npcs.get(i);
                        int tick = npc.getTicksTillNextAttack();
                        String attackSymbol = getAttackTypeSymbol(npc.getNextAttack());

                        SimultaneousGroup group = simultaneousNPCs.get(npc);
                        String indicator = "";

                        if (group != null)
                        {
                            indicator = " ⚠";
                            sharedGroup = group;
                        }

                        if (i > 0)
                        {
                            rightText.append(" ");
                        }
                        rightText.append(tick).append(" ").append(attackSymbol).append(indicator);
                    }

                    // Use group color if all NPCs are in the same simultaneous group
                    if (sharedGroup != null)
                    {
                        lineColor = sharedGroup.color;
                    }

                    panelComponent.getChildren().add(LineComponent.builder()
                            .left(typeName + ":")
                            .leftColor(attackTextColor)
                            .right(rightText.toString())
                            .rightColor(lineColor)
                            .build());
                }
            }
        }
    }

    private boolean hasMultipleGroups(List<InfernoNPC> npcs, Map<InfernoNPC, SimultaneousGroup> simultaneousNPCs)
    {
        Set<SimultaneousGroup> groups = new HashSet<>();
        boolean hasNormal = false;

        for (InfernoNPC npc : npcs)
        {
            SimultaneousGroup group = simultaneousNPCs.get(npc);
            if (group != null)
            {
                groups.add(group);
            }
            else
            {
                hasNormal = true;
            }
        }

        // Multiple groups if we have normal NPCs + simultaneous, or multiple different simultaneous groups
        return (hasNormal && !groups.isEmpty()) || groups.size() > 1;
    }

    private void addMultiGroupNPCLines(String typeName, List<InfernoNPC> npcs, Map<InfernoNPC, SimultaneousGroup> simultaneousNPCs)
    {
        // Group NPCs by their simultaneous group
        Map<SimultaneousGroup, List<InfernoNPC>> groupedNpcs = new HashMap<>();
        List<InfernoNPC> normalNpcs = new ArrayList<>();

        for (InfernoNPC npc : npcs)
        {
            SimultaneousGroup group = simultaneousNPCs.get(npc);
            if (group != null)
            {
                groupedNpcs.computeIfAbsent(group, k -> new ArrayList<>()).add(npc);
            }
            else
            {
                normalNpcs.add(npc);
            }
        }

        boolean firstLine = true;

        // Add line for normal (non-simultaneous) NPCs
        if (!normalNpcs.isEmpty())
        {
            StringBuilder rightText = new StringBuilder();
            for (int i = 0; i < normalNpcs.size(); i++)
            {
                InfernoNPC npc = normalNpcs.get(i);
                int tick = npc.getTicksTillNextAttack();
                String attackSymbol = getAttackTypeSymbol(npc.getNextAttack());

                if (i > 0)
                {
                    rightText.append(" ");
                }
                rightText.append(tick).append(" ").append(attackSymbol);
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(typeName + ":")
                    .leftColor(attackTextColor)
                    .right(rightText.toString())
                    .rightColor(attackTextColor)
                    .build());

            firstLine = false;
        }

        // Add lines for each simultaneous group
        for (Map.Entry<SimultaneousGroup, List<InfernoNPC>> entry : groupedNpcs.entrySet())
        {
            SimultaneousGroup group = entry.getKey();
            List<InfernoNPC> groupNpcs = entry.getValue();

            StringBuilder rightText = new StringBuilder();
            for (int i = 0; i < groupNpcs.size(); i++)
            {
                InfernoNPC npc = groupNpcs.get(i);
                int tick = npc.getTicksTillNextAttack();
                String attackSymbol = getAttackTypeSymbol(npc.getNextAttack());

                if (i > 0)
                {
                    rightText.append(" ");
                }
                rightText.append(tick).append(" ").append(attackSymbol).append(" ⚠");
            }

            // Use empty left side if we already showed the type name
            String leftText = firstLine ? typeName + ":" : "";

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(leftText)
                    .leftColor(attackTextColor)
                    .right(rightText.toString())
                    .rightColor(group.color)
                    .build());

            firstLine = false;
        }
    }

    // ===== SIMPLE SIMULTANEOUS ATTACK DETECTION =====

    /**
     * Data class for simultaneous attack groups
     */
    private static class SimultaneousGroup
    {
        final List<InfernoNPC> npcs;
        final Color color;
        final int priority;
        final int tick;

        SimultaneousGroup(List<InfernoNPC> npcs, Color color, int priority, int tick)
        {
            this.npcs = npcs;
            this.color = color;
            this.priority = priority;
            this.tick = tick;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof SimultaneousGroup)) return false;
            SimultaneousGroup that = (SimultaneousGroup) o;
            return tick == that.tick && priority == that.priority;
        }

        @Override
        public int hashCode()
        {
            return tick * 31 + priority;
        }
    }

    /**
     * Finds NPCs that are attacking on the same tick (using existing tick tracking)
     */
    private Map<InfernoNPC, SimultaneousGroup> findSimultaneousAttackers(Map<InfernoNPC.Type, List<InfernoNPC>> npcsByType)
    {
        Map<InfernoNPC, SimultaneousGroup> result = new HashMap<>();
        List<InfernoNPC> activeNPCs = new ArrayList<>();

        // Collect only attack-capable NPCs
        for (Map.Entry<InfernoNPC.Type, List<InfernoNPC>> entry : npcsByType.entrySet())
        {
            InfernoNPC.Type type = entry.getKey();
            if (isAttackingNPC(type))
            {
                activeNPCs.addAll(entry.getValue());
            }
        }

        // Group NPCs by their exact attack tick (using existing getTicksTillNextAttack())
        Map<Integer, List<InfernoNPC>> npcsByTick = new HashMap<>();
        for (InfernoNPC npc : activeNPCs)
        {
            int attackTick = npc.getTicksTillNextAttack();
            npcsByTick.computeIfAbsent(attackTick, k -> new ArrayList<>()).add(npc);
        }

        // Find simultaneous groups (2+ NPCs attacking on same tick with different prayers)
        int colorIndex = 0;
        for (Map.Entry<Integer, List<InfernoNPC>> entry : npcsByTick.entrySet())
        {
            int tick = entry.getKey();
            List<InfernoNPC> npcsOnTick = entry.getValue();

            // Only flag as simultaneous if 2+ NPCs and they need different prayers
            if (npcsOnTick.size() >= 2 && requiresDifferentPrayers(npcsOnTick))
            {
                Color groupColor = SIMULTANEOUS_COLORS[colorIndex % SIMULTANEOUS_COLORS.length];
                int priority = calculateGroupPriority(npcsOnTick);

                SimultaneousGroup group = new SimultaneousGroup(npcsOnTick, groupColor, priority, tick);

                for (InfernoNPC npc : npcsOnTick)
                {
                    result.put(npc, group);
                }

                colorIndex++;
            }
        }

        return result;
    }

    /**
     * Determines if an NPC type can attack (excludes support NPCs)
     */
    private boolean isAttackingNPC(InfernoNPC.Type type)
    {
        return type != InfernoNPC.Type.NIBBLER
                && type != InfernoNPC.Type.HEALER_JAD
                && type != InfernoNPC.Type.HEALER_ZUK
                && type != InfernoNPC.Type.ZUK; // Exclude Zuk for now
    }

    /**
     * Checks if NPCs require different prayers (making simultaneous attacks dangerous)
     */
    private boolean requiresDifferentPrayers(List<InfernoNPC> npcs)
    {
        Set<Prayer> prayers = new HashSet<>();
        for (InfernoNPC npc : npcs)
        {
            Prayer prayer = npc.getNextAttack().getPrayer();
            if (prayer != null)
            {
                prayers.add(prayer);
            }
        }
        // Only dangerous if they need different prayers
        return prayers.size() > 1;
    }

    /**
     * Calculates priority for a group (higher damage = higher priority)
     */
    private int calculateGroupPriority(List<InfernoNPC> npcs)
    {
        int totalDamage = 0;
        for (InfernoNPC npc : npcs)
        {
            totalDamage += getMaxDamage(npc.getType());
        }
        return totalDamage;
    }

    /**
     * Gets max damage for NPC type
     */
    private int getMaxDamage(InfernoNPC.Type type)
    {
        switch (type)
        {
            case JAD: return 97;
            case MAGE: return 45;
            case RANGER: return 40;
            case MELEE: return 35;
            case BAT: return 15;
            case BLOB: return 20;
            default: return 30;
        }
    }

    // ===== END SIMPLE LOGIC =====

    private void addRecommendedPrayer()
    {
        InfernoNPC.Attack closestAttack = plugin.getClosestAttack();
        if (closestAttack == null) return;

        Prayer recommendedPrayer = closestAttack.getPrayer();
        if (recommendedPrayer == null) return;

        String prayerName = getPrayerDisplayName(recommendedPrayer);
        Color prayerColor = getAttackTypeColor(closestAttack);

        // Add spacing line
        panelComponent.getChildren().add(LineComponent.builder()
                .left("")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Prayer:")
                .leftColor(attackTextColor)
                .right(prayerName)
                .rightColor(prayerColor)
                .build());
    }

    private String getNPCTypeName(InfernoNPC.Type type)
    {
        switch (type)
        {
            case JAD: return "Jad";
            case MAGE: return "Mager";
            case RANGER: return "Ranger";
            case MELEE: return "Meleer";
            case BLOB: return "Blob";
            case BAT: return "Bat";
            default: return type.toString();
        }
    }

    private String getAttackTypeSymbol(InfernoNPC.Attack attack)
    {
        switch (attack)
        {
            case MELEE: return "⚔";
            case RANGED: return "🏹";
            case MAGIC: return "✨";
            default: return "?";
        }
    }

    private String getPrayerDisplayName(Prayer prayer)
    {
        switch (prayer)
        {
            case PROTECT_FROM_MELEE: return "Melee";
            case PROTECT_FROM_MISSILES: return "Range";
            case PROTECT_FROM_MAGIC: return "Magic";
            default: return prayer.toString();
        }
    }

    private Color getAttackTypeColor(InfernoNPC.Attack attack)
    {
        switch (attack)
        {
            case MELEE: return Color.RED;
            case RANGED: return Color.GREEN;
            case MAGIC: return Color.BLUE;
            default: return attackTextColor;
        }
    }

    // Setters for overlay customization
    public void setAttackHeaderColor(Color headerColor)
    {
        this.attackHeaderColor = headerColor;
    }

    public void setAttackTextColor(Color textColor)
    {
        this.attackTextColor = textColor;
    }
}