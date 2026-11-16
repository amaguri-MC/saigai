package com.pinpoint.saigai;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 相手のサーバー（Natural Calamities の event feed）へ合図を送る。
 * 1行1つの JSON。つながっていない間の合図は溜めておき、つながったら15秒以内の物だけ送る（古い地震を後から起こさない）。
 */
public final class Link {

    private Link() {}

    private static final LinkedBlockingDeque<JsonObject> queue = new LinkedBlockingDeque<>();
    private static final AtomicLong seq = new AtomicLong();
    private static volatile Thread thread;
    private static volatile boolean running;
    private static volatile boolean connected;
    private static volatile long sent, dropped;
    private static final long MAX_AGE_MS = 15_000;

    public static boolean connected() { return connected; }
    public static String status() { return (connected ? "つながっている" : "つながっていない") + " 送った" + sent + " 捨てた" + dropped + " 待ち" + queue.size(); }

    public static synchronized void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(Link::loop, "ppsaigai-link");
        t.setDaemon(true);
        t.start();
        thread = t;
    }

    public static synchronized void stop() {
        running = false;
        Thread t = thread;
        if (t != null) t.interrupt();
        thread = null;
    }

    /** 合図を送る（どのスレッドからでもよい） */
    public static void send(JsonObject j) {
        j.addProperty("at", System.currentTimeMillis());
        j.addProperty("seq", seq.incrementAndGet());
        String token = SaigaiConfig.get().feedToken;
        if (token != null && !token.isEmpty()) j.addProperty("token", token);
        queue.offer(j);
        Saigai.LOG.info("(ppsaigai) 合図 {} {}", j.get("t").getAsString(), connected ? "" : "（今はつながっていない・つながったら送る）");
    }

    private static void loop() {
        while (running) {
            SaigaiConfig c = SaigaiConfig.get();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(c.feedHost, c.feedPort), 3000);
                s.setTcpNoDelay(true);
                s.setSoTimeout(0);
                OutputStream out = s.getOutputStream();
                connected = true;
                Saigai.LOG.info("(ppsaigai) 相手につながった {}:{}", c.feedHost, c.feedPort);
                Thread reader = new Thread(() -> drain(s), "ppsaigai-link-read");
                reader.setDaemon(true);
                reader.start();
                long lastPing = System.currentTimeMillis();
                while (running && !s.isClosed()) {
                    JsonObject j = queue.poll(1, TimeUnit.SECONDS);
                    if (j == null) {
                        if (System.currentTimeMillis() - lastPing > 10_000) {
                            out.write("PING\n".getBytes(StandardCharsets.US_ASCII));
                            out.flush();
                            lastPing = System.currentTimeMillis();
                        }
                        if (!reader.isAlive()) throw new java.io.IOException("相手が切った");
                        continue;
                    }
                    if (System.currentTimeMillis() - j.get("at").getAsLong() > MAX_AGE_MS) { dropped++; continue; }
                    try {
                        out.write((j + "\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        sent++;
                    } catch (Exception e) {
                        queue.offerFirst(j);
                        throw e;
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                if (connected) Saigai.LOG.info("(ppsaigai) 相手から切れた（{}）。つなぎ直す", e.toString());
            }
            connected = false;
            try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
        }
        connected = false;
    }

    private static void drain(Socket s) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            while (in.readLine() != null) { /* OK / PONG */ }
        } catch (Exception ignored) {
        }
    }
}
