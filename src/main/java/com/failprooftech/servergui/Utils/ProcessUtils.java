package com.failprooftech.servergui.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utilities for process inspection. Used by the Resources tab to get the server process PID
 * so we can monitor memory and CPU via OSHI. In Java 8, Process does not expose {@code pid()},
 * so we use reflection on the underlying implementation (UnixProcess, ProcessImpl, etc.).
 */
public final class ProcessUtils {

    private static final Method PID_METHOD;

    static {
        Method m = null;
        try {
            m = Process.class.getMethod("pid");
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            // Java 8: no pid() method
        }
        PID_METHOD = m;
    }

    private ProcessUtils() {}

    /**
     * Gets the process ID of the given process, if available.
     * On Java 9+ uses {@code process.pid()}. On Java 8 uses reflection on the underlying implementation.
     * @param process the process (may be the shell that started the server)
     * @return the PID, or -1 if not available (e.g. on some Windows/Java 8 setups)
     */
    public static long getPid(Process process) {
        if (process == null) return -1;
        try {
            if (PID_METHOD != null) {
                Object result = PID_METHOD.invoke(process);
                return result != null ? ((Number) result).longValue() : -1;
            }
            // Java 8: try "pid" on the process's concrete class (e.g. UNIXProcess, ProcessImpl)
            Class<?> c = process.getClass();
            while (c != null && c != Process.class) {
                try {
                    Field f = c.getDeclaredField("pid");
                    f.setAccessible(true);
                    Object value = f.get(process);
                    if (value instanceof Number) {
                        return ((Number) value).longValue();
                    }
                } catch (NoSuchFieldException ignored) {
                }
                c = c.getSuperclass();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }
}
