package de.gems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class GemListener implements Listener {

    private final GemsPlugin plugin;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, Long> killLog = new HashMap<>();
    private final Random rnd = new Random();
    private final Map<UUID, ArmorStand> echoes = new HashMap<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private static final long COMBAT_LOCK_MS = 15_000L;

    /** XP pro Spieler-Kill (Mobs geben keine Gem-XP) */
    private static final int KILL_XP = 30;
    /** Gleiches Opfer innerhalb dieser Zeit gibt keine XP (Anti-Farming) */
    private static final long SAME_VICTIM_MS = 10 * 60 * 1000L;

    public GemListener(GemsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ crafting (jedes Gem nur 1x)

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (plugin.gemOf(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
        Recipe r = e.getRecipe();
        if (r == null) return;
        Gem g = plugin.gemOf(r.getResult());
        if (g != null && plugin.isCrafted(g)) e.getInventory().setResult(null);
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        Gem g = plugin.gemOf(e.getRecipe().getResult());
        if (g == null) return;
        if (plugin.isCrafted(g) || e.isShiftClick()) {
            e.setCancelled(true);
            return;
        }
        plugin.markCrafted(g, (Player) e.getWhoClicked());
    }

    // ------------------------------------------------------------ Gem-Items schützen

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        if (plugin.gemOf(e.getEntity().getItemStack()) != null) {
            e.getEntity().setUnlimitedLifetime(true);
            e.getEntity().setInvulnerable(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (plugin.gemOf(e.getPlayer().getInventory().getItem(e.getHand())) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------ Kills -> XP (nur Spieler-Kills)

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player deadPlayer) removeEcho(deadPlayer);
        Player killer = dead.getKiller();
        if (killer == null || killer.equals(dead)) return;

        // Gem-XP gibt es nur für Spieler-Kills
        if (!(dead instanceof Player victim)) return;
        Set<Gem> held = plugin.heldGems(killer);
        if (held.isEmpty()) return;

        long now = System.currentTimeMillis();
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        Long last = killLog.get(key);
        if (last != null && now - last < SAME_VICTIM_MS) {
            killer.sendActionBar(Component.text("Gleiches Opfer – keine Gem-XP (10 Min Sperre)", NamedTextColor.GRAY));
            return;
        }
        killLog.put(key, now);
        for (Gem g : held) plugin.addXp(killer, g, KILL_XP);
    }

    // ------------------------------------------------------------ Kampf

    private boolean maceAllowed(ItemStack mace, int lvl) {
        return mace.getEnchantmentLevel(Enchantment.BREACH) <= Gem.maceBreach(lvl)
                && mace.getEnchantmentLevel(Enchantment.DENSITY) <= Gem.maceDensity(lvl)
                && mace.getEnchantmentLevel(Enchantment.WIND_BURST) <= Gem.maceWindBurst(lvl);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player p = null;
        if (e.getDamager() instanceof Player pl) p = pl;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl) p = pl;
        if (p == null) return;
        if (e.getEntity() instanceof Player victim) {
            markCombat(p);
            markCombat(victim);
        }

        // --- Mace-Gem: ohne Gem keine Mace, Verzauberungen nur bis zum erlaubten Level
        if (e.getDamager() instanceof Player) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.MACE) {
                int lvl = plugin.levelOf(p, Gem.MACE);
                if (lvl == 0) {
                    e.setCancelled(true);
                    p.sendActionBar(Component.text("Du brauchst das Mace-Gem, um die Mace zu nutzen!", NamedTextColor.RED));
                    return;
                }
                if (!maceAllowed(hand, lvl)) {
                    e.setCancelled(true);
                    p.sendActionBar(Component.text("Deine Mace ist stärker verzaubert, als dein Mace-Gem-Level erlaubt!", NamedTextColor.RED));
                    return;
                }
            }
        }

        if (!(e.getEntity() instanceof LivingEntity target) || target.equals(p)) return;

        int fire = plugin.levelOf(p, Gem.FIRE);
        if (fire >= 2) target.setFireTicks(20 * Gem.fireBurn(fire));

        int frost = plugin.levelOf(p, Gem.FROST);
        if (frost > 0 && ready(p, "FROST_PROC", Gem.frostProcCooldown(frost), false)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * (2 + frost), Gem.frostAmp(frost)));
            target.setFreezeTicks(target.getMaxFreezeTicks());
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.02);
        }

        int hunter = plugin.levelOf(p, Gem.HUNTER);
        if (hunter >= 2 && ready(p, "HUNTER_PROC", 15, false)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * (2 + hunter), 0, false, false, false));
        }

        int vamp = plugin.levelOf(p, Gem.VAMPIRE);
        if (vamp > 0 && p.getAttribute(Attribute.MAX_HEALTH) != null) {
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + e.getFinalDamage() * 0.04 * vamp));
        }

        int thunder = plugin.levelOf(p, Gem.THUNDER);
        if (thunder > 0 && rnd.nextDouble() < 0.04 * thunder) {
            target.getWorld().strikeLightning(target.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityDamageEvent.DamageCause cause = e.getCause();

        if (cause == EntityDamageEvent.DamageCause.LIGHTNING && plugin.levelOf(p, Gem.THUNDER) > 0) {
            e.setCancelled(true);
            p.setFireTicks(0);
            return;
        }
        // Wind-Gem: kein Fallschaden (ohne Slow-Falling, damit Mace-Smash weiter funktioniert)
        if (cause == EntityDamageEvent.DamageCause.FALL && plugin.levelOf(p, Gem.WIND) > 0) {
            e.setCancelled(true);
            return;
        }
        // Phönix-Gem: tödlichen Schaden überleben
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.SUICIDE) return;
        int ph = plugin.levelOf(p, Gem.PHOENIX);
        if (ph > 0 && p.getHealth() - e.getFinalDamage() <= 0
                && ready(p, Gem.PHOENIX, Gem.PHOENIX.cooldownSeconds(ph), false)) {
            e.setCancelled(true);
            double max = p.getAttribute(Attribute.MAX_HEALTH) != null ? p.getAttribute(Attribute.MAX_HEALTH).getValue() : 20;
            p.setHealth(Math.min(max, 2 + 2 * ph));
            p.setFireTicks(0);
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0));
            p.playEffect(EntityEffect.TOTEM_RESURRECT);
            p.sendMessage(Component.text("✦ Dein Phönix-Gem hat dich gerettet! (Cooldown "
                    + Gem.PHOENIX.cooldownSeconds(ph) / 60 + " Min)", NamedTextColor.GOLD));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        ItemStack result = e.getResult();

        // Prot max. III, Schärfe max. IV
        if (result != null && !capsOk(p, result)) {
            e.setResult(null);
            return;
        }

        // Mace: Verzauberungen nur bis zum erlaubten Level
        if (result != null && result.getType() == Material.MACE) {
            if (!maceAllowed(result, plugin.levelOf(p, Gem.MACE))) e.setResult(null);
            return;
        }

    }

    private int levelAny(ItemStack it, Enchantment ench) {
        int lvl = it.getEnchantmentLevel(ench);
        if (it.getItemMeta() instanceof EnchantmentStorageMeta esm) lvl = Math.max(lvl, esm.getStoredEnchantLevel(ench));
        return lvl;
    }

    private int sharpCap(Player p, ItemStack it) {
        return 4;
    }

    private boolean capsOk(Player p, ItemStack it) {
        return levelAny(it, Enchantment.PROTECTION) <= 3 && levelAny(it, Enchantment.SHARPNESS) <= sharpCap(p, it);
    }

    /** Enchanting-Table: Protection auf III und Schärfe auf IV begrenzen. */
    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        Map<Enchantment, Integer> m = e.getEnchantsToAdd();
        if (m.getOrDefault(Enchantment.PROTECTION, 0) > 3) m.put(Enchantment.PROTECTION, 3);
        int cap = sharpCap(e.getEnchanter(), e.getItem());
        if (m.getOrDefault(Enchantment.SHARPNESS, 0) > cap) m.put(Enchantment.SHARPNESS, cap);
    }

    /** Netherite-Upgrade nur für Schaufel, Spitzhacke und Hacke. */
    @EventHandler(ignoreCancelled = true)
    public void onSmithing(PrepareSmithingEvent e) {
        ItemStack r = e.getResult();
        if (r == null) return;
        String n = r.getType().name();
        if (!n.startsWith("NETHERITE_")) return;
        if (n.endsWith("_SHOVEL") || n.endsWith("_PICKAXE") || n.endsWith("_HOE")) return;
        e.setResult(null);
    }

    // ------------------------------------------------------------ Rechtsklick-Fähigkeiten

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Gem g = plugin.gemOf(e.getItem());
        if (g == null) return;
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        int lvl = plugin.data(g).level;
        if (g.potion() != null) {
            useTier(p, g, lvl);
            return;
        }
        switch (g) {
            case ENDER -> blink(p, lvl);
            case WIND -> {
                if (lvl >= 2) windBurst(p, lvl);
            }
            case FIRE -> {
                if (lvl >= 3) fireball(p, lvl);
            }
            case FROST -> {
                if (lvl >= 4) frostNova(p, lvl);
            }
            case HUNTER -> track(p, lvl);
            case ECHO -> useEcho(p, lvl);
            default -> { }
        }
    }

    // ------------------------------------------------------------ Echo-Gem

    private void markCombat(Player p) {
        combatUntil.put(p.getUniqueId(), System.currentTimeMillis() + COMBAT_LOCK_MS);
    }

    private boolean inCombat(Player p) {
        long until = combatUntil.getOrDefault(p.getUniqueId(), 0L);
        if (until <= System.currentTimeMillis()) {
            combatUntil.remove(p.getUniqueId());
            return false;
        }
        long seconds = (until - System.currentTimeMillis() + 999) / 1000;
        p.sendActionBar(Component.text("Echo im Kampf gesperrt (" + seconds + "s)", NamedTextColor.RED));
        return true;
    }

    private boolean safeEchoLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Location feet = loc.clone();
        Location head = feet.clone().add(0, 1, 0);
        Location floor = feet.clone().subtract(0, 1, 0);
        return feet.getBlock().isPassable() && head.getBlock().isPassable()
                && floor.getBlock().getType().isSolid();
    }

    private void useEcho(Player p, int lvl) {
        if (inCombat(p)) return;
        UUID id = p.getUniqueId();
        ArmorStand marker = echoes.get(id);

        if (p.isSneaking()) {
            if (marker == null || !marker.isValid()) {
                echoes.remove(id);
                p.sendActionBar(Component.text("Du hast kein Echo gesetzt.", NamedTextColor.GRAY));
                return;
            }
            marker.remove();
            echoes.remove(id);
            p.sendActionBar(Component.text("Echo entfernt.", NamedTextColor.LIGHT_PURPLE));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            return;
        }

        if (marker == null || !marker.isValid()) {
            Location here = p.getLocation().clone();
            if (!safeEchoLocation(here)) {
                p.sendActionBar(Component.text("Hier kann kein Echo platziert werden.", NamedTextColor.RED));
                return;
            }
            marker = p.getWorld().spawn(here, ArmorStand.class);
            marker.setVisible(false);
            marker.setMarker(true);
            marker.setGravity(false);
            marker.setInvulnerable(true);
            marker.setSilent(true);
            marker.setCollidable(false);
            marker.setGlowing(true);
            marker.customName(Component.text("✦ Echo", NamedTextColor.LIGHT_PURPLE));
            marker.setCustomNameVisible(true);
            echoes.put(id, marker);
            p.sendActionBar(Component.text("Echo gesetzt! Rechtsklick zum Tauschen (Cooldown), Schleichen + Rechtsklick zum Entfernen.", NamedTextColor.LIGHT_PURPLE));
            p.playSound(here, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            return;
        }

        Location echoLoc = marker.getLocation();
        Location playerLoc = p.getLocation().clone();
        if (!echoLoc.getWorld().equals(p.getWorld())) {
            p.sendActionBar(Component.text("Dein Echo befindet sich in einer anderen Welt.", NamedTextColor.RED));
            return;
        }
        if (!safeEchoLocation(echoLoc) || !safeEchoLocation(playerLoc)) {
            p.sendActionBar(Component.text("Der Positionswechsel ist hier nicht sicher möglich.", NamedTextColor.RED));
            return;
        }
        String cooldownKey = p.getUniqueId() + ":ECHO";
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(cooldownKey, 0L);
        if (now < until) {
            long minutes = (until - now + 59_999L) / 60_000L;
            p.sendActionBar(Component.text("Echo-Cooldown: noch " + minutes + " Min.", NamedTextColor.RED));
            return;
        }
        if (p.teleport(echoLoc)) {
            cooldowns.put(cooldownKey, now + Gem.ECHO.cooldownSeconds(lvl) * 1000L);
            marker.teleport(playerLoc);
            p.setFallDistance(0);
            p.getWorld().spawnParticle(Particle.PORTAL, playerLoc.clone().add(0, 1, 0), 24, 0.35, 0.5, 0.35, 0.05);
            p.getWorld().spawnParticle(Particle.PORTAL, echoLoc.clone().add(0, 1, 0), 24, 0.35, 0.5, 0.35, 0.05);
            p.playSound(echoLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
        }
    }

    public void clearEchoes() {
        for (ArmorStand marker : echoes.values()) {
            if (marker != null && marker.isValid()) marker.remove();
        }
        echoes.clear();
        combatUntil.clear();
    }

    private void removeEcho(Player p) {
        ArmorStand marker = echoes.remove(p.getUniqueId());
        if (marker != null && marker.isValid()) marker.remove();
        combatUntil.remove(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeEcho(e.getPlayer());
    }

    private boolean ready(Player p, Gem g, int seconds, boolean msg) {
        return ready(p, g.name(), seconds, msg);
    }

    private boolean ready(Player p, String id, int seconds, boolean msg) {
        String key = p.getUniqueId() + ":" + id;
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(key, 0L);
        if (now < until) {
            if (msg) p.sendActionBar(Component.text("Cooldown: " + ((until - now) / 1000 + 1) + "s", NamedTextColor.RED));
            return false;
        }
        cooldowns.put(key, now + seconds * 1000L);
        return true;
    }

    private void useTier(Player p, Gem g, int lvl) {
        int pa = g.passiveAmp(lvl);
        int[] act = g.activeTier(lvl);
        if (act[0] <= pa) {
            p.sendActionBar(Component.text(g.display + ": bereits dauerhaft aktiv", NamedTextColor.GRAY));
            return;
        }
        if (!ready(p, g, act[2], true)) return;
        p.addPotionEffect(new PotionEffect(g.potion(), act[1] * 20, act[0], false, true, true));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0);
        p.sendActionBar(Component.text(g.display + " aktiviert: Stufe " + Gem.roman(act[0] + 1)
                + " für " + act[1] + "s", g.color));
    }

    private void blink(Player p, int lvl) {
        double range = 5 + 3 * lvl;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location best = null;
        for (double d = 1; d <= range; d += 0.5) {
            Location t = eye.clone().add(dir.clone().multiply(d));
            if (t.getBlock().isPassable() && t.clone().subtract(0, 1, 0).getBlock().isPassable()) {
                best = t.clone().subtract(0, 1.62, 0);
            } else {
                break;
            }
        }
        if (best == null) return;
        if (!ready(p, Gem.ENDER, Gem.ENDER.cooldownSeconds(lvl), true)) return;
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.2);
        p.teleport(best);
        p.setFallDistance(0);
        p.getWorld().spawnParticle(Particle.PORTAL, best.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.2);
        p.getWorld().playSound(best, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    private void windBurst(Player p, int lvl) {
        if (!ready(p, Gem.WIND, Gem.WIND.cooldownSeconds(lvl), true)) return;
        Vector dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 0) dir.normalize().multiply(0.4);
        p.setVelocity(new Vector(dir.getX(), 0.7 + 0.1 * lvl, dir.getZ()));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 30, 0.4, 0.1, 0.4, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);

        if (lvl >= 5) {
            for (Entity en : p.getNearbyEntities(4, 2, 4)) {
                if (en instanceof LivingEntity le && !en.equals(p)) {
                    Vector away = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 0) away.normalize().multiply(1.1);
                    le.setVelocity(away.setY(0.5));
                }
            }
        }
    }

    private void track(Player p, int lvl) {
        double range = Gem.hunterRange(lvl);
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player t : p.getWorld().getPlayers()) {
            if (t.equals(p) || t.getGameMode() == GameMode.SPECTATOR) continue;
            double d = t.getLocation().distance(p.getLocation());
            if (d <= range && d < bestD) {
                best = t;
                bestD = d;
            }
        }
        if (best == null) {
            p.sendActionBar(Component.text("Jäger: niemand in " + (int) range + " Blöcken", NamedTextColor.GRAY));
            return;   // kein Cooldown, wenn niemand gefunden wurde
        }
        if (!ready(p, Gem.HUNTER, Gem.HUNTER.cooldownSeconds(lvl), true)) return;

        Vector to = best.getLocation().toVector().subtract(p.getLocation().toVector());
        double angleTo = Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        double rel = ((angleTo - p.getLocation().getYaw()) % 360 + 360) % 360;   // 0 = geradeaus, 90 = rechts
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[(int) Math.round(rel / 45.0) % 8];
        String vertical = to.getY() > 8 ? " ▲" : (to.getY() < -8 ? " ▼" : "");
        p.sendActionBar(Component.text("Jäger: " + best.getName() + " – " + (int) bestD + " m " + arrow + vertical,
                Gem.HUNTER.color));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.6f);
    }

    private void frostNova(Player p, int lvl) {
        if (!ready(p, Gem.FROST, Gem.FROST.cooldownSeconds(lvl), true)) return;
        double r = lvl >= 5 ? 6 : 4;
        int seconds = lvl >= 5 ? 4 : 3;
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (en instanceof Player t && !t.equals(p)) {
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * seconds, 1));
                t.setFreezeTicks(t.getMaxFreezeTicks());
            }
        }
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 120, r / 2, 0.8, r / 2, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);
    }

    private void fireball(Player p, int lvl) {
        if (!ready(p, Gem.FIRE, Gem.FIRE.cooldownSeconds(lvl), true)) return;
        SmallFireball fb = p.launchProjectile(SmallFireball.class, p.getEyeLocation().getDirection().multiply(1.2));
        fb.setIsIncendiary(false);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
    }
}
