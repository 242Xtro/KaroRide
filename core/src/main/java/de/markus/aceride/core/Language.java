package de.markus.aceride.core;

import java.util.Locale;

/** App choice only; never changes the Karoo/system/JVM locale. */
public enum Language {
    ENGLISH("en", Locale.ENGLISH), GERMAN("de", Locale.GERMAN);
    private final String code;
    private final Locale locale;
    Language(String code, Locale locale) { this.code = code; this.locale = locale; }
    public String code() { return code; }
    public Locale locale() { return locale; }
    public static Language fromCode(String code) {
        return "de".equalsIgnoreCase(code) ? GERMAN : ENGLISH;
    }
}
