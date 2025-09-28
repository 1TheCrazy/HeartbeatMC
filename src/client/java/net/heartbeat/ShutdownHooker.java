package net.heartbeat;

import net.fabricmc.loader.api.FabricLoader;
import net.heartbeat.util.EmbeddedExtractor;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ShutdownHooker {
    // We install a shutdown hook for this reason:
    // Windows holds a lock on files that are used, so when the JVM loads the mod JARs it locks them, so that they cannot be deleted
    // Therefore we have to wait until the lock is released (which happens when the JVM terminates)
    // But then we can't execute code anymore, so we spawn a detached Process that handles the deletion of files.
    // There is a Path.toFile().deleteOnExit(), but ChatGPT (the almighty) told that the Lock may still not be released when this executes
    // so we can't use that (and I had the shutdown hook already set up when I learnt about the method ¯\_(ツ)_/¯)
    public static void install() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                long pid = ProcessHandle.current().pid();

                // Get the Path to the current java.exe
                String javaHome = System.getProperty("java.home");
                Path javaBin = Paths.get(
                        javaHome, "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
                );

                // This may not show up in logs
                Heartbeat.LOGGER.info("Starting Replacer... ");

                // Launch DETACHED so it survives after JVM exits
                new ProcessBuilder(
                        "cmd.exe", "/c", "start", "", // detached new window
                        "powershell", "-ExecutionPolicy", "Bypass", "-NoExit", "-Command",
                        // Launch replacer JAR with the java bin we were just using
                        javaBin.toString(), "-jar", ".\\.heartbeat\\" + EmbeddedExtractor.REPLACER_JAR_FILE_NAME,
                        // 1st Arg: Running dir of Game
                        // 2nd Arg: String of jars that should be deleted ("mod1.jar,mod2.jar")
                        FabricLoader.getInstance().getGameDir().toString(), "mod1.jar,mod2.jar"
                ).start();

            } catch (Exception e) {
                // This may not show up in logs
                Heartbeat.LOGGER.error("Failed to start Replacer: {0}", e);
            }
        }, "jar-replacer"));
    }
}