package io.github.kestrelworks.calamities;

import java.util.ArrayList;
import java.util.List;

/** Client side: voice clips announced by the server, waiting for the bolt at that spot to roll its thunder. */
public final class VoiceQueue {
    private VoiceQueue() {}

    private record Item(double x, double y, double z, int variant, long expires) {}

    private static final List<Item> items = new ArrayList<>();

    public static synchronized void add(double x, double y, double z, int variant) {
        long now = System.currentTimeMillis();
        items.removeIf(i -> i.expires < now);
        items.add(new Item(x, y, z, variant, now + 4000));
    }

    /** The variant announced for a bolt at this spot (removed once taken), or -1 if none. */
    public static synchronized int take(double x, double y, double z) {
        long now = System.currentTimeMillis();
        items.removeIf(i -> i.expires < now);
        for (int k = 0; k < items.size(); k++) {
            Item i = items.get(k);
            double dx = i.x - x, dy = i.y - y, dz = i.z - z;
            if (dx * dx + dy * dy + dz * dz < 4.0) { items.remove(k); return i.variant; }
        }
        return -1;
    }

    public static synchronized void clear() { items.clear(); }
}
