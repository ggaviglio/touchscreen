package com.touchscreen;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.events.ClientTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;

import net.runelite.client.input.MouseListener;
import net.runelite.client.plugins.PluginManager;

@Slf4j
@PluginDescriptor(
		name = "Touchscreen",
		description = "Add controls for touchscreen devices.",
		tags = {"click", "hold", "mouse", "camera", "drag", "delay"},
		enabledByDefault = false
)

public class TouchscreenPlugin extends Plugin implements MouseListener
{
//	@Inject
	private PluginManager pluginManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private TouchscreenConfig config;

	ArrayList<WidgetInfo> containerList = new ArrayList<WidgetInfo>(Arrays.asList(WidgetInfo.INVENTORY));
	private MouseEvent consumedMouseEvent;
	private MouseEvent consumedMouseDragEvent;
	private Point pressInitialPoint;
	private long pressInitialTime;
	private boolean pressIsContainer;
	private boolean pressConsumed = false;
	private boolean rebindMiddleDrag = false;
	private boolean blockLeftRelease = false;

	@Provides
	TouchscreenConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TouchscreenConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		updateContainerList();
		mouseManager.registerMouseListener(this);
	}

	@Override
	protected void shutDown() throws Exception
	{
		mouseManager.unregisterMouseListener(this);
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent mouseEvent)
	{
		if (SwingUtilities.isLeftMouseButton(mouseEvent) && (client.getGameState() == GameState.LOGGED_IN))
		{
			if (pressConsumed)
			{
				//Allow mouse event
				pressConsumed = false;
			}
			else
			{
				// Fix for yellow/red-x bugs on touch screen devices
				// Postpone click event until mouse release, allowing right-click-menu to update properly
				consumedMouseEvent = mouseEvent;
				consumedMouseDragEvent = consumedMouseEvent;
				mouseEvent.consume();
				clientThread.invokeLater(() ->
				{
					pressInitialPoint = new Point(consumedMouseEvent.getX(), consumedMouseEvent.getY());
					pressInitialTime = System.currentTimeMillis();
					pressIsContainer = isPointOnContainer(pressInitialPoint);
					pressConsumed = true;
				});
			}
		}
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent mouseEvent)
	{
		if (SwingUtilities.isLeftMouseButton(mouseEvent))
		{
			if (pressConsumed)
			{
				if (pressIsContainer)
				{
					dispatchMouseEvent(consumedMouseEvent);
					dispatchMouseEvent(consumedMouseEvent, mouseEvent.getID(), consumedMouseEvent.getButton());
				}
				else
				{
					// Use the current release position instead of the original click position
					// When clicking the viewport, original position may not work if right-click-menu has changed
					dispatchMouseEvent(mouseEvent, consumedMouseEvent.getID(), consumedMouseEvent.getButton());
					dispatchMouseEvent(mouseEvent, mouseEvent.getID(), consumedMouseEvent.getButton());
				}
			}
			// If camera dragging occurred, release the middle mouse
			else if (rebindMiddleDrag)
			{
				dispatchMouseEvent(mouseEvent, mouseEvent.getID(), MouseEvent.BUTTON2);
				rebindMiddleDrag = false;
			}
			// If press was rebound to right mouse, block left mouse release
			else if (blockLeftRelease)
			{
				blockLeftRelease = false;
			}
			else
			{
				return mouseEvent;
			}
			mouseEvent.consume();
		}
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent mouseEvent)
	{
		// mouseDragged events are always MouseEvent.Button0, regardless of which key is being held
		if (pressConsumed)
		{
			consumedMouseDragEvent = mouseEvent;
			int distance = pressInitialPoint.distanceTo(new Point(mouseEvent.getX(), mouseEvent.getY()));
			if (pressIsContainer && (config.containerDragThreshold() <= distance))
			{
				dispatchMouseEvent(consumedMouseEvent);
			}
			else if (!pressIsContainer && (config.rotateOnDragThreshold() <= distance))
			{
				rebindMiddleDrag = true;
				pressConsumed = false;
				dispatchMouseEvent(mouseEvent, consumedMouseEvent.getID(), MouseEvent.BUTTON2);
			}
		}
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick)
	{
		if (pressConsumed)
		{
			if (pressInitialTime <= System.currentTimeMillis() - config.clickHoldThreshold())
			{
				pressConsumed = false;
				blockLeftRelease = true;
				dispatchMouseEvent(consumedMouseDragEvent, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3);
				dispatchMouseEvent(consumedMouseDragEvent, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON3);
			}
		}
	}

	private void updateContainerList()
	{
		for (WidgetInfo i : WidgetInfo.values())
		{
			if (i.name().contains("ITEM_CONTAINER") || i.name().contains("ITEMS_CONTAINER"))
				if (!containerList.contains(i))
					containerList.add(i);
		}
	}

	private boolean isPointOnContainer(Point point)
	{
		for (WidgetInfo i : containerList)
		{
			Widget w = client.getWidget(i);
			if (w != null && !w.isHidden() && w.contains(point))
				return true;
		}
		return false;
	}

	public Point fixStretchedCoordinates(int x, int y)
	{
		// Mouse click coordinates are incorrect during stretched mode
		if (client.isStretchedEnabled())
		{
			final Dimension stretched = client.getStretchedDimensions();
			final Dimension real = client.getRealDimensions();
			final double width = (stretched.width / real.getWidth());
			final double height = (stretched.height / real.getHeight());
			x = (int) Math.ceil(x * width);
			y = (int) Math.ceil(y * height);
//			x = (int) (x * width);
//			y = (int) (y * height);
		}
		return new Point(x,y);
	}

	private MouseEvent fixMouseEvent(MouseEvent e, int id, int button)
	{
		Point point = null;
		if (rebindMiddleDrag)
			point = new Point(e.getX(), e.getY());
		else
			point = fixStretchedCoordinates(e.getX(), e.getY());
		return new MouseEvent(
				(Component) e.getSource(),
				id,
				e.getWhen(),
				e.getModifiersEx(),
				point.getX(),
				point.getY(),
				e.getClickCount(),
				e.isPopupTrigger(),
				button
		);
	}

	private void dispatchMouseEvent(MouseEvent e, int id, int button)
	{
		client.getCanvas().dispatchEvent(fixMouseEvent(e, id, button));
	}

	private void dispatchMouseEvent(MouseEvent e)
	{
		dispatchMouseEvent(e, e.getID(), e.getButton());
	}
}