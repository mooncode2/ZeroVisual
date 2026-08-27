package ru.zero.ui.gui.theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import ru.zero.util.render.core.Renderer2D;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public final class MinecraftTheme {
    private MinecraftTheme() {
    }

    public static final int GOLD = 0xFFAA00;
    public static final int GOLD_DARK = 0x8B6B1F;
    public static final int GREEN = 0x35AA35;
    public static final int GREEN_DARK = 0x1E5E1E;
    public static final int PANEL = 0x161616;
    public static final int PANEL_LIGHT = 0x232323;
    public static final int OUTLINE = 0x000000;
    public static final int BORDER = 0x555555;
    public static final int DISABLED = 0x808080;
    public static final int TEXT = 0xE0E0E0;
    public static final int WHITE = 0xFFFFFF;

    public enum IconType {
        EYE,
        PEN,
        SEARCH,
        GEAR,
        MAP,
        CROWN,
        ARROW_DOWN
    }

    public static int getTextureId(Identifier id) {
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        AbstractTexture tex = tm.getTexture(id);
        if (tex == null) {
            return 0;
        }

        if (!(tex.getGlTexture() instanceof GlTexture glTexture)) {
            return 0;
        }

        return glTexture.getGlId();
    }

    public static void drawAvatar(Renderer2D r2, float x, float y, float size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }

        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (entry == null) {
            return;
        }

        Identifier skin = entry.getSkinTextures().body().id();
        int id = getTextureId(skin);
        if (id <= 0) {
            return;
        }

        float u0 = 8.0F / 64.0F;
        float v0 = 8.0F / 64.0F;
        float u1 = 16.0F / 64.0F;
        float v1 = 16.0F / 64.0F;
        r2.drawRgbaTextureWithUV(id, x, y, size, size, u0, v0, u1, v1);
    }

    public static void drawPanel(Renderer2D r2, float x, float y, float w, float h, float radius, int fill, int outline) {
        r2.rect(x, y, w, h, radius, fill);
        r2.rectOutline(x, y, w, h, radius, outline, 1.0F);
        r2.rectOutline(x + 1.0F, y + 1.0F, w - 2.0F, h - 2.0F, Math.max(0.0F, radius - 1.0F), BORDER, 0.6F);
        r2.rect(x + 1.0F, y + 1.0F, w - 2.0F, 1.0F, 0.0F, Renderer2D.ColorUtil.replAlpha(WHITE, 18));
    }

    public static void drawScrollbar(Renderer2D r2, float x, float y, float w, float h, float thumbY, float thumbH) {
        r2.rect(x, y, w, h, 0.0F, Renderer2D.ColorUtil.replAlpha(OUTLINE, 120));
        r2.rect(x, thumbY, w, thumbH, 0.0F, Renderer2D.ColorUtil.replAlpha(BORDER, 200));
        r2.rect(x, thumbY, w, 1.0F, 0.0F, Renderer2D.ColorUtil.replAlpha(WHITE, 30));
    }

    private static void px(Renderer2D r2, float x, float y, float s, int color) {
        r2.rect(x, y, s, s, 0.0F, color);
    }

    public static void drawIcon(Renderer2D r2, IconType type, float x, float y, float size, int color) {
        float px = size / 9.0F;
        String[] art = ART.get(type);
        if (art == null) {
            return;
        }

        for (int row = 0; row < art.length; row++) {
            String line = art[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == '#') {
                    px(r2, x + col * px, y + row * px, px + 0.5F, color);
                }
            }
        }
    }

    private static final java.util.Map<IconType, String[]> ART = new java.util.EnumMap<>(IconType.class);

    static {
        ART.put(IconType.EYE, new String[] {
            ".........",
            "..#####..",
            ".#######.",
            "##.###.##",
            "##.###.##",
            ".#######.",
            "..#####..",
            ".........",
            "........."
        });
        ART.put(IconType.PEN, new String[] {
            "......#..",
            ".....##..",
            "....#.#..",
            "...#..#..",
            "..#...#..",
            ".#....#..",
            "#.....#..",
            "#....#...",
            ".####...."
        });
        ART.put(IconType.SEARCH, new String[] {
            ".....##..",
            "....#..#.",
            "...#...#.",
            "..#....#.",
            "...#...#.",
            "....#..#.",
            ".....##..",
            "......#..",
            ".....#..."
        });
        ART.put(IconType.GEAR, new String[] {
            "..#...#..",
            ".#######.",
            "##.###.##",
            ".#######.",
            "##.###.##",
            ".#######.",
            "..#...#..",
            ".........",
            "........."
        });
        ART.put(IconType.MAP, new String[] {
            ".######..",
            ".#....#.#",
            ".#....#.#",
            ".#....#.#",
            ".#....#.#",
            ".######.#",
            "..####...",
            ".........",
            "........."
        });
        ART.put(IconType.CROWN, new String[] {
            "#.#.#.#.#",
            "#########",
            ".#######.",
            "..#####..",
            "...###...",
            "....#....",
            ".........",
            ".........",
            "........."
        });
        ART.put(IconType.ARROW_DOWN, new String[] {
            ".........",
            "....#....",
            "...###...",
            "..#####..",
            ".#######.",
            "........#",
            ".......#.",
            "......#..",
            ".....#..."
        });
    }
}
