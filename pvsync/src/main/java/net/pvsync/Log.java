package net.pvsync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Everything this mod says goes to the server log only, always prefixed "[pv]" (H23). */
public final class Log {
    private Log() {}

    private static final Logger L = LoggerFactory.getLogger("pv");

    public static void info(String s) { L.info("[pv] " + s); }

    public static void warn(String s) { L.warn("[pv] " + s); }
}
