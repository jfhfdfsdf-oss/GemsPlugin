package de.gems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public class GemsPlugin extends JavaPlugin implements TabExecutor {

    public static final int MAX_LEVEL = 5;
    /** XP für Level-Up: XP_BASE * level^2  (Lv1->2: 50, 2->3: 200, 3->4: 450, 4->5: 800) */
    public static final int XP_BASE = 50;

    public static class GemData {
        public boolean crafted;
        public int level = 1;
        public int xp;
        public String owner = "";
    }

    private final Map<Gem, GemData> data = new EnumMap<>(Gem.class);
    private NamespacedKey gemKey;
    private NamespacedKey heartKey;
    private File dataFile;
    private GemListener gemListener;

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        gemKey = new NamespacedKey(this, "gem");
        heartKey = new NamespacedKey(this, "heart_bonus");
        dataFile = new File(getDataFolder(), "data.yml");
        for (Gem g : Gem.values()) data.put(g, new GemData());
        loadData();
        registerRecipes();

        gemListener = new GemListener(this);
        getServer().getPluginManager().registerEvents(gemListener, this);
        PluginCommand cmd = getCommand("gems");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getServer().getScheduler().runTaskTimer(this, this::tick, 40L, 40L);
        getServer().getScheduler().runTaskTimer(this, this::saveData, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (gemListener != null) gemListener.clearEchoes();
        saveData();
        for (Gem g : Gem.values()) Bukkit.removeRecipe(recipeKey(g));
    }

    // ------------------------------------------------------------------ data

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (Gem g : Gem.values()) {
            String p = "gems." + g.name() + ".";
            GemData d = data.get(g);
            d.crafted = cfg.getBoolean(p + "crafted", false);
            d.level = Math.max(1, Math.min(MAX_LEVEL, cfg.getInt(p + "level", 1)));
            d.xp = cfg.getInt(p + "xp", 0);
            d.owner = cfg.getString(p + "owner", "");
        }
    }

    public void saveData() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Gem g : Gem.values()) {
            String p = "gems." + g.name() + ".";
            GemData d = data.get(g);
            cfg.set(p + "crafted", d.crafted);
            cfg.set(p + "level", d.level);
            cfg.set(p + "xp", d.xp);
            cfg.set(p + "owner", d.owner);
        }
        try {
            getDataFolder().mkdirs();
            cfg.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Konnte data.yml nicht speichern: " + e.getMessage());
        }
    }

    public GemData data(Gem g) {
        return data.get(g);
    }

    public boolean isCrafted(Gem g) {
        return data.get(g).crafted;
    }

    public void markCrafted(Gem g, Player p) {
        GemData d = data.get(g);
        d.crafted = true;
        d.owner = p.getName();
        saveData();
        Bukkit.broadcast(Component.text("✦ " + p.getName() + " hat das ", NamedTextColor.GRAY)
                .append(Component.text(g.display, g.color, TextDecoration.BOLD))
                .append(Component.text(" gecraftet! Es kann nie wieder hergestellt werden.", NamedTextColor.GRAY)));
    }

    // ------------------------------------------------------------------ items

    public NamespacedKey recipeKey(Gem g) {
        return new NamespacedKey(this, "gem_" + g.name().toLowerCase());
    }

    public static int xpNeeded(int level) {
        return XP_BASE * level * level;
    }

    public ItemStack createItem(Gem g) {
        GemData d = data.get(g);
        ItemStack it = new ItemStack(g.icon);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("✦ " + g.display, g.color, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(line("Level " + d.level + "/" + MAX_LEVEL, NamedTextColor.GOLD));
        if (d.level < MAX_LEVEL) {
            int need = xpNeeded(d.level);
            int filled = Math.min(10, d.xp * 10 / need);
            lore.add(line("█".repeat(filled) + "░".repeat(10 - filled) + " " + d.xp + "/" + need + " XP", NamedTextColor.GRAY));
        } else {
            lore.add(line("MAX LEVEL", NamedTextColor.GREEN));
        }
        lore.add(Component.empty());
        for (int l = 1; l <= MAX_LEVEL; l++) {
            boolean unlocked = l <= d.level;
            lore.add(line((unlocked ? "✔ " : "✘ ") + "Lv" + l + ": " + g.ability(l),
                    unlocked ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        lore.add(line("Einzigartig – nur 1x craftbar", NamedTextColor.DARK_PURPLE));
        lore.add(line("Wirkt, solange es im Inventar liegt", NamedTextColor.DARK_GRAY));
        m.lore(lore);
        m.setEnchantmentGlintOverride(true);
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(gemKey, PersistentDataType.STRING, g.name());
        it.setItemMeta(m);
        return it;
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    public Gem gemOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(gemKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return Gem.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Set<Gem> heldGems(Player p) {
        Set<Gem> set = EnumSet.noneOf(Gem.class);
        for (ItemStack it : p.getInventory().getContents()) {
            Gem g = gemOf(it);
            if (g != null) set.add(g);
        }
        return set;
    }

    /** Level des Gems, wenn der Spieler es im Inventar hat – sonst 0. */
    public int levelOf(Player p, Gem g) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (gemOf(it) == g) return data.get(g).level;
        }
        return 0;
    }

    /** Ersetzt die Gem-Items im Inventar durch frisch generierte (aktuelle Lore). */
    public void refresh(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            Gem g = gemOf(contents[i]);
            if (g != null) inv.setItem(i, createItem(g));
        }
    }

    private void registerRecipes() {
        for (Gem g : Gem.values()) {
            NamespacedKey key = recipeKey(g);
            Bukkit.removeRecipe(key);
            ShapedRecipe r = new ShapedRecipe(key, createItem(g));
            r.shape(g.rows());
            for (int i = 0; i < g.mats.length; i++) r.setIngredient((char) ('A' + i), g.mats[i]);
            Bukkit.addRecipe(r);
        }
    }

    // ------------------------------------------------------------------ XP

    public void addXp(Player p, Gem g, int amount) {
        GemData d = data.get(g);
        if (d.level >= MAX_LEVEL) return;
        d.xp += amount;
        boolean up = false;
        while (d.level < MAX_LEVEL && d.xp >= xpNeeded(d.level)) {
            d.xp -= xpNeeded(d.level);
            d.level++;
            up = true;
        }
        if (d.level >= MAX_LEVEL) d.xp = 0;
        if (up) {
            p.showTitle(Title.title(
                    Component.text(g.display, g.color, TextDecoration.BOLD),
                    Component.text("Level " + d.level + " erreicht!  " + g.ability(d.level), NamedTextColor.GOLD),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            saveData();
        }
        refresh(p);
    }

    // ------------------------------------------------------------------ passive effects

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<Gem> held = heldGems(p);
            for (Gem g : held) applyEffects(p, g, data.get(g).level);
            updateHearts(p, held.contains(Gem.HEART) ? Gem.hearts(data.get(Gem.HEART).level) * 2.0 : 0.0);
        }
    }

    private void eff(Player p, PotionEffectType t, int amp) {
        int dur = t == PotionEffectType.NIGHT_VISION ? 260 : 100;
        p.addPotionEffect(new PotionEffect(t, dur, amp, true, false, true));
    }

    private void applyEffects(Player p, Gem g, int lvl) {
        // Stufen-Gems: dauerhafter Effekt ab Lv2 (Rechtsklick-Effekte regelt der Listener)
        if (g.potion() != null) {
            int pa = g.passiveAmp(lvl);
            if (pa >= 0) eff(p, g.potion(), pa);
            return;
        }
        switch (g) {
            case FIRE -> eff(p, PotionEffectType.FIRE_RESISTANCE, 0);
            default -> { }
        }
    }

    private void updateHearts(Player p, double bonus) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        AttributeModifier old = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (m.getKey().equals(heartKey)) old = m;
        }
        if (old != null && old.getAmount() == bonus) return;
        if (old != null) inst.removeModifier(old);
        if (bonus > 0) inst.addModifier(new AttributeModifier(heartKey, bonus, AttributeModifier.Operation.ADD_NUMBER));
        if (p.getHealth() > inst.getValue()) p.setHealth(inst.getValue());
    }

    // ------------------------------------------------------------------ commands

    private Gem parseGem(String s) {
        try {
            return Gem.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(Component.text("=== Gems ===", NamedTextColor.GOLD, TextDecoration.BOLD));
            for (Gem g : Gem.values()) {
                GemData d = data.get(g);
                Component status = d.crafted
                        ? Component.text(" – " + d.owner + " (Lv " + d.level + ")", NamedTextColor.GRAY)
                        : Component.text(" – noch nicht gecraftet", NamedTextColor.GREEN);
                sender.sendMessage(Component.text("• " + g.name().toLowerCase() + ": ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(g.display, g.color)).append(status));
            }
            sender.sendMessage(Component.text("/gems recipe <gem> zeigt das Rezept.", NamedTextColor.DARK_GRAY));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("recipe")) {
            Gem g = args.length > 1 ? parseGem(args[1]) : null;
            if (g == null) {
                sender.sendMessage(Component.text("Nutzung: /gems recipe <gem>", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("Rezept " + g.display + ":", g.color));
            String[] shapeRows = g.shape.split("/");
            for (int i = 0; i < shapeRows.length; i++) {
                StringBuilder line = new StringBuilder("Reihe " + (i + 1) + ":");
                for (char c : shapeRows[i].toCharArray()) {
                    line.append("  [").append(c == '.' ? "leer" : g.mats[c - 'A'].name().toLowerCase().replace('_', ' ')).append("]");
                }
                sender.sendMessage(Component.text(line.toString(), NamedTextColor.GRAY));
            }
            return true;
        }

        if (!sender.hasPermission("gems.admin")) {
            sender.sendMessage(Component.text("Keine Rechte.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Nutzung: /gems <give|reset|setlevel> <gem> ...", NamedTextColor.RED));
            return true;
        }
        Gem g = parseGem(args[1]);
        if (g == null) {
            sender.sendMessage(Component.text("Unbekanntes Gem.", NamedTextColor.RED));
            return true;
        }
        GemData d = data.get(g);
        switch (sub) {
            case "give" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2])
                        : (sender instanceof Player pl ? pl : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Spieler nicht gefunden.", NamedTextColor.RED));
                    return true;
                }
                if (!d.crafted) {
                    d.crafted = true;
                    d.owner = target.getName();
                    saveData();
                }
                target.getInventory().addItem(createItem(g)).values()
                        .forEach(rest -> target.getWorld().dropItem(target.getLocation(), rest));
                sender.sendMessage(Component.text(g.display + " an " + target.getName() + " gegeben.", NamedTextColor.GREEN));
            }
            case "reset" -> {
                d.crafted = false;
                d.level = 1;
                d.xp = 0;
                d.owner = "";
                saveData();
                sender.sendMessage(Component.text(g.display + " zurückgesetzt (wieder craftbar).", NamedTextColor.GREEN));
            }
            case "setlevel" -> {
                int lvl;
                try {
                    lvl = Integer.parseInt(args[2]);
                } catch (Exception e) {
                    sender.sendMessage(Component.text("Nutzung: /gems setlevel <gem> <1-5>", NamedTextColor.RED));
                    return true;
                }
                d.level = Math.max(1, Math.min(MAX_LEVEL, lvl));
                d.xp = 0;
                saveData();
                for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
                sender.sendMessage(Component.text(g.display + " ist jetzt Level " + d.level + ".", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unbekannter Unterbefehl.", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("info", "recipe"));
            if (sender.hasPermission("gems.admin")) out.addAll(List.of("give", "reset", "setlevel"));
        } else if (args.length == 2) {
            for (Gem g : Gem.values()) out.add(g.name().toLowerCase());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setlevel")) {
            out.addAll(List.of("1", "2", "3", "4", "5"));
        }
        String last = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(last));
        return out;
    }
}
