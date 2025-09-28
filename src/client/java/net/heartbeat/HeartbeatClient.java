package net.heartbeat;

import net.fabricmc.api.ClientModInitializer;
import net.heartbeat.util.FileUtil;

public class HeartbeatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Initialize dirs & jar
		FileUtil.initialize();

		// Install Shutdown Hook
		ShutdownHooker.install();
	}
}