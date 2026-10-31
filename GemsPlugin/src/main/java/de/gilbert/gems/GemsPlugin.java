package de.gilbert.gems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gems utility/adventure plugin for Paper 26.3.
 * This build intentionally contains no weapon unlocks or combat-boosting abilities.
 */
public final class GemsPlugin extends org.bukkit.plugin.java.JavaPlugin
        implements Listener, CommandExecutor, TabCompleter {

    private NamespacedKey gemKey;
    private NamespacedKey instanceKey;
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, Map<PotionEffectType, Integer>> managedEffects = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> phoenixCooldowns = new HashMap<>();
    private final Map<UUID, Double> baseHealth = new HashMap<>();

    private enum Gem {
        SPEED("speed", "Speed-Gem", "Bewegungsgeschwindigkeit", Material.AMETHYST_SHARD,
                new String[]{"FFF", "SDS", " H "}, Map.of('F', Material.FEATHER, 'S', Material.SUGAR, 'D', Material.DIAMOND_BOOTS, 'H', Material.RABBIT_FOOT)),
        HEAL("heal", "Heil-Gem", "Regeneration", Material.AMETHYST_SHARD,
                new String[]{"GTG", "MBM", " K "}, Map.of('G', Material.GHAST_TEAR, 'T', Material.GLISTERING_MELON_SLICE, 'M', Material.GLISTERING_MELON_SLICE, 'B', Material.BEACON, 'K', Material.GOLDEN_CARROT)),
        GUARDIAN("guardian", "Wächter-Gem", "Schutz durch Resistenz", Material.AMETHYST_SHARD,
                new String[]{"OIO", "ISI", "ONS"}, Map.of('O', Material.OBSIDIAN, 'I', Material.IRON_BLOCK, 'S', Material.DIAMOND, 'N', Material.NETHERITE_SCRAP)),
        HEART("heart", "Herz-Gem", "Zusätzliche maximale Herzen", Material.AMETHYST_SHARD,
                new String[]{"GTG", "GEG", " G "}, Map.of('G', Material.GOLDEN_APPLE, 'T', Material.GHAST_TEAR, 'E', Material.ENCHANTED_GOLDEN_APPLE)),
        FIRE("fire", "Feuer-Gem", "Feuerresistenz", Material.AMETHYST_SHARD,
                new String[]{" B ", "MLM", "MMM"}, Map.of('B', Material.BLAZE_ROD, 'M', Material.MAGMA_BLOCK, 'L', Material.LAVA_BUCKET)),
        ENDER("ender", "Ender-Gem", "Sicherer Kurzstrecken-Teleport", Material.AMETHYST_SHARD,
                new String[]{"PEP", "CAC", " P "}, Map.of('P', Material.ENDER_PEARL, 'E', Material.ENDER_EYE, 'C', Material.CHORUS_FRUIT, 'A', Material.ELYTRA)),
        WIND("wind", "Wind-Gem", "Kein Fallschaden und Windstoß nach oben", Material.AMETHYST_SHARD,
                new String[]{"WFB", "FNF", "BFW"}, Map.of('W', Material.PHANTOM_MEMBRANE, 'F', Material.FEATHER, 'B', Material.BREEZE_ROD, 'N', Material.NETHER_STAR)),
        PHOENIX("phoenix", "Phönix-Gem", "Einmalige Rettung vor tödlichem Schaden", Material.AMETHYST_SHARD,
                new String[]{"F F", "GTG", "FNF"}, Map.of('F', Material.AMETHYST_SHARD, 'G', Material.GLOWSTONE, 'T', Material.TOTEM_OF_UNDYING, 'N', Material.NETHER_STAR));

        final String id;
        final String display;
        final String description;
        final Material itemMaterial;
        final String[] shape;
        final Map<Character, Material> ingredients;

        Gem(String id, String display, String description, Material itemMaterial,
            String[] shape, Map<Character, Material> ingredients) {
            this.id = id;
            this.display = display;
            this.description = description;
            this.itemMaterial = itemMaterial;
            this.shape = shape;
            this.ingredients = ingredients;
        }

        static Gem find(String value) {
            if (value == null) return null;
            String normalized = value.toLowerCase(Locale.ROOT).replace("-gem", "").replace("_", "");
            for (Gem gem : values()) {
                String displayId = gem.display.toLowerCase(Locale.ROOT).replace("-gem", "").replace("ä", "a");
                if (gem.id.equalsIgnoreCase(normalized) || displayId.equals(normalized)) return gem;
            }
            return null;
        }
    }

    @Override
    public void onEnable() {
        gemKey = new NamespacedKey(this, "gem_type");
        instanceKey = new NamespacedKey(this, "gem_instance");
        dataFile = new File(getDataFolder(), "data.yml");
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create plugin data folder.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("gems") != null) {
            getCommand("gems").setExecutor(this);
            getCommand("gems").setTabCompleter(this);
        }
        registerRecipes();
        for (Player player : Bukkit.getOnlinePlayers()) refreshPlayerItems(player);
        Bukkit.getScheduler().runTaskTimer(this, this::tickPlayers, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, this::awardOnlineXp, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveData, 6000L, 6000L);
        getLogger().info("Gems enabled. Registered " + Gem.values().length + " utility Gems.");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreManagedEffects(player);
            restoreHealth(player);
        }
        saveData();
    }

    private void registerRecipes() {
        for (Gem gem : Gem.values()) {
            NamespacedKey key = new NamespacedKey(this, "recipe_" + gem.id);
            Bukkit.removeRecipe(key);
            ShapedRecipe recipe = new ShapedRecipe(key, createGemItem(gem));
            recipe.shape(gem.shape);
            for (Map.Entry<Character, Material> entry : gem.ingredients.entrySet()) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }
            Bukkit.addRecipe(recipe);
        }
    }

    private ItemStack createGemItem(Gem gem) {
        ItemStack item = new ItemStack(gem.itemMaterial, 1);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(gemKey, PersistentDataType.STRING, gem.id);
        pdc.set(instanceKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.setDisplayName("§d✦ " + gem.display + " §d✦");
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.setLore(List.of("§7" + gem.description, "§7Level: §e1§7/§e5", "§7XP: §b0§7/§b50", "§8Einzigartiges Gem-Item"));
        item.setItemMeta(meta);
        return item;
    }

    private Gem getGem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(gemKey, PersistentDataType.STRING);
        if (id == null) return null;
        for (Gem gem : Gem.values()) if (gem.id.equals(id)) return gem;
        return null;
    }

    private String craftedPath(Gem gem) { return "crafted." + gem.id; }
    private String ownerPath(Gem gem) { return "owners." + gem.id; }
    private String playerPath(UUID player, Gem gem) { return "players." + player + "." + gem.id; }
    private int level(UUID player, Gem gem) { return Math.max(1, Math.min(5, data.getInt(playerPath(player, gem) + ".level", 1))); }
    private int xp(UUID player, Gem gem) { return Math.max(0, data.getInt(playerPath(player, gem) + ".xp", 0)); }

    private int nextXp(int level) {
        return switch (level) { case 1 -> 50; case 2 -> 200; case 3 -> 450; case 4 -> 800; default -> 0; };
    }

    private void addGemXp(Player player, Gem gem, int amount) {
        String path = playerPath(player.getUniqueId(), gem);
        int currentLevel = level(player.getUniqueId(), gem);
        int currentXp = xp(player.getUniqueId(), gem) + amount;
        boolean leveled = false;
        while (currentLevel < 5 && currentXp >= nextXp(currentLevel)) {
            currentXp -= nextXp(currentLevel);
            currentLevel++;
            leveled = true;
        }
        data.set(path + ".level", currentLevel);
        data.set(path + ".xp", currentXp);
        if (leveled) {
            player.sendTitle("§d§lGEM LEVEL UP!", "§f" + gem.display + " §7ist jetzt Level §e" + currentLevel, 10, 60, 15);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            saveData();
        }
        refreshPlayerItems(player);
    }

    /** Non-combat progression: each carried Gem earns 5 XP per minute online. */
    private void awardOnlineXp() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Gem gem : Gem.values()) {
                if (hasGem(player, gem)) addGemXp(player, gem, 5);
            }
        }
    }

    private void refreshPlayerItems(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) refreshItem(inv.getItem(slot), player);
        for (ItemStack armor : inv.getArmorContents()) refreshItem(armor, player);
        refreshItem(inv.getItemInOffHand(), player);
    }

    private void refreshItem(ItemStack item, Player player) {
        Gem gem = getGem(item);
        if (gem == null || !item.hasItemMeta()) return;
        int lv = level(player.getUniqueId(), gem);
        int currentXp = xp(player.getUniqueId(), gem);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("§7" + gem.description);
        lore.add("§7Level: §e" + lv + "§7/§e5");
        lore.add("§7XP: §b" + currentXp + (lv < 5 ? "§7/§b" + nextXp(lv) : "§7/§bMAX"));
        lore.add("§8Fähigkeiten:");
        lore.addAll(abilityLore(gem));
        lore.add("§8Einzigartiges Gem-Item");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private List<String> abilityLore(Gem gem) {
        return switch (gem) {
            case SPEED -> List.of("§71: Aktiv Geschwindigkeit I", "§72: Permanent Geschwindigkeit I", "§73: Permanent I + aktiv II", "§74: Permanent Geschwindigkeit II", "§75: Permanent II + aktiv III");
            case HEAL -> List.of("§71: Aktiv Regeneration I", "§72: Permanent Regeneration I", "§73: Permanent I + aktiv II", "§74: Permanent I + aktiv II", "§75: Permanent I + aktiv III");
            case GUARDIAN -> List.of("§71: Aktive Resistenz I (10 s)", "§72: Aktive Resistenz I (15 s)", "§73: Aktive Resistenz II (10 s)", "§74: Permanent Resistenz I + aktiv II", "§75: Permanent Resistenz I + aktiv II (15 s)");
            case HEART -> List.of("§71-2: +1 Herz", "§73-4: +2 Herzen", "§75: +3 Herzen");
            case FIRE -> List.of("§71-5: Permanente Feuerresistenz");
            case ENDER -> List.of("§71: 8 Blöcke", "§72: 11 Blöcke", "§73: 14 Blöcke", "§74: 17 Blöcke", "§75: 20 Blöcke Teleportreichweite");
            case WIND -> List.of("§71: Kein Fallschaden", "§72: Windstoß (12 s Cooldown)", "§73: Windstoß (10 s)", "§74: Windstoß (8 s)", "§75: Windstoß (6 s)");
            case PHOENIX -> List.of("§71-2: Rettung mit 2/3 Herzen (15 min)", "§73: 4 Herzen (10 min)", "§74: 5 Herzen (7 min)", "§75: 6 Herzen (5 min)");
        };
    }

    private boolean hasGem(Player player, Gem gem) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getStorageContents()) if (getGem(item) == gem) return true;
        for (ItemStack item : inv.getArmorContents()) if (getGem(item) == gem) return true;
        return getGem(inv.getItemInOffHand()) == gem;
    }

    private int gemLevel(Player player, Gem gem) { return level(player.getUniqueId(), gem); }

    private void tickPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayerItems(player);
            applyPassives(player);
        }
    }

    private void applyPassives(Player player) {
        Map<PotionEffectType, Integer> wanted = new HashMap<>();
        if (hasGem(player, Gem.SPEED)) {
            int lv = gemLevel(player, Gem.SPEED);
            if (lv >= 2) wanted.put(PotionEffectType.SPEED, lv >= 4 ? 1 : 0);
        }
        if (hasGem(player, Gem.HEAL) && gemLevel(player, Gem.HEAL) >= 2) wanted.put(PotionEffectType.REGENERATION, 0);
        if (hasGem(player, Gem.GUARDIAN) && gemLevel(player, Gem.GUARDIAN) >= 4) wanted.put(PotionEffectType.RESISTANCE, 0);
        if (hasGem(player, Gem.FIRE)) wanted.put(PotionEffectType.FIRE_RESISTANCE, 0);

        Map<PotionEffectType, Integer> previous = managedEffects.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        for (Map.Entry<PotionEffectType, Integer> entry : wanted.entrySet()) {
            PotionEffect current = player.getPotionEffect(entry.getKey());
            int amp = entry.getValue();
            if (current == null || current.getAmplifier() < amp || (current.getAmplifier() == amp && current.getDuration() < 30)) {
                player.addPotionEffect(new PotionEffect(entry.getKey(), 50, amp, true, false, true));
                previous.put(entry.getKey(), amp);
            }
        }
        for (PotionEffectType type : new HashSet<>(previous.keySet())) {
            if (!wanted.containsKey(type)) {
                PotionEffect current = player.getPotionEffect(type);
                if (current != null && current.getAmplifier() == previous.get(type)) player.removePotionEffect(type);
                previous.remove(type);
            }
        }
        updateHeartBonus(player);
    }

    private void restoreManagedEffects(Player player) {
        Map<PotionEffectType, Integer> previous = managedEffects.remove(player.getUniqueId());
        if (previous == null) return;
        for (Map.Entry<PotionEffectType, Integer> entry : previous.entrySet()) {
            PotionEffect current = player.getPotionEffect(entry.getKey());
            if (current != null && current.getAmplifier() == entry.getValue()) player.removePotionEffect(entry.getKey());
        }
    }

    private double getBaseHealth(Player player) {
        UUID id = player.getUniqueId();
        if (baseHealth.containsKey(id)) return baseHealth.get(id);
        String path = "baseHealth." + id;
        double value = data.getDouble(path, player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
        baseHealth.put(id, value);
        if (!data.contains(path)) data.set(path, value);
        return value;
    }

    private void updateHeartBonus(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        double base = getBaseHealth(player);
        int hearts = hasGem(player, Gem.HEART) ? switch (gemLevel(player, Gem.HEART)) {
            case 1, 2 -> 1; case 3, 4 -> 2; default -> 3;
        } : 0;
        double desired = base + hearts * 2.0;
        if (Math.abs(attribute.getBaseValue() - desired) > 0.001) {
            double oldMax = attribute.getValue();
            attribute.setBaseValue(desired);
            player.setHealth(Math.min(attribute.getValue(), player.getHealth() + Math.max(0, attribute.getValue() - oldMax)));
        }
    }

    private void restoreHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) attribute.setBaseValue(getBaseHealth(player));
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Gem gem = getGem(event.getRecipe() == null ? null : event.getRecipe().getResult());
        if (gem != null) {
            if (data.getBoolean(craftedPath(gem), false)) event.getInventory().setResult(null);
            else event.getInventory().setResult(createGemItem(gem));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(CraftItemEvent event) {
        Gem gem = getGem(event.getCurrentItem());
        if (gem == null) return;
        if (event.isShiftClick() || data.getBoolean(craftedPath(gem), false)) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            data.set(craftedPath(gem), true);
            data.set(ownerPath(gem), player.getUniqueId().toString());
            saveData();
            Bukkit.broadcastMessage("§d✦ §f" + player.getName() + " §7hat das einzigartige " + gem.display + " §7hergestellt!");
            Bukkit.getScheduler().runTask(this, () -> refreshPlayerItems(player));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        if (hand == null) return;
        if (hand.getType() == Material.ENDER_PEARL) {
            event.setCancelled(true);
            player.sendMessage("§cEnderperlen sind auf diesem Server deaktiviert.");
            return;
        }
        Gem gem = getGem(hand);
        if (gem == null) return;
        event.setCancelled(true);
        activate(player, gem);
    }

    private void activate(Player player, Gem gem) {
        int lv = gemLevel(player, gem);
        switch (gem) {
            case SPEED -> {
                if (lv == 2 || lv == 4) { player.sendMessage("§7Dein Speed-Gem wirkt auf diesem Level passiv."); return; }
                int amp = lv >= 5 ? 2 : lv >= 3 ? 1 : 0;
                potionActive(player, gem, PotionEffectType.SPEED, amp, lv >= 5 ? 15 : lv >= 3 ? 30 : 20, lv >= 5 ? 90 : 60, "Geschwindigkeit");
            }
            case HEAL -> {
                if (lv == 2) { player.sendMessage("§7Dein Heil-Gem wirkt auf diesem Level passiv."); return; }
                int amp = lv >= 5 ? 2 : lv >= 3 ? 1 : 0;
                potionActive(player, gem, PotionEffectType.REGENERATION, amp, lv >= 5 ? 15 : 30, lv >= 5 ? 90 : 60, "Regeneration");
            }
            case GUARDIAN -> {
                int amp = lv >= 3 ? 1 : 0;
                int duration = (lv == 2 || lv == 5) ? 15 : 10;
                potionActive(player, gem, PotionEffectType.RESISTANCE, amp, duration, lv >= 5 ? 120 : 90, "Resistenz");
            }
            case HEART -> player.sendMessage("§dDein Herz-Gem wirkt passiv, solange es in deinem Inventar liegt.");
            case FIRE -> player.sendMessage("§6Dein Feuer-Gem gibt dir permanente Feuerresistenz.");
            case ENDER -> enderTeleport(player, lv);
            case WIND -> {
                if (lv < 2) { player.sendMessage("§cDer Windstoß ist ab Level 2 verfügbar."); return; }
                int cd = lv >= 5 ? 6 : lv == 4 ? 8 : lv == 3 ? 10 : 12;
                if (!checkCooldown(player, gem, cd)) return;
                player.setVelocity(player.getVelocity().add(new Vector(0, 1.25, 0)));
                player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation().add(0, 0.5, 0), 30, 0.4, 0.2, 0.4, 0.1);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
            }
            case PHOENIX -> player.sendMessage("§6Das Phönix-Gem rettet dich automatisch vor tödlichem Schaden.");
        }
    }

    private void potionActive(Player player, Gem gem, PotionEffectType type, int amplifier,
                              int seconds, int cooldownSeconds, String label) {
        if (!checkCooldown(player, gem, cooldownSeconds)) return;
        player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, true, true, true));
        player.sendMessage("§a" + label + " aktiviert.");
    }

    private boolean checkCooldown(Player player, Gem gem, int seconds) {
        String key = player.getUniqueId() + ":" + gem.id;
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(key, 0L);
        if (until > now) {
            player.sendMessage("§cCooldown: noch " + Math.max(1, (until - now + 999) / 1000) + " Sekunden.");
            return false;
        }
        cooldowns.put(key, now + seconds * 1000L);
        return true;
    }

    private void enderTeleport(Player player, int lv) {
        int[] ranges = {8, 11, 14, 17, 20};
        int[] cds = {14, 12, 10, 8, 6};
        if (!checkCooldown(player, Gem.ENDER, cds[lv - 1])) return;
        Location origin = player.getLocation();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location safe = null;
        for (double distance = 0.5; distance <= ranges[lv - 1]; distance += 0.5) {
            Location candidate = origin.clone().add(direction.clone().multiply(distance));
            candidate.setYaw(origin.getYaw()); candidate.setPitch(origin.getPitch());
            if (!isSafe(candidate)) break;
            safe = candidate;
        }
        if (safe == null) {
            player.sendMessage("§cVor dir ist kein sicherer Teleportpunkt.");
            return;
        }
        player.teleport(safe, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.playSound(safe, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, safe.clone().add(0, 1, 0), 35, 0.4, 0.8, 0.4, 0.5);
    }

    private boolean isSafe(Location location) {
        if (location.getWorld() == null) return false;
        var feet = location.getBlock();
        var head = location.clone().add(0, 1, 0).getBlock();
        var floor = location.clone().subtract(0, 1, 0).getBlock();
        return feet.isPassable() && head.isPassable() && !floor.isPassable() && !floor.isLiquid();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (hasGem(player, Gem.WIND) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() < player.getHealth() || !hasGem(player, Gem.PHOENIX)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL || cause == EntityDamageEvent.DamageCause.SUICIDE) return;
        long now = System.currentTimeMillis();
        if (phoenixCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) return;
        int lv = gemLevel(player, Gem.PHOENIX);
        int[] cooldown = {900, 900, 600, 420, 300};
        int[] hearts = {2, 3, 4, 5, 6};
        phoenixCooldowns.put(player.getUniqueId(), now + cooldown[lv - 1] * 1000L);
        event.setCancelled(true);
        double health = hearts[lv - 1] * 2.0;
        player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), health));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 8 * 20, 1, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 8 * 20, 0, true, true, true));
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.2);
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1f, 1f);
        player.sendMessage("§6✦ Das Phönix-Gem hat dich gerettet!");
    }

    @EventHandler
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                    || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.EnderPearl) event.setCancelled(true);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> {
            refreshPlayerItems(event.getPlayer());
            applyPassives(event.getPlayer());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        restoreManagedEffects(player);
        restoreHealth(player);
        String prefix = player.getUniqueId() + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        phoenixCooldowns.remove(player.getUniqueId());
        baseHealth.remove(player.getUniqueId());
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§d§lGEMS §7- Utility-Übersicht");
            for (Gem gem : Gem.values()) {
                String status = data.getBoolean(craftedPath(gem), false) ? "§a hergestellt" : "§e verfügbar";
                sender.sendMessage("§d/gems recipe " + gem.id + " §7- " + gem.display + status);
                if (sender instanceof Player player) {
                    sender.sendMessage("§7  Dein Level: §e" + level(player.getUniqueId(), gem) + " §7| XP: §b" + xp(player.getUniqueId(), gem));
                }
            }
            sender.sendMessage("§8Jedes vorhandene Gem erhält 5 XP pro Minute Spielzeit.");
            sender.sendMessage("§8Diese Fassung enthält nur Utility-/Schutzfähigkeiten, keine Waffen- oder Angriffsboni.");
            return true;
        }
        if (args[0].equalsIgnoreCase("recipe") && args.length >= 2) {
            Gem gem = Gem.find(args[1]);
            if (gem == null) { sender.sendMessage("§cUnbekanntes Gem."); return true; }
            sender.sendMessage("§dRezept: " + gem.display);
            for (String row : gem.shape) sender.sendMessage("§f[" + row.replace(' ', '·') + "]");
            sender.sendMessage("§7Zutaten:");
            gem.ingredients.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    sender.sendMessage("§f" + entry.getKey() + " §7= §f" + entry.getValue().name()));
            sender.sendMessage("§7Leere Felder sind mit · markiert.");
            return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("gems.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
            if (args.length < 2) { sender.sendMessage("§c/gems give <gem> [spieler]"); return true; }
            Gem gem = Gem.find(args[1]);
            if (gem == null) { sender.sendMessage("§cUnbekanntes Gem."); return true; }
            Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) { sender.sendMessage("§cSpieler nicht online oder Ziel fehlt."); return true; }
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(createGemItem(gem));
            for (ItemStack leftover : leftovers.values()) target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            refreshPlayerItems(target);
            sender.sendMessage("§a" + gem.display + " an " + target.getName() + " vergeben.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("gems.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
            if (args.length < 2) { sender.sendMessage("§c/gems reset <gem>"); return true; }
            Gem gem = Gem.find(args[1]);
            if (gem == null) { sender.sendMessage("§cUnbekanntes Gem."); return true; }
            data.set(craftedPath(gem), false);
            data.set(ownerPath(gem), null);
            saveData();
            sender.sendMessage("§eHerstellungsstatus von " + gem.display + " zurückgesetzt. Bestehende Items bleiben erhalten.");
            return true;
        }
        if (args[0].equalsIgnoreCase("setlevel")) {
            if (!sender.hasPermission("gems.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage("§cDieser Befehl muss im Spiel ausgeführt werden."); return true; }
            if (args.length < 3) { sender.sendMessage("§c/gems setlevel <gem> <1-5>"); return true; }
            Gem gem = Gem.find(args[1]);
            int lv;
            try { lv = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage("§cLevel muss 1 bis 5 sein."); return true; }
            if (gem == null || lv < 1 || lv > 5) { sender.sendMessage("§cGem oder Level ungültig."); return true; }
            String path = playerPath(player.getUniqueId(), gem);
            data.set(path + ".level", lv); data.set(path + ".xp", 0);
            refreshPlayerItems(player); saveData();
            sender.sendMessage("§a" + gem.display + " ist jetzt Level " + lv + ".");
            return true;
        }
        sender.sendMessage("§cVerwendung: /gems | /gems recipe <gem> | /gems give <gem> [spieler] | /gems reset <gem> | /gems setlevel <gem> <1-5>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> gems = Arrays.stream(Gem.values()).map(g -> g.id).collect(Collectors.toList());
        if (args.length == 1) return Arrays.asList("recipe", "give", "reset", "setlevel").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("recipe") || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("setlevel"))) {
            return gems.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("setlevel")) return List.of("1", "2", "3", "4", "5");
        return Collections.emptyList();
    }

    private void saveData() {
        if (data == null || dataFile == null) return;
        try { data.save(dataFile); }
        catch (IOException exception) { getLogger().severe("Could not save data.yml: " + exception.getMessage()); }
    }
}
