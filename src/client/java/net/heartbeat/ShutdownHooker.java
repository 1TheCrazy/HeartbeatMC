package net.heartbeat;

public class ShutdownHooker {
    public static void install() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                long pid = ProcessHandle.current().pid();

                // Launch DETACHED so it survives after JVM exits
                new ProcessBuilder(
                        "cmd.exe", "/c", "start", "", // detached new window
                        "powershell", "-ExecutionPolicy", "Bypass", "-NoExit","-Command", "Write-Output 'Hello from Java!'"
                ).start();

            } catch (Exception e) {

            }
        }, "jar-swapper-win"));
    }
}