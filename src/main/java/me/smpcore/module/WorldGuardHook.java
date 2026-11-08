package me.smpcore.module;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import org.bukkit.Location;

/**
 * Optional WorldGuard link, done with reflection so SMPCore needs no WorldGuard jar to compile or run.
 * A spot counts as a safe zone when WorldGuard says PvP is denied there (flag pvp: deny).
 */
public final class WorldGuardHook {
    private final Method adapt;
    private final Method createQuery;
    private final Object container;
    private final Method testState;
    private final Object pvpFlag;
    private final Class<?> stateFlag;

    private WorldGuardHook(Method adapt, Object container, Method createQuery, Method testState, Object pvpFlag, Class<?> stateFlag) {
        this.adapt = adapt;
        this.container = container;
        this.createQuery = createQuery;
        this.testState = testState;
        this.pvpFlag = pvpFlag;
        this.stateFlag = stateFlag;
    }

    /** @return the hook, or null when WorldGuard (or the API it needs) isn't there. */
    public static WorldGuardHook create() {
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wg.getClass().getMethod("getPlatform").invoke(wg);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Method createQuery = container.getClass().getMethod("createQuery");
            Object sample = createQuery.invoke(container);
            Method testState = null;
            for (Method m : sample.getClass().getMethods()) {
                if (m.getName().equals("testState") && m.getParameterCount() == 3 && m.getParameterTypes()[2].isArray()) {
                    testState = m;
                    break;
                }
            }
            if (testState == null) return null;
            Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object pvp = flags.getField("PVP").get(null);
            Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Method adapt = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter").getMethod("adapt", Location.class);
            return new WorldGuardHook(adapt, container, createQuery, testState, pvp, stateFlag);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when a WorldGuard region denies PvP at this location. */
    public boolean pvpDenied(Location l) {
        try {
            Object wl = adapt.invoke(null, l);
            Object query = createQuery.invoke(container);
            Object flags = Array.newInstance(stateFlag, 1);
            Array.set(flags, 0, pvpFlag);
            Object allowed = testState.invoke(query, wl, null, flags);
            return allowed instanceof Boolean b && !b;
        } catch (Throwable t) {
            return false;
        }
    }
}
