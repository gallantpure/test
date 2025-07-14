package net.runelite.client.plugins.inferno;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;

import net.runelite.client.plugins.inferno.displaymodes.InfernoWaveDisplayMode;
import lombok.AccessLevel;
import lombok.Setter;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import static net.runelite.client.plugins.inferno.InfernoWaveMappings.addWaveComponent;

@Singleton
public class InfernoWaveOverlay extends Overlay
{
	private final InfernoPlugin plugin;
	private final InfernoConfig config;
	private final PanelComponent panelComponent;

	@Setter(AccessLevel.PACKAGE)
	private Color waveHeaderColor;

	@Setter(AccessLevel.PACKAGE)
	private Color waveTextColor;

	@Setter(AccessLevel.PACKAGE)
	private InfernoWaveDisplayMode displayMode;

	@Inject
	InfernoWaveOverlay(final InfernoPlugin plugin, final InfernoConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		this.panelComponent = new PanelComponent();
		setPosition(OverlayPosition.TOP_RIGHT);
		setPriority(OverlayPriority.HIGH);
		panelComponent.setPreferredSize(new Dimension(160, 0));
	}

	public Dimension render(final Graphics2D graphics)
	{
		panelComponent.getChildren().clear();

		// Add nibbler counter at the top
		addNibblerCounter();

		if (displayMode == InfernoWaveDisplayMode.CURRENT ||
				displayMode == InfernoWaveDisplayMode.BOTH)
		{
			addWaveComponent(
					config,
					panelComponent,
					"Current Wave (Wave " + plugin.getCurrentWaveNumber() + ")",
					plugin.getCurrentWaveNumber(),
					waveHeaderColor,
					waveTextColor
			);
		}

		if (displayMode == InfernoWaveDisplayMode.NEXT ||
				displayMode == InfernoWaveDisplayMode.BOTH)
		{
			addWaveComponent(
					config,
					panelComponent,
					"Next Wave (Wave " + plugin.getNextWaveNumber() + ")",
					plugin.getNextWaveNumber(),
					waveHeaderColor,
					waveTextColor
			);
		}

		return panelComponent.render(graphics);
	}

	private void addNibblerCounter()
	{
		// Count living nibblers
		int nibblerCount = 0;
		for (InfernoNPC infernoNPC : plugin.getInfernoNpcs())
		{
			if (infernoNPC.getType() == InfernoNPC.Type.NIBBLER)
			{
				nibblerCount++;
			}
		}

		// Only show if there are nibblers
		if (nibblerCount == 0)
		{
			return;
		}

		// Choose color based on count
		Color nibblerColor;
		if (nibblerCount >= 5)
		{
			nibblerColor = Color.RED;        // Many nibblers - danger!
		}
		else if (nibblerCount >= 3)
		{
			nibblerColor = Color.ORANGE;     // Several nibblers - caution
		}
		else
		{
			nibblerColor = Color.YELLOW;     // Few nibblers - manageable
		}

		// Add nibbler count line to panel
		panelComponent.getChildren().add(LineComponent.builder()
				.left("Nibblers Alive:")
				.leftColor(Color.WHITE)
				.right(String.valueOf(nibblerCount))
				.rightColor(nibblerColor)
				.build());

		// Add spacing line after nibbler counter
		panelComponent.getChildren().add(LineComponent.builder()
				.left("")
				.right("")
				.build());
	}
}