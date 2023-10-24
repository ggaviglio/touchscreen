package com.touchscreen;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("touchscreen")
public interface TouchscreenConfig extends Config
{
	@ConfigItem(
			keyName = "rotateOnDragThreshold",
			name = "Camera drag distance",
			description = "Only start rotating the camera after dragging a minimum number of pixels.",
			position = 0
	)
	default int rotateOnDragThreshold()
	{
		return 30;
	}

	@ConfigItem(
			keyName = "containerDragThreshold",
			name = "Item drag distance",
			description = "Only drag items after dragging a minimum number of pixels.",
			position = 1
	)
	default int containerDragThreshold()
	{
		return 5;
	}

	@ConfigItem(
			keyName = "clickHoldThreshold",
			name = "Click hold timer (ms)",
			description = "After timer expires, left click hold will be rebound to right click.",
			position = 2
	)
	default int clickHoldThreshold()
	{
		return 500;
	}
}
