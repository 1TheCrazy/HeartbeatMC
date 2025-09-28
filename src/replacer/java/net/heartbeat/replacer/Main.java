package net.heartbeat.replacer;

import java.io.IOException;
import java.nio.file.*;

public class Main {
    public static final int REPLACER_VERSION = 1;

    // 1st Arg: Running dir of Game
    // 2nd Arg: String of jars that should be deleted ("mod1.jar,mod2.jar")
    public static void main(String[] args) {
        Path gameDir = Path.of(args[0]);
        Path dotHeartbeatDir = gameDir.resolve(".heartbeat");
        Path updatesDir = dotHeartbeatDir.resolve("updates");
        Path modsDir = gameDir.resolve("mods");
        String[] toBeDeletedJarNames = args[1].split(",");

        // Copy every new version jar from updatesDir to modsDir
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(updatesDir)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    Path dest = modsDir.resolve(file.getFileName());
                    Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);

                    System.out.println("Moved: " + file + " -> " + dest);
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to move file: " + e);
        }

        // Delete every jar that should be deleted:
        for(String jarName : toBeDeletedJarNames){
            Path file = modsDir.resolve(jarName);
            boolean success = file.toFile().delete();

            if(success)
                System.out.println("Deleted old JAR '" + jarName + "'");
            else
                System.out.println("Couldn't delete old JAR '" + jarName + "'");
        }
    }
}
