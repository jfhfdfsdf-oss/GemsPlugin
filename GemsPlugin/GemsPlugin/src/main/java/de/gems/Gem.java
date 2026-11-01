package de.gems;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

/**
 * Die 15 Gems. Jedes hat ein eigenes, zur Fähigkeit passendes Rezept.
 * shape: 3 Reihen getrennt durch '/', '.' = leerer Slot, Buchstaben A, B, C, ... = die Materialien der Reihe nach.
 *
 * "Stufen-Gems" (Stärke, Speed, Regeneration, Resistenz) folgen diesem Schema:
 *   Lv1: nur Rechtsklick  -> Effekt I für 20s  (CD 60s)
 *   Lv2: dauerhaft Effekt I
 *   Lv3: dauerhaft I + Rechtsklick -> Effekt II für 30s (CD 60s)
 *   Lv4: dauerhaft II (Regeneration bleibt bei I, Wächter ist eigens generft)
 *   Lv5: dauerhaft + Rechtsklick -> Effekt III für 15s (CD 90s)
 */
public enum Gem {
    STRENGTH("Stärke-Gem", Material.BLAZE_POWDER, NamedTextColor.RED, ".A./BCB/DDD",
            Material.DIAMOND_SWORD, Material.BLAZE_ROD, Material.POTION, Material.REDSTONE_BLOCK),
    SPEED("Speed-Gem", Material.SUGAR, NamedTextColor.AQUA, "AAA/BCB/.D.",
            Material.FEATHER, Material.SUGAR, Material.DIAMOND_BOOTS, Material.RABBIT_FOOT),
    FROST("Frost-Gem", Material.PRISMARINE_SHARD, NamedTextColor.BLUE, "B.B/ACA/B.B",
            Material.BLUE_ICE, Material.SNOW_BLOCK, Material.HEART_OF_THE_SEA),
    REGEN("Heil-Gem", Material.GLISTERING_MELON_SLICE, NamedTextColor.RED, "ABA/BCB/.D.",
            Material.GHAST_TEAR, Material.GLISTERING_MELON_SLICE, Material.GOLDEN_APPLE, Material.GOLDEN_CARROT),
    RESISTANCE("Wächter-Gem", Material.NETHERITE_SCRAP, NamedTextColor.DARK_GRAY, "ABA/BCB/ABA",
            Material.OBSIDIAN, Material.IRON_BLOCK, Material.SHIELD),
    MACE("Mace-Gem", Material.ECHO_SHARD, NamedTextColor.GRAY, "CAC/.B./.B.",
            Material.HEAVY_CORE, Material.BREEZE_ROD, Material.IRON_BLOCK),
    HEART("Herz-Gem", Material.GHAST_TEAR, NamedTextColor.LIGHT_PURPLE, "ABA/ACA/.A.",
            Material.GOLDEN_APPLE, Material.GHAST_TEAR, Material.GOLDEN_APPLE),
    FIRE("Feuer-Gem", Material.MAGMA_CREAM, NamedTextColor.GOLD, ".B./ACA/AAA",
            Material.MAGMA_BLOCK, Material.BLAZE_ROD, Material.LAVA_BUCKET),
    ECHO("Echo-Gem", Material.AMETHYST_SHARD, NamedTextColor.LIGHT_PURPLE, "A.A/BCB/A.A",
            Material.AMETHYST_SHARD, Material.STRING, Material.GLOWSTONE_DUST),
    VAMPIRE("Vampir-Gem", Material.FERMENTED_SPIDER_EYE, NamedTextColor.DARK_RED, "ADA/.C./B.B",
            Material.FERMENTED_SPIDER_EYE, Material.BONE_BLOCK, Material.WITHER_SKELETON_SKULL, Material.NETHER_WART_BLOCK),
    THUNDER("Donner-Gem", Material.COPPER_INGOT, NamedTextColor.YELLOW, "ABC/.D./CBA",
            Material.COPPER_BLOCK, Material.LIGHTNING_ROD, Material.DIAMOND_BLOCK, Material.COPPER_SWORD),
    ENDER("Ender-Gem", Material.POPPED_CHORUS_FRUIT, NamedTextColor.DARK_PURPLE, "ABA/ABA/ABA",
            Material.ENDER_PEARL, Material.ENDER_EYE),
    WIND("Wind-Gem", Material.PHANTOM_MEMBRANE, NamedTextColor.WHITE, "ABC/BAB/CBA",
            Material.WIND_CHARGE, Material.FEATHER, Material.BREEZE_ROD),
    HUNTER("Jäger-Gem", Material.COMPASS, NamedTextColor.DARK_GREEN, ".A./BCB/D.D",
            Material.SPYGLASS, Material.EMERALD_BLOCK, Material.COMPASS, Material.SPECTRAL_ARROW),
    PHOENIX("Phönix-Gem", Material.GLOWSTONE_DUST, NamedTextColor.GOLD, "ABA/CBC/ABA",
            Material.FIRE_CHARGE, Material.TOTEM_OF_UNDYING, Material.GLOWSTONE);

    public final String display;
    public final Material icon;
    public final NamedTextColor color;
    public final String shape;
    public final Material[] mats;

    Gem(String display, Material icon, NamedTextColor color, String shape, Material... mats) {
        this.display = display;
        this.icon = icon;
        this.color = color;
        this.shape = shape;
        this.mats = mats;
    }

    /** Die 3 Reihen des Rezepts im Bukkit-Format (Leerzeichen = leer). */
    public String[] rows() {
        return shape.replace('.', ' ').split("/");
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    // ------------------------------------------------------------ Stufen-Gems

    /** Potion-Effekt der Stufen-Gems, sonst null. */
    public PotionEffectType potion() {
        return switch (this) {
            case STRENGTH -> PotionEffectType.STRENGTH;
            case SPEED -> PotionEffectType.SPEED;
            case REGEN -> PotionEffectType.REGENERATION;
            case RESISTANCE -> PotionEffectType.RESISTANCE;
            default -> null;
        };
    }

    private String effectName() {
        return switch (this) {
            case STRENGTH -> "Stärke";
            case SPEED -> "Speed";
            case REGEN -> "Regeneration";
            case RESISTANCE -> "Resistenz";
            default -> "";
        };
    }

    /** Dauerhafte Verstärkung (0 = Stufe I), -1 = kein Dauer-Effekt. */
    public int passiveAmp(int lvl) {
        if (potion() == null) return -1;
        if (this == RESISTANCE) return lvl >= 4 ? 0 : -1;      // Wächter: erst ab Lv4, nur Stufe I
        if (lvl < 2) return -1;
        if (lvl >= 4) return this == REGEN ? 0 : 1;
        return 0;
    }

    /** Rechtsklick-Effekt: {Verstärkung, Sekunden, Cooldown-Sekunden} */
    public int[] activeTier(int lvl) {
        if (this == RESISTANCE) {                                // Wächter: kürzer, schwächer, längerer Cooldown
            return switch (lvl) {
                case 1 -> new int[]{0, 10, 90};
                case 2 -> new int[]{0, 15, 90};
                case 3, 4 -> new int[]{1, 10, 90};
                default -> new int[]{1, 15, 120};
            };
        }
        if (lvl >= 5) return new int[]{2, 15, 90};
        if (lvl >= 3) return new int[]{1, 30, 60};
        return new int[]{0, 20, 60};
    }

    private String tierText(int lvl) {
        String n = effectName();
        int pa = passiveAmp(lvl);
        int[] act = activeTier(lvl);
        StringBuilder sb = new StringBuilder();
        if (pa >= 0) sb.append("Dauerhaft ").append(n).append(" ").append(roman(pa + 1));
        if (act[0] > pa) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("Rechtsklick: ").append(n).append(" ").append(roman(act[0] + 1))
                    .append(" für ").append(act[1]).append("s (CD ").append(act[2]).append("s)");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ Mace

    public static int maceBreach(int lvl) { return Math.max(0, lvl - 1); }          // Lv5 = IV
    public static int maceDensity(int lvl) { return lvl >= 5 ? 5 : Math.max(0, lvl - 1); }
    public static int maceWindBurst(int lvl) { return Math.max(0, lvl - 2); }       // ab Lv3

    // ------------------------------------------------------------ Jäger

    public static int hunterRange(int lvl) {
        return switch (lvl) {
            case 1 -> 40;
            case 2 -> 60;
            case 3 -> 80;
            case 4 -> 110;
            default -> 150;
        };
    }

    // ------------------------------------------------------------ Frost

    public static int frostAmp(int lvl) { return lvl >= 3 ? 1 : 0; }

    /** Abklingzeit des Treffer-Effekts pro Spieler: Lv1 = 10s ... Lv5 = 6s */
    public static int frostProcCooldown(int lvl) { return 11 - lvl; }

    // ------------------------------------------------------------ sonstige Werte

    /** Zusätzliche Herzen: Lv1-5 = 1,1,2,2,3 */
    public static int hearts(int lvl) {
        return switch (lvl) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            default -> 3;
        };
    }

    /** Brenndauer (Sekunden) bei Treffern des Feuer-Gems */
    public static int fireBurn(int lvl) { return lvl >= 2 ? lvl + 1 : 0; }

    public int cooldownSeconds(int lvl) {
        return switch (this) {
            case ENDER, WIND -> Math.max(4, 16 - 2 * lvl);
            case FIRE -> Math.max(4, 12 - 3 * (lvl - 3));
            case FROST -> lvl >= 5 ? 24 : 30;
            case HUNTER -> 35 - 4 * lvl;
            case PHOENIX -> switch (lvl) {
                case 1, 2 -> 900;
                case 3 -> 600;
                case 4 -> 420;
                default -> 300;
            };
            case ECHO -> switch (lvl) {
                case 1 -> 3600; // 60 Minuten
                case 2 -> 3120; // 52 Minuten
                case 3 -> 2700; // 45 Minuten
                case 4 -> 2220; // 37 Minuten
                default -> 1800; // 30 Minuten
            };
            default -> 60;
        };
    }

    /** Beschreibung dessen, was das Gem auf dem jeweiligen Level kann (kumulativ). */
    public String ability(int lvl) {
        return switch (this) {
            case STRENGTH, SPEED, REGEN, RESISTANCE -> tierText(lvl);
            case MACE -> lvl <= 1
                    ? "Du darfst die Mace benutzen"
                    : "Erlaubt: Breach " + roman(maceBreach(lvl)) + ", Dichte " + roman(maceDensity(lvl))
                    + (maceWindBurst(lvl) > 0 ? ", Windstoß " + roman(maceWindBurst(lvl)) : "");
            case HEART -> "+" + hearts(lvl) + " Herzen Maximalleben";
            case FIRE -> {
                StringBuilder sb = new StringBuilder("Feuerresistenz");
                if (lvl >= 2) sb.append(" + Treffer: Gegner brennen ").append(fireBurn(lvl)).append("s");
                if (lvl >= 3) sb.append(" + Rechtsklick: Feuerball (CD ").append(cooldownSeconds(lvl)).append("s)");
                yield sb.toString();
            }
            case ECHO -> "Echo setzen und Position tauschen (CD " + (cooldownSeconds(lvl) / 60)
                    + " Min); Schleichen + Rechtsklick entfernt das Echo";
            case FROST -> {
                StringBuilder sb = new StringBuilder("Treffer verlangsamen (Slowness " + roman(frostAmp(lvl) + 1) + ", "
                        + (2 + lvl) + "s, alle " + frostProcCooldown(lvl) + "s)");
                if (lvl >= 4) sb.append(" + Rechtsklick: Frost-Nova, Radius ").append(lvl >= 5 ? 6 : 4)
                        .append(" (CD ").append(cooldownSeconds(lvl)).append("s)");
                yield sb.toString();
            }
            case VAMPIRE -> "Lebensraub: " + (4 * lvl) + "% des Schadens";
            case THUNDER -> "Blitz-Chance " + (4 * lvl) + "% pro Treffer, Blitz-immun";
            case ENDER -> "Rechtsklick: Teleport " + (5 + 3 * lvl) + " Blöcke (CD " + cooldownSeconds(lvl) + "s)";
            case WIND -> {
                StringBuilder sb = new StringBuilder("Kein Fallschaden");
                if (lvl >= 2) sb.append(" + Rechtsklick: Windstoß nach oben (CD ").append(cooldownSeconds(lvl)).append("s)");
                if (lvl >= 5) sb.append(" + schleudert Gegner weg");
                yield sb.toString();
            }
            case HUNTER -> {
                String t = "Rechtsklick: nächsten Spieler orten (" + hunterRange(lvl) + " Blöcke, CD " + cooldownSeconds(lvl) + "s)";
                if (lvl >= 2) t += " + Treffer lässt Gegner leuchten (" + (2 + lvl) + "s, alle 15s)";
                yield t;
            }
            case PHOENIX -> "Tödlicher Schaden: überlebe mit " + (1 + lvl) + " Herzen (CD "
                    + (cooldownSeconds(lvl) / 60) + " Min)";
        };
    }
}
