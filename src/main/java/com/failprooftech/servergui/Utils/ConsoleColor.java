package com.failprooftech.servergui.Utils;

/**
 * ANSI escape-code colors for internal console messages printed by ServerGUI.
 * <p>
 * Each constant's {@link #toString()} returns the raw ANSI escape sequence so
 * it can be concatenated directly into strings passed to
 * {@code ServerGUI.addToConsole()}.
 *
 * <pre>{@code
 *   addToConsole(ConsoleColor.RED + "Something went wrong." + ConsoleColor.RESET);
 * }</pre>
 */
public enum ConsoleColor {

    // Standard colors
    BLACK  ("\u001B[30m"),
    RED    ("\u001B[31m"),
    GREEN  ("\u001B[32m"),
    YELLOW ("\u001B[33m"),
    BLUE   ("\u001B[34m"),
    MAGENTA("\u001B[35m"),
    CYAN   ("\u001B[36m"),
    WHITE  ("\u001B[37m"),

    // Bright / high-intensity variants
    BRIGHT_BLACK  ("\u001B[90m"),
    BRIGHT_RED    ("\u001B[91m"),
    BRIGHT_GREEN  ("\u001B[92m"),
    BRIGHT_YELLOW ("\u001B[93m"),
    BRIGHT_BLUE   ("\u001B[94m"),
    BRIGHT_MAGENTA("\u001B[95m"),
    BRIGHT_CYAN   ("\u001B[96m"),
    BRIGHT_WHITE  ("\u001B[97m"),

    // Formatting
    BOLD     ("\u001B[1m"),
    ITALIC   ("\u001B[3m"),
    UNDERLINE("\u001B[4m"),
    STRIKETHROUGH("\u001B[9m"),

    /** Resets all attributes back to the terminal default. */
    RESET("\u001B[0m"),
    ;

    private final String code;

    ConsoleColor(String code) {
        this.code = code;
    }

    /** Returns the raw ANSI escape sequence. */
    @Override
    public String toString() {
        return code;
    }
}
