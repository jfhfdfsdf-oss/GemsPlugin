package de.levelpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityExperienceChangeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LevelPvPPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack[]>> inbox = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingSenders = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> pendingDeliveryItems = new ConcurrentHashMap<>();
    private File playersFile, deliveriesFile;
    private YamlConfiguration playersYaml, deliveriesYaml;
    private NamespacedKey crystalKey;
    private NamespacedKey guiKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        crystalKey = new NamespacedKey(this, "level_crystal");
        guiKey = new NamespacedKey(this, "levelpvp_gui");
        playersFile = new File(getDataFolder(), "players.yml");
        deliveriesFile = new File(getDataFolder(), "deliveries.yml");
        loadData();
        Objects.requireNonNull(getCommand("stats")).setExecutor(this);
        Objects.requireNonNull(getCommand("delivery")).setExecutor(this);
        Objects.requireNonNull(getCommand("estractlevel")).setExecutor(this);
        Objects.requireNonNull(getCommand("levelcap")).setExecutor(this);
        Objects.requireNonNull(getCommand("pvpmod")).setExecutor(this);
        Objects.requireNonNull(getCommand("pvpmod")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) applyHealth(p);
        getLogger().info("LevelPvP 1.1.0 enabled.");
    }

    @Override public void onDisable() { saveData(); }

    private static final class PlayerData {
        int level, kills, deaths;
        Integer personalCap;
        String lastName = "Unknown";
    }

    private PlayerData data(UUID id) { return players.computeIfAbsent(id, k -> new PlayerData()); }
    private int minLevel() { return getConfig().getInt("settings.min-level", -10); }
    private int globalCap() { return Math.min(50, Math.max(1, getConfig().getInt("settings.global-max-level", 30))); }
    private int cap(PlayerData d) { return d.personalCap == null ? globalCap() : Math.min(50, Math.max(1, d.personalCap)); }
    private int clamp(PlayerData d, int level) { return Math.max(minLevel(), Math.min(cap(d), level)); }

    // Whole-heart progression: 0..30 adds 1 heart / 3 levels; 30..50 adds 1 / 2 levels.
    private int hearts(int level) {
        if (level < 0) return Math.max(1, 10 + level);
        if (level <= 30) return 10 + level / 3;
        if (level <= 40) return 20 + (level - 30) / 2;
        return 25 + (level - 40) / 2;
    }

    private void applyHealth(Player p) {
        PlayerData d = data(p.getUniqueId());
        int hearts = hearts(d.level);
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double max = hearts * 2.0;
        attr.setBaseValue(max);
        if (p.getHealth() > max) p.setHealth(max);
    }

    private void setLevel(UUID id, int level) {
        PlayerData d = data(id);
        d.level = clamp(d, level);
        Player p = Bukkit.getPlayer(id);
        if (p != null) applyHealth(p);
        saveData();
    }
    private void changeLevel(UUID id, int amount) { setLevel(id, data(id).level + amount); }

    private void loadData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        playersYaml = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection sec = playersYaml.getConfigurationSection("players");
        if (sec != null) for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                PlayerData d = new PlayerData();
                d.level = sec.getInt(key + ".level", 0);
                d.kills = sec.getInt(key + ".kills", 0);
                d.deaths = sec.getInt(key + ".deaths", 0);
                d.lastName = sec.getString(key + ".name", "Unknown");
                if (sec.contains(key + ".cap")) d.personalCap = sec.getInt(key + ".cap");
                players.put(id, d);
            } catch (IllegalArgumentException ignored) {}
        }
        deliveriesYaml = YamlConfiguration.loadConfiguration(deliveriesFile);
        ConfigurationSection inboxSec = deliveriesYaml.getConfigurationSection("inbox");
        if (inboxSec != null) for (String key : inboxSec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                List<ItemStack[]> list = new ArrayList<>();
                for (String deliveryId : inboxSec.getConfigurationSection(key).getKeys(false)) {
                    List<?> raw = inboxSec.getList(key + "." + deliveryId);
                    ItemStack[] items = new ItemStack[18];
                    if (raw != null) for (int i=0; i<Math.min(18, raw.size()); i++)
                        if (raw.get(i) instanceof ItemStack item) items[i] = item;
                    list.add(items);
                }
                inbox.put(id, list);
            } catch (Exception ignored) {}
        }
    }

    private void saveData() {
        if (playersFile == null) return;
        YamlConfiguration py = new YamlConfiguration();
        for (var e : players.entrySet()) {
            String k = "players." + e.getKey();
            PlayerData d = e.getValue();
            py.set(k + ".level", d.level); py.set(k + ".kills", d.kills);
            py.set(k + ".deaths", d.deaths); py.set(k + ".name", d.lastName);
            if (d.personalCap != null) py.set(k + ".cap", d.personalCap);
        }
        saveAtomic(py, playersFile);
        YamlConfiguration dy = new YamlConfiguration();
        for (var e : inbox.entrySet()) {
            int i = 0;
            for (ItemStack[] items : e.getValue()) dy.set("inbox." + e.getKey() + "." + (i++), Arrays.asList(items));
        }
        saveAtomic(dy, deliveriesFile);
    }

    private void saveAtomic(YamlConfiguration yaml, File file) {
        try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            yaml.save(tmp);
            try { java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ex) { java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) { getLogger().severe("Could not save " + file.getName() + ": " + ex.getMessage()); }
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        data(e.getPlayer().getUniqueId()).lastName = e.getPlayer().getName();
        applyHealth(e.getPlayer()); saveData();
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { saveData(); }

    @EventHandler(ignoreCancelled = true)
    public void onXp(EntityExperienceChangeEvent e) {
        if (e.getEntity() instanceof Player p) {
            int amount = (int)Math.floor(e.getAmount() * xpMultiplier(data(p.getUniqueId()).level));
            e.setAmount(Math.max(0, amount));
        }
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        ItemStack item = e.getItem();
        if (item == null || !isCrystal(item)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        PlayerData d = data(p.getUniqueId());
        if (d.level >= 0) { p.sendMessage("§cDu kannst Level-Kristalle nur bei negativem Level benutzen."); return; }
        changeLevel(p.getUniqueId(), 1);
        item.setAmount(item.getAmount() - 1);
        p.sendMessage("§a+1 Level. Dein Level: §e" + data(p.getUniqueId()).level);
    }

    private ItemStack crystal(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§dLevel-Kristall");
        meta.setLore(List.of("§7Rechtsklick: §a+1 Level", "§8LevelPvP"));
        meta.getPersistentDataContainer().set(crystalKey, PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta);
        return item;
    }
    private boolean isCrystal(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(crystalKey, PersistentDataType.BYTE);
    }

    private void openDelivery(Player p) {
        Inventory inv = Bukkit.createInventory(new MenuHolder("delivery"), 27, "§8Delivery");
        inv.setItem(22, named(Material.WRITABLE_BOOK, "§aEmpfänger auswählen", "§7Klicke hier und wähle einen Spieler."));
        inv.setItem(26, named(Material.LIME_DYE, "§aLieferung senden", "§7Lege Items in die oberen Slots."));
        inv.setItem(18, named(Material.BARREL, "§eEingang öffnen", "§7Deine erhaltenen Lieferungen"));
        p.openInventory(inv);
    }
    private void openRecipients(Player sender, ItemStack[] contents) {
        Inventory inv = Bukkit.createInventory(new MenuHolder("recipients"), 54, "§8Empfänger auswählen");
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(sender) || slot >= 45) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target); meta.setDisplayName("§e" + target.getName());
            meta.setLore(List.of("§7Klicken zum Auswählen"));
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        sender.openInventory(inv);
    }
    private void openInbox(Player p) {
        List<ItemStack[]> list = inbox.getOrDefault(p.getUniqueId(), new ArrayList<>());
        if (list.isEmpty()) { p.sendMessage("§7Du hast keine offenen Lieferungen."); return; }
        Inventory inv = Bukkit.createInventory(new MenuHolder("inbox"), 54, "§8Deine Lieferungen");
        int slot = 0;
        for (int i=0; i<list.size() && slot<45; i++) {
            ItemStack box = named(Material.CHEST, "§eLieferung #" + (i+1), "§7Klicken zum Abholen");
            box.getItemMeta().getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, "delivery:" + i);
            inv.setItem(slot++, box);
        }
        p.openInventory(inv);
    }
    private ItemStack named(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(List.of(lore)); item.setItemMeta(meta); return item;
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getInventory().getHolder() instanceof MenuHolder holder)) return;
        String type = holder.type;
        if (type.equals("delivery")) {
            if (e.getRawSlot() >= 27) return;
            if (e.getRawSlot() == 22 || e.getRawSlot() == 26 || e.getRawSlot() == 18) e.setCancelled(true);
            if (e.getRawSlot() == 22) {
                ItemStack[] contents = new ItemStack[18];
                for (int i=0;i<18;i++) contents[i] = e.getInventory().getItem(i);
                pendingSenders.put(p.getUniqueId(), p.getUniqueId());
                pendingDeliveryItems.put(p.getUniqueId(), contents);
                openRecipients(p, contents);
            } else if (e.getRawSlot() == 18) { e.setCancelled(true); openInbox(p); }
            else if (e.getRawSlot() == 26) {
                e.setCancelled(true);
                p.sendMessage("§cWähle zuerst einen Empfänger über den Buch-Slot.");
            }
            return;
        }
        e.setCancelled(true);
        if (type.equals("recipients")) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD || !(clicked.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) || sm.getOwningPlayer() == null) return;
            UUID recipient = sm.getOwningPlayer().getUniqueId();
            ItemStack[] items = pendingDeliveryItems.remove(p.getUniqueId());
            pendingSenders.remove(p.getUniqueId());
            if (items == null || Arrays.stream(items).allMatch(it -> it == null || it.getType() == Material.AIR)) {
                p.closeInventory();
                p.sendMessage("§cDu hast keine Items in die Lieferung gelegt.");
                return;
            }
            inbox.computeIfAbsent(recipient, k -> new ArrayList<>()).add(items);
            saveData();
            p.closeInventory();
            Player onlineTarget = Bukkit.getPlayer(recipient);
            if (onlineTarget != null) onlineTarget.sendMessage("§aDu hast eine neue Lieferung erhalten! Öffne §e/delivery§a zum Abholen.");
            p.sendMessage("§aLieferung an §e" + sm.getOwningPlayer().getName() + " §agesendet.");
            return;
        }
        if (type.equals("inbox")) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.CHEST) return;
            // Claim by displayed index; all items are safely inserted or retained if inventory is full.
            int index = e.getRawSlot();
            List<ItemStack[]> list = inbox.get(p.getUniqueId());
            if (list == null || index >= list.size()) return;
            ItemStack[] items = list.remove(index);
            for (ItemStack it : items) if (it != null && it.getType() != Material.AIR) {
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(it);
                for (ItemStack rest : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
            saveData(); p.closeInventory(); p.sendMessage("§aLieferung abgeholt.");
        }
    }

    @EventHandler public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuHolder h && !h.type.equals("delivery")) e.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !(e.getInventory().getHolder() instanceof MenuHolder h)) return;
        if (h.type.equals("delivery")) {
            // The contents were copied to the pending delivery before switching to recipient selection.
            if (pendingDeliveryItems.containsKey(p.getUniqueId())) return;
            // Items left in the compose GUI are returned to the sender to prevent loss.
            for (int i=0;i<18;i++) {
                ItemStack item = e.getInventory().getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    Map<Integer, ItemStack> left = p.getInventory().addItem(item);
                    for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
                }
            }
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        final String type;
        MenuHolder(String type) { this.type = type; }
        @Override public Inventory getInventory() { return null; }
    }

    private void openStats(Player p) {
        PlayerData d = data(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(new MenuHolder("stats"), 27, "§8Deine Statistiken");
        inv.setItem(4, named(Material.PLAYER_HEAD, "§e" + p.getName(), "§7Dein Profil"));
        inv.setItem(10, named(Material.NETHER_STAR, "§bLevel: §f" + d.level, "§7Levelcap: " + cap(d)));
        inv.setItem(12, named(Material.COMPASS, "§cKills: §f" + d.kills, "§7Gesamte Kills"));
        inv.setItem(14, named(Material.SKELETON_SKULL, "§cTode: §f" + d.deaths, "§7Gesamte Tode"));
        inv.setItem(16, named(Material.GOLDEN_APPLE, "§aHerzen: §f" + hearts(d.level), "§7Ganze Herzen"));
        p.openInventory(inv);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("stats")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler."); return true; }
            openStats(p); return true;
        }
        if (cmd.equals("delivery")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler."); return true; }
            openDelivery(p); return true;
        }
        if (cmd.equals("estractlevel")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler."); return true; }
            if (args.length != 1) { sender.sendMessage("§c/estractlevel <Anzahl>"); return true; }
            int amount;
            try { amount = Integer.parseInt(args[0]); } catch (NumberFormatException ex) { sender.sendMessage("§cBitte eine gültige Zahl eingeben."); return true; }
            PlayerData d = data(p.getUniqueId());
            if (amount < 1 || amount > 64 || d.level < amount) { sender.sendMessage("§cDu kannst maximal so viele Level extrahieren, wie du besitzt (max. 64 pro Aufruf)."); return true; }
            int remaining = amount;
            while (remaining > 0) {
                int stack = Math.min(64, remaining);
                p.getInventory().addItem(crystal(stack));
                remaining -= stack;
            }
            changeLevel(p.getUniqueId(), -amount);
            sender.sendMessage("§a" + amount + " Level wurden in Level-Kristalle umgewandelt.");
            return true;
        }
        if (cmd.equals("levelcap")) {
            if (!sender.hasPermission("levelpvp.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
            if (args.length == 2 && args[0].equalsIgnoreCase("global")) {
                int cap = parseCap(args[1]); if (cap < 1) { sender.sendMessage("§cMaximallevel muss zwischen 1 und 50 liegen."); return true; }
                getConfig().set("settings.global-max-level", cap); saveConfig();
                sender.sendMessage("§aGlobales Levelcap: " + cap); return true;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
                Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { sender.sendMessage("§cSpieler muss online sein."); return true; }
                data(target.getUniqueId()).personalCap = null; setLevel(target.getUniqueId(), data(target.getUniqueId()).level);
                saveData(); sender.sendMessage("§aIndividuelles Cap zurückgesetzt."); return true;
            }
            if (args.length == 2) {
                Player target = Bukkit.getPlayerExact(args[0]); int cap = parseCap(args[1]);
                if (target == null || cap < 1) { sender.sendMessage("§c/levelcap <Online-Spieler> <1-50>"); return true; }
                PlayerData d = data(target.getUniqueId()); d.personalCap = cap; setLevel(target.getUniqueId(), d.level);
                saveData(); sender.sendMessage("§aLevelcap für " + target.getName() + ": " + cap); return true;
            }
            sender.sendMessage("§c/levelcap <Spieler> <1-50> | /levelcap reset <Spieler> | /levelcap global <1-50>"); return true;
        }
        if (cmd.equals("pvpmod")) {
            if (!sender.hasPermission("levelpvp.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
            if (args.length == 0) { sender.sendMessage("§c/pvpmod <reload|setlevel|addlevel|removelevel|stats|reset>"); return true; }
            if (args[0].equalsIgnoreCase("reload")) { reloadConfig(); sender.sendMessage("§aKonfiguration geladen."); return true; }
            if (args.length >= 2) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage("§cSpieler muss online sein."); return true; }
                PlayerData d = data(target.getUniqueId());
                try {
                    if (args[0].equalsIgnoreCase("setlevel") && args.length == 3) setLevel(target.getUniqueId(), Integer.parseInt(args[2]));
                    else if (args[0].equalsIgnoreCase("addlevel") && args.length == 3) changeLevel(target.getUniqueId(), Integer.parseInt(args[2]));
                    else if (args[0].equalsIgnoreCase("removelevel") && args.length == 3) changeLevel(target.getUniqueId(), -Integer.parseInt(args[2]));
                    else if (args[0].equalsIgnoreCase("reset")) { players.remove(target.getUniqueId()); applyHealth(target); saveData(); }
                    else if (args[0].equalsIgnoreCase("stats")) sender.sendMessage("§e" + target.getName() + " §7Level " + d.level + " | Kills " + d.kills + " | Tode " + d.deaths);
                    else { sender.sendMessage("§cUngültiger Unterbefehl."); return true; }
                } catch (NumberFormatException ex) { sender.sendMessage("§cUngültige Zahl."); return true; }
                saveData(); sender.sendMessage("§aBefehl ausgeführt."); return true;
            }
            sender.sendMessage("§cUngültige Argumente."); return true;
        }
        return false;
    }

    private int parseCap(String s) {
        try { int n = Integer.parseInt(s); return n >= 1 && n <= 50 ? n : -1; }
        catch (NumberFormatException ex) { return -1; }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("levelpvp.admin")) return List.of();
        if (command.getName().equalsIgnoreCase("pvpmod") && args.length == 1)
            return List.of("reload","setlevel","addlevel","removelevel","stats","reset").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}
