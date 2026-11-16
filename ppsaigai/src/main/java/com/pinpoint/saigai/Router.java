package com.pinpoint.saigai;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * 入口。外（中継）から 25565 に来た接続の、最初の2つのパケット（ハンドシェイクとログインの名前）だけを読み、
 * 名前が表にあればこちらのサーバーへ、それ以外はもう一方のサーバーへ、そのまま流す。
 * 中身はそれ以上見ない（暗号・圧縮はつないだ先のサーバーとそのまま）。
 * サーバー一覧の問い合わせ（ステータス）には入口が自分で答える：どちらの人にも同じ名前・人数は「???」
 * （人数や名前を出すと、もう片方がいることが分かってしまう）。
 *
 * マイクラに頼らない作りなので、単体で試せる（main）。
 */
public final class Router {

    public interface Settings {
        String bind();
        int port();
        List<String> pinpoint();
        int pinpointPort();
        int guestPort();
        String motd();
    }

    private final Settings cfg;
    private final Consumer<String> log;
    private volatile ServerSocket server;

    public Router(Settings cfg, Consumer<String> log) {
        this.cfg = cfg;
        this.log = log;
    }

    public void start() throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(cfg.bind() == null || cfg.bind().isEmpty() ? new InetSocketAddress(cfg.port()) : new InetSocketAddress(cfg.bind(), cfg.port()));
        server = ss;
        Thread t = new Thread(() -> {
            while (!ss.isClosed()) {
                try {
                    Socket c = ss.accept();
                    Thread h = new Thread(() -> handle(c), "ppsaigai-router-conn");
                    h.setDaemon(true);
                    h.start();
                } catch (IOException e) {
                    if (!ss.isClosed()) log.accept("入口の受け付けで失敗 " + e);
                }
            }
        }, "ppsaigai-router");
        t.setDaemon(true);
        t.start();
        log.accept("入口を開いた :" + cfg.port() + "（表の人→" + cfg.pinpointPort() + "・ほか→" + cfg.guestPort() + "）");
    }

    public void stop() {
        ServerSocket ss = server;
        server = null;
        if (ss != null) try { ss.close(); } catch (IOException ignored) {}
    }

    // ------------------------------------------------------------------ 1本の接続

    private void handle(Socket client) {
        Socket backend = null;
        try {
            client.setTcpNoDelay(true);
            client.setSoTimeout(15_000);
            InputStream in = client.getInputStream();
            int first = in.read();
            if (first < 0) { client.close(); return; }
            if (first == 0xFE) { client.close(); return; } // 古い形の問い合わせ：答えない
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte[] hs = readPacket(in, first, raw);
            DataInputStream d = new DataInputStream(new java.io.ByteArrayInputStream(hs));
            int id = readVarInt(d);
            if (id != 0) { client.close(); return; }
            int protocol = readVarInt(d);
            readString(d);
            d.readUnsignedShort();
            int next = readVarInt(d);

            if (next == 1) { status(client, in, protocol); return; }

            int b = in.read();
            if (b < 0) { client.close(); return; }
            byte[] login = readPacket(in, b, raw);
            DataInputStream l = new DataInputStream(new java.io.ByteArrayInputStream(login));
            readVarInt(l);
            String name = readString(l);
            boolean pp = cfg.pinpoint().stream().anyMatch(n -> n.equalsIgnoreCase(name));
            int port = pp ? cfg.pinpointPort() : cfg.guestPort();
            log.accept("入口：" + name + " → " + (pp ? "こちら" : "もう一方") + " :" + port);

            backend = new Socket();
            try {
                backend.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            } catch (IOException e) {
                log.accept("入口：" + port + " のサーバーが動いていない（" + name + " は入れなかった）");
                kick(client, "Server is starting. Please try again in a moment.");
                return;
            }
            backend.setTcpNoDelay(true);
            client.setSoTimeout(0);
            OutputStream bo = backend.getOutputStream();
            bo.write(raw.toByteArray());
            bo.flush();
            Socket bk = backend;
            Thread up = new Thread(() -> pipe(client, bk), "ppsaigai-router-up");
            up.setDaemon(true);
            up.start();
            pipe(bk, client);
        } catch (Exception e) {
            closeQuiet(client);
            closeQuiet(backend);
        }
    }

    private void status(Socket client, InputStream in, int protocol) throws IOException {
        OutputStream out = client.getOutputStream();
        String json = "{\"version\":{\"name\":\"1.21.11\",\"protocol\":" + protocol + "},"
                + "\"description\":{\"text\":\"" + cfg.motd().replace("\\", "\\\\").replace("\"", "\\\"") + "\"},"
                + "\"enforcesSecureChat\":false}";
        for (int i = 0; i < 2; i++) {
            int b = in.read();
            if (b < 0) break;
            byte[] p = readPacket(in, b, null);
            DataInputStream d = new DataInputStream(new java.io.ByteArrayInputStream(p));
            int id = readVarInt(d);
            if (id == 0) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                writeVarInt(body, 0);
                writeString(body, json);
                writePacket(out, body.toByteArray());
            } else if (id == 1) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                writeVarInt(body, 1);
                body.write(p, p.length - 8, 8);
                writePacket(out, body.toByteArray());
                break;
            }
        }
        out.flush();
        client.close();
    }

    private static void kick(Socket client, String text) {
        try {
            OutputStream out = client.getOutputStream();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeVarInt(body, 0);
            writeString(body, "{\"text\":\"" + text + "\"}");
            writePacket(out, body.toByteArray());
            out.flush();
        } catch (IOException ignored) {
        }
        closeQuiet(client);
    }

    private static void pipe(Socket from, Socket to) {
        byte[] buf = new byte[16384];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            closeQuiet(from);
            closeQuiet(to);
        }
    }

    private static void closeQuiet(Socket s) {
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    // ------------------------------------------------------------------ パケットの読み書き

    /** 長さ（VarInt・最初の1バイトは読んである）＋中身を読み、raw があれば読んだバイトをそのまま足す */
    static byte[] readPacket(InputStream in, int firstByte, ByteArrayOutputStream raw) throws IOException {
        int len = 0, shift = 0, b = firstByte;
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (true) {
            if (b < 0) throw new IOException("途中で切れた");
            head.write(b);
            len |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            if (shift > 21) throw new IOException("長さが変");
            b = in.read();
        }
        if (len <= 0 || len > 32767) throw new IOException("長さが変 " + len);
        byte[] body = in.readNBytes(len);
        if (body.length != len) throw new IOException("途中で切れた");
        if (raw != null) { raw.write(head.toByteArray()); raw.write(body); }
        return body;
    }

    static int readVarInt(DataInputStream d) throws IOException {
        int v = 0, shift = 0;
        while (true) {
            int b = d.readUnsignedByte();
            v |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return v;
            shift += 7;
            if (shift > 28) throw new IOException("VarInt が長い");
        }
    }

    static String readString(DataInputStream d) throws IOException {
        int n = readVarInt(d);
        if (n < 0 || n > 32767 * 3) throw new IOException("文字が長い");
        byte[] b = new byte[n];
        d.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    static void writeVarInt(ByteArrayOutputStream o, int v) {
        while ((v & ~0x7F) != 0) { o.write((v & 0x7F) | 0x80); v >>>= 7; }
        o.write(v);
    }

    static void writeString(ByteArrayOutputStream o, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(o, b.length);
        o.write(b);
    }

    static void writePacket(OutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        writeVarInt(p, body.length);
        p.write(body);
        out.write(p.toByteArray());
    }

    /** 単体で試す：java Router.java <入口> <こちら> <もう一方> [名前,名前…] */
    public static void main(String[] a) throws Exception {
        int port = Integer.parseInt(a[0]), pp = Integer.parseInt(a[1]), gs = Integer.parseInt(a[2]);
        new Router(new Settings() {
            public String bind() { return "127.0.0.1"; }
            public int port() { return port; }
            public List<String> pinpoint() { return List.of(a.length > 3 ? a[3].split(",") : new String[0]); }
            public int pinpointPort() { return pp; }
            public int guestPort() { return gs; }
            public String motd() { return "A Minecraft Server"; }
        }, s -> System.out.println("ROUTER " + s)).start();
        Thread.sleep(Long.MAX_VALUE);
    }
}
