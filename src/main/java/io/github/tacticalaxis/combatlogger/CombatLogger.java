package io.github.tacticalaxis.combatlogger;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"ConstantConditions", "NullableProblems", "DuplicatedCode"})
public class CombatLogger extends JavaPlugin implements Listener {

    private static CombatLogger instance;

    private static WorldGuardPlugin worldGuard;

    private static ConcurrentHashMap<Player, BossBar> bars;

    private static ConcurrentHashMap<Player, Long> timer;

    private static ConcurrentHashMap<Player, Player> combat;

    public static CombatLogger getInstance() {
        return instance;
    }

    private static boolean playerCanBeReleased(Player player) {
        if (timer.containsKey(player)) {
            return System.currentTimeMillis() >= timer.get(player);
        }
        return false;
    }

    @Override
    public void onEnable() {
        instance = this;
        try {
            getWorldGuard();
        } catch (Exception e) {
            Bukkit.getLogger().severe(ChatColor.RED + "Worldguard not present! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        ConfigurationManager.getInstance().setupConfiguration();
        getCommand("combatlogger").setExecutor(this);
        bars = new ConcurrentHashMap<>();
        timer = new ConcurrentHashMap<>();
        combat = new ConcurrentHashMap<>();
        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : timer.keySet()) {
                    if (playerCanBeReleased(player)) {
                        try {
                            if (config().getBoolean("logging-messages", true)) {
                                String message = ChatColor.translateAlternateColorCodes('&', config().getString("logging-message-leave"));
                                player.sendMessage(message.replace("%target%", combat.get(player).getName()));
                            }
                        } catch (Exception ignored) {}
                        removePlayer(player);
                    } else {
                        try {
                            bars.get(player).setProgress(((timer.get(player) - (double) System.currentTimeMillis()) / 1000) / config().getLong("logging-time", 10));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }.runTaskTimer(this, 5L, 1L);
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    @EventHandler
    public void onLeave(PlayerMoveEvent event) {
        if (config().getBoolean("release-on-left-zone", true)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (isPVPzone(from) && !isPVPzone(to)) {
                if (timer.containsKey(event.getPlayer())) {
                    Player other = combat.get(event.getPlayer());
                    removePlayer(event.getPlayer());
                    if (config().getBoolean("logging-messages", true)) {
                        String message = ChatColor.translateAlternateColorCodes('&', config().getString("logging-message-leave"));
                        event.getPlayer().sendMessage(message.replace("%target%", other.getName()));
                        other.sendMessage(message.replace("%target%", event.getPlayer().getName()));
                    }
                }
            }
        }
    }

    private boolean isPVPzone(Location location) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(location.getWorld()));
        for (String r : config().getStringList("blacklist-zones")) {
            ProtectedRegion region = regionManager.getRegion(r);
            if (region != null) {
                if (region.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        ConfigurationManager.getInstance().reloadConfiguration();
        sender.sendMessage(ChatColor.GREEN + "CombatLogger configuration reloaded!");
        return true;
    }

    private static FileConfiguration config() {
        return ConfigurationManager.getInstance().getConfiguration(ConfigurationManager.CONFIG_MAIN);
    }

    @EventHandler
    public void cmd(PlayerCommandPreprocessEvent event) {
        if (timer.containsKey(event.getPlayer())) {
            String used = event.getMessage().substring(1);
            List<String> deny = config().getStringList("denied-commands");
            for (String command : deny) {
                StringBuilder current = new StringBuilder();
                for (int i = 1; i <= used.split(" ").length; i++) {
                    current.append(used.split(" ")[i - 1]).append(" ");
                    System.out.println(current.toString().toLowerCase().trim());
                    if (command.toLowerCase().trim().equals(current.toString().toLowerCase().trim())) {
                        event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', config().getString("command-disabled-message")));
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        if (timer.containsKey(event.getPlayer())) {
            if (timer.containsKey(event.getPlayer())) {
                Player other = combat.get(event.getPlayer());
                removePlayer(event.getPlayer());
                if (config().getBoolean("logging-messages", true)) {
                    String message = ChatColor.translateAlternateColorCodes('&', config().getString("logging-message-leave"));
                    event.getPlayer().sendMessage(message.replace("%target%", other.getName()));
                    other.sendMessage(message.replace("%target%", event.getPlayer().getName()));
                }
                if (!playerCanBeReleased(event.getPlayer())) {
                    event.getPlayer().setHealth(0);
                }
            }
        }
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        if (timer.containsKey(event.getEntity())) {
            Player other = combat.get(event.getEntity());
            removePlayer(event.getEntity());
            if (config().getBoolean("logging-messages", true)) {
                String message = ChatColor.translateAlternateColorCodes('&', config().getString("logging-message-leave"));
                event.getEntity().sendMessage(message.replace("%target%", other.getName()));
                other.sendMessage(message.replace("%target%", event.getEntity().getName()));
            }
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player player = (Player) event.getEntity();
            if (isPVPzone(player.getLocation()) && isPVPzone(damager.getLocation())) {


                if (combat.get(damager) != player || combat.get(player) != damager) {
                        removePlayer(damager);
                        removePlayer(player);
                    timer.put(player, System.currentTimeMillis() + (1000 * (config().getLong("logging-time", 10))));
                    timer.put(damager, System.currentTimeMillis() + (1000 * (config().getLong("logging-time", 10))));
                    BarColor colour;
                    try {
                        colour = BarColor.valueOf(config().getString("bar-colour"));
                    } catch (Exception e) {
                        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Bossbar colour " + ChatColor.GOLD + (config().getString("bar-colour", "null")).toUpperCase() + ChatColor.RED + " does not exist!");
                        colour = BarColor.RED;
                    }
                    setBar(player, damager, colour);
                    combat.put(player, damager);

                    setBar(damager, player, colour);
                    combat.put(damager, player);

                    if (config().getBoolean("logging-messages", true)) {
                        String message = ChatColor.translateAlternateColorCodes('&', config().getString("logging-message-enter"));
                        player.sendMessage(message.replace("%target%", damager.getName()));
                        damager.sendMessage(message.replace("%target%", player.getName()));
                    }
                } else {
                    timer.remove(player);
                    timer.remove(damager);
                    timer.put(player, System.currentTimeMillis() + (1000 * (config().getLong("logging-time", 10))));
                    timer.put(damager, System.currentTimeMillis() + (1000 * (config().getLong("logging-time", 10))));
                }
            }
        }
    }

    private void setBar(Player damager, Player player, BarColor colour) {
        BossBar damagerBar = Bukkit.createBossBar(ChatColor.translateAlternateColorCodes('&', config().getString("bar-text").replace("%target%",player.getName()).replace("%player%",damager.getName())), colour , BarStyle.SEGMENTED_10, BarFlag.PLAY_BOSS_MUSIC);
        damagerBar.setProgress(1);
        damagerBar.addPlayer(damager);
        bars.put(damager, damagerBar);
    }

    public static WorldGuardPlugin getWorldGuard() {
        Plugin plugin = instance.getServer().getPluginManager().getPlugin("WorldGuard");
        if (!(plugin instanceof WorldGuardPlugin)) {
            return null;
        }
        return (WorldGuardPlugin) plugin;
    }

    private void removePlayer(Player player) {
        timer.remove(player);
        if (bars.containsKey(player)) {
            bars.get(player).removeAll();
        }
        bars.remove(player);
        combat.remove(player);
    }
}