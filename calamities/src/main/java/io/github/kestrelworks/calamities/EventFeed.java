package io.github.kestrelworks.calamities;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.MinecraftServer;

/**
 * World event feed: an external controller connects over TCP and sends one JSON object per line.
 * {"token":"…","t":"quake","mag":0.6} / {"t":"storm"} / {"t":"gale"} / {"t":"spirit","name":…,"id":…,"tex":…,"sig":…}
 * Events older than {@code maxAgeMs} (field "at", epoch ms) are dropped so a late reconnect never replays stale ones.
 */
public final class EventFeed {

    private static volatile ServerSocket socket;
    private static volatile Thread thread;
    private static final long MAX_AGE_MS = 15_000;

    private EventFeed() {}

    public static synchronized void start(MinecraftServer server) {
        stop();
        CalamityConfig c = CalamityConfig.get();
        if (!c.feedEnabled) return;
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(InetAddress.getByName(c.feedHost), c.feedPort));
            socket = ss;
            Thread t = new Thread(() -> acceptLoop(server, ss), "calamities-feed");
            t.setDaemon(true);
            t.start();
            thread = t;
            Calamities.LOG.info("event feed listening on {}:{}", c.feedHost, c.feedPort);
        } catch (Exception e) {
            Calamities.LOG.warn("event feed could not start on {}:{}", c.feedHost, c.feedPort, e);
        }
    }

    public static synchronized void stop() {
        ServerSocket ss = socket;
        socket = null;
        if (ss != null) try { ss.close(); } catch (Exception ignored) {}
        thread = null;
    }

    private static void acceptLoop(MinecraftServer server, ServerSocket ss) {
        while (!ss.isClosed()) {
            try {
                Socket s = ss.accept();
                Thread t = new Thread(() -> handle(server, s), "calamities-feed-conn");
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                if (!ss.isClosed()) Calamities.LOG.warn("event feed accept failed", e);
            }
        }
    }

    private static void handle(MinecraftServer server, Socket s) {
        String who = String.valueOf(s.getRemoteSocketAddress());
        Calamities.LOG.info("event feed: controller connected {}", who);
        try (s; BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = s.getOutputStream()) {
            s.setTcpNoDelay(true);
            s.setSoTimeout(60_000);
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equals("PING")) { out.write("PONG\n".getBytes(StandardCharsets.US_ASCII)); out.flush(); continue; }
                JsonObject j;
                try { j = JsonParser.parseString(line).getAsJsonObject(); } catch (Exception e) { continue; }
                String token = CalamityConfig.get().feedToken;
                if (token != null && !token.isEmpty() && !token.equals(str(j, "token"))) {
                    Calamities.LOG.warn("event feed: bad token from {}", who);
                    return;
                }
                long at = j.has("at") ? j.get("at").getAsLong() : System.currentTimeMillis();
                if (System.currentTimeMillis() - at > MAX_AGE_MS) {
                    Calamities.LOG.info("event feed: dropped stale {}", line);
                    continue;
                }
                server.execute(() -> dispatch(server, j));
                out.write(("OK " + str(j, "seq") + "\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
        } catch (Exception e) {
            Calamities.LOG.info("event feed: controller {} gone ({})", who, e.toString());
        }
    }

    static void dispatch(MinecraftServer server, JsonObject j) {
        String t = str(j, "t");
        String src = str(j, "src");
        try {
            switch (t) {
                case "quake" -> Disasters.quake(server, j.has("mag") ? j.get("mag").getAsFloat() : 0.5f, src);
                case "storm" -> Disasters.thunder(server, src);
                case "gale" -> Disasters.wind(server, src);
                case "blizzard" -> Disasters.blizzard(server, src);
                case "calm" -> Disasters.calm(server);
                case "reveal" -> Disasters.reveal(j.has("step") ? j.get("step").getAsFloat() : 0f,
                        j.has("to") ? j.get("to").getAsFloat() : -1f);
                case "voice" -> Disasters.voice(j.has("stage") ? j.get("stage").getAsInt() : 0);
                case "spirit" -> Disasters.ghost(server, str(j, "id"), str(j, "name"), str(j, "tex"), str(j, "sig"));
                default -> Calamities.LOG.info("event feed: unknown event {}", t);
            }
        } catch (Exception e) {
            Calamities.LOG.warn("event feed: {} failed", t, e);
        }
    }

    private static String str(JsonObject j, String k) {
        return j.has(k) && !j.get(k).isJsonNull() ? j.get(k).getAsString() : "";
    }
}
