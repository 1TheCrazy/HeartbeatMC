package net.heartbeat.util;

import com.google.common.base.Objects;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.heartbeat.Heartbeat;
import net.heartbeat.HeartbeatClient;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {
    private static class ModInfo {
        String jarName;
        String modName;

        public ModInfo(String jarName, String modName){
            this.jarName = jarName;
            this.modName = modName;
        }
    }

    public static void check(){
        Heartbeat.LOGGER.info("Checking for updates...");

        // Get a Map of every Mod's Hash that is loaded mapped to its relevant Info
        Map<String, ModInfo> hash2JarMap = getHash2JarMap();

        // Query Modrinth for updates
        queryModrinth(hash2JarMap);
    }

    private static Map<String, ModInfo> getHash2JarMap(){
        Map<String, ModInfo> hash2Jar = new HashMap<>();

        // Loop through every Mod and hash the jar
        for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
            ModOrigin origin = c.getOrigin();
            if (origin.getKind() != ModOrigin.Kind.PATH)
                continue;

            // Compute the hash
            for (Path p : origin.getPaths()) {
                // We hit a Dir and not a File
                if(!p.toString().endsWith(".jar"))
                    continue;

                String hash = sha1(p);

                // Add to hashes that should be checked (if not empty)
                if(!Objects.equal(hash, ""))
                    hash2Jar.put(hash, new ModInfo(p.getFileName().toString(), c.getMetadata().getName()));
            }
        }

        return hash2Jar;
    }

    private static JsonObject constructBody(Map<String, ModInfo> hash2Jar){
        // Construct the HTTP Request Body
        JsonObject body = new JsonObject();

        // add Hashes to body
        JsonArray hashes = new JsonArray();
        for(String hash: hash2Jar.keySet())
            hashes.add(hash);
        body.add("hashes", hashes);

        // add algorithm
        body.addProperty("algorithm", "sha1");

        // add loaders
        JsonArray loaders = new JsonArray();
        loaders.add("fabric");
        body.add("loaders", loaders);

        // add game version
        JsonArray versions = new JsonArray();
        versions.add(FabricLoader.getInstance().getRawGameVersion());
        body.add("game_versions", versions);

        return body;
    }

    private static void queryModrinth(Map<String, ModInfo> hash2Jar){
        // Construct body
        JsonObject body = constructBody(hash2Jar);

        // send request
        CompletableFuture<String> res = sendHttpQuery(body.toString());

        // handle result
        res.thenAccept(resBody -> {
            // See https://docs.modrinth.com/api/operations/getlatestversionsfromhashes/
            JsonObject root = JsonParser.parseString(resBody).getAsJsonObject();

            for (String hash : root.keySet()) {
                JsonObject version = root.getAsJsonObject(hash);
                JsonObject file = version.get("files").getAsJsonArray().get(0).getAsJsonObject();

                String newestHash = file.get("hashes").getAsJsonObject().get("sha1").getAsString();
                String downloadUrl = file.get("url").getAsString();
                String fileName = file.get("filename").getAsString();

                if(!java.util.Objects.equals(hash, newestHash)){
                    // Get the associated ModInfo
                    ModInfo info = hash2Jar.get(hash);

                    // kickoff the download of the new file
                    kickoffDownload(downloadUrl, fileName, info);
                }
            }
        });
    }

    private static void kickoffDownload(String downloadUri, String fileName, ModInfo info){
        Heartbeat.LOGGER.info("Downloading update for'{}' from '{}'({})", info.modName, downloadUri, fileName);

        HttpClient client = HttpClient.newHttpClient();

        Path downloadPath = FileUtil.getUpdatePath().resolve(fileName);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(downloadUri))
                .build();

        // async download
        CompletableFuture<HttpResponse<Path>> future =
                client.sendAsync(req, HttpResponse.BodyHandlers.ofFile(downloadPath));

        future.thenAccept(resp -> {
            if (resp.statusCode() == 200) {
                // Mark that this mod's jar should be removed on restart
                HeartbeatClient.markJarAsRemovable(info.jarName);

                // Display Toast
                // This is the reason for the ModInfo class
                // We don't get the name of the mod from request as that only includes version info,
                // so we just store the name as well (too bad we don't get tuples)
                MinecraftClient.getInstance().execute(() -> ToastUtil.showUpdatedToast(info.modName));
            }
            else{
                // Display Error Toast
                MinecraftClient.getInstance().execute(() -> ToastUtil.showErrorToast(info.modName));
            }
        });
    }

    private static CompletableFuture<String> sendHttpQuery(String jsonBody){
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.modrinth.com/v2/version_files/update"))
                .header("User-Agent", "MyMod/1.0 (+github.com/me/my-mod)")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    // Vibe coded
    private static String sha1(Path jar){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-1");

            try (var in = Files.newInputStream(jar)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
            }

            byte[] digest = md.digest();

            StringBuilder sb = new StringBuilder(digest.length * 2);

            for (byte b : digest)
                sb.append(String.format("%02x", b));

            return sb.toString();
        }
        catch(Exception ex){
            Heartbeat.LOGGER.error("Error while computing sha1 for '{}': {}", jar, ex);
            return "";
        }
    }
}
