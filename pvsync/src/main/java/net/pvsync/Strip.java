package net.pvsync;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Takes the names of people the receiver may not know out of a text (titles, action bar, bossbar names).
 * A name made by a selector is a player's display name (hover = that player); it is removed together with the
 * ", " that joined it to the next name, so the rest reads exactly as if that person were not online.
 */
public final class Strip {
    private Strip() {}

    /** null = nothing to take out. */
    public static Component strip(Component c, Set<UUID> uuids, Set<String> names) {
        if (c == null || (uuids.isEmpty() && names.isEmpty())) return null;
        if (!Rewriter.refs(c, uuids, names, 0)) return null;
        if (self(c, uuids, names)) return Component.empty();
        Component out = node(c, uuids, names, 0);
        return out == null ? Component.empty() : out;
    }

    /** Does this node itself (not its children) stand for a hidden player? */
    static boolean self(Component c, Set<UUID> uuids, Set<String> names) {
        Style st = c.getStyle();
        if (st.getHoverEvent() instanceof HoverEvent.ShowEntity se && uuids.contains(se.entity().uuid)) return true;
        if (st.getInsertion() != null && names.contains(st.getInsertion().toLowerCase(Locale.ROOT))) return true;
        if (st.getClickEvent() instanceof ClickEvent.SuggestCommand sc) {
            String cmd = sc.command().toLowerCase(Locale.ROOT).trim();
            for (String n : names) if (cmd.equals("/tell " + n) || cmd.equals("/msg " + n)) return true;
        }
        return c.getContents() instanceof PlainTextContents t && names.contains(t.text().toLowerCase(Locale.ROOT));
    }

    private static boolean separator(Component c) {
        return c.getSiblings().isEmpty() && c.getContents() instanceof PlainTextContents t && t.text().equals(", ");
    }

    /** Rebuilt node, or null when the whole node is a hidden player. */
    private static Component node(Component c, Set<UUID> uuids, Set<String> names, int depth) {
        if (depth > 32) return c;
        if (self(c, uuids, names)) return null;
        MutableComponent base;
        if (c.getContents() instanceof TranslatableContents tc) {
            Object[] args = tc.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component ac) {
                    Component r = node(ac, uuids, names, depth + 1);
                    args[i] = r == null ? Component.empty() : r;
                } else if (args[i] instanceof String s && names.contains(s.toLowerCase(Locale.ROOT))) {
                    args[i] = "";
                }
            }
            base = Component.translatableWithFallback(tc.getKey(), tc.getFallback(), args);
        } else {
            base = c.plainCopy();
        }
        base.setStyle(c.getStyle());
        List<Component> kids = new ArrayList<>();
        List<Boolean> removed = new ArrayList<>();
        for (Component k : c.getSiblings()) {
            Component r = Rewriter.refs(k, uuids, names, 0) ? node(k, uuids, names, depth + 1) : k;
            kids.add(r);
            removed.add(r == null);
        }
        // drop the ", " next to each removed name (the one before it, else the one after it)
        for (int i = 0; i < kids.size(); i++) {
            if (!removed.get(i)) continue;
            int prev = i - 1;
            while (prev >= 0 && kids.get(prev) == null) prev--;
            if (prev >= 0 && separator(kids.get(prev))) {
                kids.set(prev, null);
                continue;
            }
            int next = i + 1;
            while (next < kids.size() && kids.get(next) == null && removed.get(next)) next++;
            if (next < kids.size() && kids.get(next) != null && separator(kids.get(next))) kids.set(next, null);
        }
        for (Component k : kids) if (k != null) base.append(k);
        return base;
    }
}
