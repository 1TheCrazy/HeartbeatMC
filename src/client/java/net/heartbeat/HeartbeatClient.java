package net.heartbeat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.heartbeat.util.FileUtil;
import net.heartbeat.util.UpdateChecker;

public class HeartbeatClient implements ClientModInitializer {
	private static String removableJarsString;

	@Override
	public void onInitializeClient() {
		// Initialize dirs & jar
		FileUtil.initialize();

		// Install Shutdown Hook
		ShutdownHooker.install();

		// Schedule Update Check
		scheduleUpdateCheck();
	}

	public static void markJarAsRemovable(String jar){
		removableJarsString += "," + jar;
	}

	public static String getRemovableJarsString(){
		return removableJarsString;
	}

	private void scheduleUpdateCheck(){
		// Only Check for updates when resources are loaded since translation keys may not yet be loaded in Main Menu if we show a Toast too early
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> UpdateChecker.check());
	}
}