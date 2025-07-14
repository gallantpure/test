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
        Set<InfernoNPC> simultaneousNPCs = findSimultaneousAttackers(npcsByType);

        // Display in priority order: Jad, Mager, Ranger, Meleer, Blob, Bat
        // Only show if they exist
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

                // Check if we need to split into multiple lines due to different colors
                if (hasMultipleColors(npcs, simultaneousNPCs))
                {
                    // Create separate lines: one for normal NPCs, one for simultaneous NPCs
                    addMultiColorNPCLines(typeName, npcs, simultaneousNPCs);
                }
                else
                {
                    // All NPCs have the same color, display on single line
                    StringBuilder rightText = new StringBuilder();

                    for (int i = 0; i < npcs.size(); i++)
                    {
                        InfernoNPC npc = npcs.get(i);
                        int tick = npc.getTicksTillNextAttack();
                        String attackSymbol = getAttackTypeSymbol(npc.getNextAttack());
                        String skullSymbol = simultaneousNPCs.contains(npc) ? "💀" : "";

                        if (i > 0)
                        {
                            rightText.append(" ");
                        }
                        rightText.append(tick).append(" ").append(attackSymbol).append(skullSymbol);
                    }

                    // Determine color: red if ALL NPCs are simultaneous, white otherwise
                    boolean allSimultaneous = npcs.stream().allMatch(simultaneousNPCs::contains);
                    Color textColor = allSimultaneous ? Color.RED : attackTextColor;

                    panelComponent.getChildren().add(LineComponent.builder()
                            .left(typeName + ":")
                            .leftColor(attackTextColor)
                            .right(rightText.toString())
                            .rightColor(textColor)
                            .build());
                }
            }
        }
    }

    private boolean hasMultipleColors(List<InfernoNPC> npcs, Set<InfernoNPC> simultaneousNPCs)
    {
        boolean hasSimultaneous = false;
        boolean hasNonSimultaneous = false;

        for (InfernoNPC npc : npcs)
        {
            if (simultaneousNPCs.contains(npc))
            {
                hasSimultaneous = true;
            }
            else
            {
                hasNonSimultaneous = true;
            }
        }

        return hasSimultaneous && hasNonSimultaneous;
    }

    private void addMultiColorNPCLines(String typeName, List<InfernoNPC> npcs, Set<InfernoNPC> simultaneousNPCs)
    {
        // Group NPCs by whether they're simultaneous or not
        List<InfernoNPC> normalNpcs = npcs.stream()
                .filter(npc -> !simultaneousNPCs.contains(npc))
                .collect(Collectors.toList());

        List<InfernoNPC> simultaneousNpcs = npcs.stream()
                .filter(simultaneousNPCs::contains)
                .collect(Collectors.toList());

        // Add line for normal (white) NPCs if any exist
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
        }

        // Add line for simultaneous (red) NPCs if any exist
        if (!simultaneousNpcs.isEmpty())
        {
            StringBuilder rightText = new StringBuilder();
            for (int i = 0; i < simultaneousNpcs.size(); i++)
            {
                InfernoNPC npc = simultaneousNpcs.get(i);
                int tick = npc.getTicksTillNextAttack();
                String attackSymbol = getAttackTypeSymbol(npc.getNextAttack());
                String skullSymbol = "💀"; // All NPCs in this group are simultaneous

                if (i > 0)
                {
                    rightText.append(" ");
                }
                rightText.append(tick).append(" ").append(attackSymbol).append(skullSymbol);
            }

            // Use empty left side if we already showed the type name for normal NPCs
            String leftText = normalNpcs.isEmpty() ? typeName + ":" : "";

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(leftText)
                    .leftColor(attackTextColor)
                    .right(rightText.toString())
                    .rightColor(Color.RED)
                    .build());
        }
    }

    private Set<InfernoNPC> findSimultaneousAttackers(Map<InfernoNPC.Type, List<InfernoNPC>> npcsByType)
    {
        Set<InfernoNPC> simultaneousNPCs = new HashSet<>();
        List<InfernoNPC> allNPCs = new ArrayList<>();

        // Collect only attack-capable NPCs (exclude Nibblers and Healers)
        for (Map.Entry<InfernoNPC.Type, List<InfernoNPC>> entry : npcsByType.entrySet())
        {
            InfernoNPC.Type type = entry.getKey();
            if (type == InfernoNPC.Type.NIBBLER ||
                    type == InfernoNPC.Type.HEALER_JAD ||
                    type == InfernoNPC.Type.HEALER_ZUK ||
                    type == InfernoNPC.Type.ZUK)
            {
                continue; // skip Nibblers, Healers, and Zuk
            }
            allNPCs.addAll(entry.getValue());
        }

        // Check each NPC against every other NPC for *potential* simultaneous attacks (not just soon)
        for (int i = 0; i < allNPCs.size(); i++)
        {
            InfernoNPC npc1 = allNPCs.get(i);

            for (int j = i + 1; j < allNPCs.size(); j++)
            {
                InfernoNPC npc2 = allNPCs.get(j);

                if (canAttackSimultaneously(npc1, npc2))
                {
                    simultaneousNPCs.add(npc1);
                    simultaneousNPCs.add(npc2);
                }
            }
        }

        return simultaneousNPCs;
    }

    // New: persistent simultaneous logic using GCD
    private boolean canAttackSimultaneously(InfernoNPC npc1, InfernoNPC npc2)
    {
        // Only simultaneous if their protection prayer is different!
        Prayer p1 = npc1.getNextAttack().getPrayer();
        Prayer p2 = npc2.getNextAttack().getPrayer();
        if (p1 == null || p2 == null || p1 == p2)
            return false;

        int tick1 = npc1.getTicksTillNextAttack();
        int tick2 = npc2.getTicksTillNextAttack();
        int cycle1 = getAttackCycle(npc1.getType());
        int cycle2 = getAttackCycle(npc2.getType());
        int diff = Math.abs(tick1 - tick2);
        int gcdVal = gcd(cycle1, cycle2);
        return gcdVal > 0 && (diff % gcdVal == 0);
    }

    private int gcd(int a, int b)
    {
        return b == 0 ? a : gcd(b, a % b);
    }

    private int getAttackCycle(InfernoNPC.Type type)
    {
        switch (type)
        {
            case BAT: return 3;      // Bats attack every 3 ticks
            case BLOB: return 6;     // Blobs attack every 6 ticks
            case MAGE: return 5;     // Magers attack every 5 ticks
            case RANGER: return 5;   // Rangers attack every 5 ticks
            case MELEE: return 7;    // Meleer attack every 7 ticks
            case JAD: return 8;      // Jad attacks every 8 ticks
            default: return 5;       // Default cycle
        }
    }

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