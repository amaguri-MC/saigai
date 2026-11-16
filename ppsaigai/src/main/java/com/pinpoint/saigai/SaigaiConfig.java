package com.pinpoint.saigai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** config/ppsaigai.json（合図を送る側のサーバーに置く） */
public class SaigaiConfig {

    /** 落ちる・死ぬと、相手のサーバーで災害になる人 */
    public List<String> sources = List.of();
    /** 雷・暴風のキーを押せる人 */
    public List<String> operators = List.of();
    /** /ppsaigai（雷・暴風・吹雪・地震・亡霊などをコマンドで出す）を使える人（テスト用）。コンソールはいつでも使える */
    public List<String> testers = List.of("Amaguri");

    /** 落下ダメージ（ハート半分＝1）→ 地震の強さ 0..1：base + ダメージ × perDamage（上限1） */
    public float quakeBase = 0.22f;
    public float quakePerDamage = 0.078f;

    /** 相手のサーバーの MOD（Natural Calamities）が待っている所 */
    public String feedHost = "127.0.0.1";
    public int feedPort = 25590;
    public String feedToken = "";

    /** 入口：外（中継）からの 25565 を、名前でどちらのサーバーへ流すか決める */
    public boolean routerEnabled = true;
    public String routerBind = "";
    public int routerPort = 25565;
    /** この名前の人はこのサーバーへ。それ以外は全員もう一方へ */
    public List<String> routerPinpoint = List.of();
    public int pinpointPort = 25571;
    public int guestPort = 25572;
    /** サーバー一覧に出す名前（どちらの側の人にも同じ物が出る） */
    public String routerMotd = "A Minecraft Server";

    /** 1人用ワールド（テスト）でも合図・入口を動かす */
    public boolean inSingleplayer = false;

    private static SaigaiConfig current = new SaigaiConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static SaigaiConfig get() { return current; }

    public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("ppsaigai.json"); }

    public static void load() {
        Path f = file();
        try {
            if (Files.exists(f)) {
                SaigaiConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), SaigaiConfig.class);
                if (c != null) current = c;
            }
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(current), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Saigai.LOG.warn("(ppsaigai) 設定を読めない {}", f, e);
        }
    }
}
