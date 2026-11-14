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
