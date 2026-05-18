package com.promptslinger.burp;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Theme {

    public final String name;
    public final Color  BG, SURFACE, ENTRY_BG, FG, ACCENT,
                        GREEN, RED, MUTED, ORANGE, PINK;

    public Theme(String name,
                 Color bg, Color surface, Color entryBg, Color fg,
                 Color accent, Color green, Color red, Color muted,
                 Color orange, Color pink) {
        this.name     = name;
        this.BG       = bg;
        this.SURFACE  = surface;
        this.ENTRY_BG = entryBg;
        this.FG       = fg;
        this.ACCENT   = accent;
        this.GREEN    = green;
        this.RED      = red;
        this.MUTED    = muted;
        this.ORANGE   = orange;
        this.PINK     = pink;
    }

    // â”€â”€ Built-in themes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static final Theme DRACULA = new Theme("Dracula",
        new Color(0x1e1e2e), new Color(0x2a2a3d), new Color(0x313244),
        new Color(0xf8f8f2), new Color(0xbd93f9),
        new Color(0x50fa7b), new Color(0xff5555), new Color(0x6272a4),
        new Color(0xffb86c), new Color(0xff79c6));

    public static final Theme BURP = new Theme("Burp Classic",
        new Color(0x2b2b2b), new Color(0x3c3f41), new Color(0x45484a),
        new Color(0xbbbbbb), new Color(0xff6633),
        new Color(0x6a8759), new Color(0xcc4b4b), new Color(0x777777),
        new Color(0xff8c00), new Color(0xcc7832));

    public static final Theme MATRIX = new Theme("Matrix",
        new Color(0x0d0d0d), new Color(0x0a1a0a), new Color(0x0f1f0f),
        new Color(0x00ff41), new Color(0x00cc33),
        new Color(0x39ff14), new Color(0xff2222), new Color(0x005500),
        new Color(0x00ff99), new Color(0x00e5ff));

    public static final Theme NORD = new Theme("Nord",
        new Color(0x2e3440), new Color(0x3b4252), new Color(0x434c5e),
        new Color(0xeceff4), new Color(0x88c0d0),
        new Color(0xa3be8c), new Color(0xbf616a), new Color(0x616e88),
        new Color(0xd08770), new Color(0xb48ead));

    public static final Theme SOFT_LIGHT = new Theme("Soft Light",
        new Color(0xfafafa), new Color(0xf0f0f0), new Color(0xffffff),
        new Color(0x383a42), new Color(0x4078f2),
        new Color(0x50a14f), new Color(0xe45649), new Color(0xa0a1a7),
        new Color(0xc18401), new Color(0xa626a4));

    // #e41c38 red Â· #c18641 gold Â· #909090 muted gray â€” neutral black base
    public static final Theme HEXXED = new Theme("Hexxed BitHeadz",
        new Color(0x111111),  // BG       â€” near black
        new Color(0x1e1e1e),  // SURFACE  â€” dark charcoal
        new Color(0x2a2a2a),  // ENTRY_BG â€” lighter charcoal
        new Color(0xf0f0f0),  // FG       â€” clean white text
        new Color(0xe41c38),  // ACCENT   â€” signature red
        new Color(0xc18641),  // GREEN    â€” signature gold (no green in logo)
        new Color(0xe41c38),  // RED      â€” signature red
        new Color(0x909090),  // MUTED    â€” signature gray
        new Color(0xc18641),  // ORANGE   â€” signature gold
        new Color(0xe8607a)); // PINK     â€” softened red

    // black Â· neon cyan #00e5ff Â· hot magenta #ff007f Â· purple undertones
    public static final Theme GIRLS_HACK = new Theme("Girls Hack Better",
        new Color(0x000000),  // BG       â€” pure black
        new Color(0x0a0a12),  // SURFACE  â€” dark purple-black
        new Color(0x12121e),  // ENTRY_BG â€” slightly lighter
        new Color(0xf0f0f0),  // FG       â€” clean white
        new Color(0xff007f),  // ACCENT   â€” hot magenta
        new Color(0x00e5ff),  // GREEN    â€” neon cyan (the dominant second color)
        new Color(0xff007f),  // RED      â€” hot magenta
        new Color(0x6644aa),  // MUTED    â€” muted purple
        new Color(0x00bcd4),  // ORANGE   â€” deeper cyan
        new Color(0xff69b4)); // PINK     â€” soft pink

    public static final Theme[] ALL = {DRACULA, BURP, MATRIX, NORD, SOFT_LIGHT, HEXXED, GIRLS_HACK};

    // â”€â”€ Persistence â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static final Path THEME_FILE =
        Paths.get(System.getProperty("user.home"), ".promptslinger", "theme.txt");

    public static Theme loadSaved() {
        try {
            if (Files.exists(THEME_FILE)) {
                String saved = Files.readString(THEME_FILE).trim();
                for (Theme t : ALL)
                    if (t.name.equals(saved)) return t;
            }
        } catch (Exception ignored) {}
        return DRACULA;
    }

    public static void save(Theme t) {
        try {
            Files.createDirectories(THEME_FILE.getParent());
            Files.writeString(THEME_FILE, t.name);
        } catch (Exception e) {
            System.err.println("[PromptSlinger] Failed to save theme preference: " + e.getMessage());
        }
    }
}
