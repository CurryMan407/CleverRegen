package org.curryman;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.BlockState;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class CleverRegen extends JavaPlugin implements Listener {

    private WandListener wandListener;
    private final Map<String, Map<String, BlockState>> regionBlocks = new HashMap<>();
    private final Map<String, Integer> regionTasks = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wandListener = new WandListener(this);
        getServer().getPluginManager().registerEvents(wandListener, this);

        new File(getDataFolder(), "Regions").mkdirs();
        loadAllRegions();

        getCommand("cr").setExecutor(this);
        getCommand("cr").setTabCompleter(new CommandTabCompleter());

        getLogger().info(ChatColor.GREEN + "CleverRegen has been enabled!");
    }

    @Override
    public void onDisable() {
        saveAllRegions();
        getLogger().info(ChatColor.RED + "CleverRegen has been disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (command.getName().equalsIgnoreCase("cr")) {
                if (args.length > 0) {
                    switch (args[0].toLowerCase()) {
                        case "wand":
                            giveWand(player);
                            return true;
                        case "save":
                            if (args.length > 1) {
                                saveRegion(player, args[1]);
                                return true;
                            }
                            break;
                        case "reload":
                            reloadAllConfigs();
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aCleverRegen config and regions reloaded."));
                            return true;
                        case "forceregen":
                            if (args.length > 1) {
                                forceRegenerate(player, args[1]);
                                return true;
                            }
                            break;
                        case "delete":
                            if (args.length > 1) {
                                deleteRegion(player, args[1]);
                                return true;
                            }
                            break;
                        case "list":
                            listRegions(player);
                            return true;
                        case "tp":
                            if (args.length > 1) {
                                teleportToRegion(player, args[1]);
                                return true;
                            }
                            break;
                        case "help":
                            displayHelp(player);
                            return true;
                        default:
                            displayHelp(player);
                            return true;
                    }
                } else {
                    displayHelp(player);
                    return true;
                }
            }
        }
        return false;
    }

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.STONE_AXE);
        ItemMeta meta = wand.getItemMeta();
        String wandName = getConfig().getString("wand-name", "&aCleverRegen Wand");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', wandName));
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.wand-given", "&aYou have been given the CleverRegen wand!")));
    }

    private void saveRegion(Player player, String regionName) {
        Location pos1 = wandListener.getPos1(player);
        Location pos2 = wandListener.getPos2(player);

        if (pos1 != null && pos2 != null) {
            File regionFile = new File(getDataFolder(), "Regions/" + regionName + ".yml");
            FileConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFile);

            regionConfig.set("region.pos1.x", pos1.getBlockX());
            regionConfig.set("region.pos1.y", pos1.getBlockY());
            regionConfig.set("region.pos1.z", pos1.getBlockZ());
            regionConfig.set("region.pos2.x", pos2.getBlockX());
            regionConfig.set("region.pos2.y", pos2.getBlockY());
            regionConfig.set("region.pos2.z", pos2.getBlockZ());
            regionConfig.set("world", pos1.getWorld().getName());
            regionConfig.set("delay", 60);

            try {
                regionConfig.save(regionFile);
                saveRegionBlocksToMemory(regionName, pos1, pos2);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aRegion " + regionName + " saved!"));
                scheduleSingleRegionRegeneration(regionName, regionConfig);
            } catch (IOException e) {
                e.printStackTrace();
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cFailed to save the region."));
            }
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou must set both positions first."));
        }
    }

    private void saveRegionBlocksToMemory(String regionName, Location pos1, Location pos2) {
        Map<String, BlockState> blocks = new HashMap<>();

        int xMin = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int xMax = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int yMin = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int yMax = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int zMin = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int zMax = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    Location loc = new Location(pos1.getWorld(), x, y, z);
                    blocks.put(x + "," + y + "," + z, loc.getBlock().getState());
                }
            }
        }

        regionBlocks.put(regionName, blocks);
    }

    private void regenerateRegion(String regionName, boolean useDelay) {
        File regionFile = new File(getDataFolder(), "Regions/" + regionName + ".yml");
        if (!regionFile.exists()) {
            getLogger().log(Level.WARNING, "Region file " + regionName + ".yml does not exist.");
            return;
        }

        FileConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFile);
        String worldName = regionConfig.getString("world");
        if (worldName == null) {
            getLogger().log(Level.WARNING, "World name is not specified in " + regionName + ".yml.");
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().log(Level.WARNING, "World " + worldName + " is not loaded or does not exist.");
            return;
        }

        Location pos1 = new Location(world, regionConfig.getInt("region.pos1.x"), regionConfig.getInt("region.pos1.y"), regionConfig.getInt("region.pos1.z"));
        Location pos2 = new Location(world, regionConfig.getInt("region.pos2.x"), regionConfig.getInt("region.pos2.y"), regionConfig.getInt("region.pos2.z"));

        Map<String, BlockState> blocks = regionBlocks.get(regionName);
        if (blocks == null) {
            getLogger().log(Level.WARNING, "No blocks stored in memory for region " + regionName);
            return;
        }

        if (useDelay) {
            int delay = regionConfig.getInt("delay", 60);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                regenerateBlocks(pos1, pos2, blocks, regionConfig.getString("type", "all"), () -> {
                    pushPlayersUpwards(pos1, pos2);
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.region-regenerated", "&aRegion {region} has been regenerated!")).replace("{region}", regionName));
                });
            }, delay * 20L);
        } else {
            regenerateBlocks(pos1, pos2, blocks, regionConfig.getString("type", "all"), () -> {
                pushPlayersUpwards(pos1, pos2);
            });
        }
    }

    private void regenerateBlocks(Location pos1, Location pos2, Map<String, BlockState> blocks, String type, Runnable callback) {
        int xMin = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int xMax = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int yMin = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int yMax = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int zMin = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int zMax = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        new BukkitRunnable() {
            @Override
            public void run() {
                for (int x = xMin; x <= xMax; x++) {
                    for (int y = yMin; y <= yMax; y++) {
                        for (int z = zMin; z <= zMax; z++) {
                            String key = x + "," + y + "," + z;
                            if (blocks.containsKey(key)) {
                                BlockState blockState = blocks.get(key);
                                blockState.getLocation().getBlock().setType(blockState.getType(), false);
                                blockState.update(true, false);
                            }
                        }
                    }
                }
                callback.run();
            }
        }.runTask(this);
    }

    private void pushPlayersUpwards(Location pos1, Location pos2) {
        int xMin = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int xMax = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int yMin = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int yMax = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int zMin = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int zMax = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation();
            if (loc.getBlockX() >= xMin && loc.getBlockX() <= xMax && loc.getBlockY() >= yMin && loc.getBlockY() <= yMax && loc.getBlockZ() >= zMin && loc.getBlockZ() <= zMax) {
                while (loc.getBlock().getType() != Material.AIR) {
                    loc.add(0, 1, 0);
                }
                player.teleport(loc);
            }
        }
    }
    private void forceRegenerate(Player player, String regionName) {
        if (regionBlocks.containsKey(regionName)) {
            regenerateRegion(regionName, false);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.region-regenerated", "&6Clever&eRegen &f» &aRegion {region} has been regenerated!")));

            String actionBarMessage = getConfig().getString("messages.actionbar-message", "&6Region &e%region% &6regenerated successfully");

            actionBarMessage = ChatColor.translateAlternateColorCodes('&', actionBarMessage.replace("%region%", regionName));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMessage));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cRegion " + regionName + " does not exist."));
        }
    }


    private void deleteRegion(Player player, String regionName) {
        File regionFile = new File(getDataFolder(), "Regions/" + regionName + ".yml");
        if (regionFile.exists() && regionFile.delete()) {

            Integer taskId = regionTasks.remove(regionName);
            if (taskId != null) {
                Bukkit.getScheduler().cancelTask(taskId);
            }


            regionBlocks.remove(regionName);

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aRegion " + regionName + " deleted."));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cFailed to delete the region " + regionName + "."));
        }
    }

    private void listRegions(Player player) {
        File regionsFolder = new File(getDataFolder(), "Regions");
        String[] regionFiles = regionsFolder.list((dir, name) -> name.endsWith(".yml"));

        if (regionFiles != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aSaved regions:"));
            for (String regionFile : regionFiles) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e- " + regionFile.replace(".yml", "")));
            }
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo regions found."));
        }
    }

    private void teleportToRegion(Player player, String regionName) {
        File regionFile = new File(getDataFolder(), "Regions/" + regionName + ".yml");
        if (!regionFile.exists()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cRegion " + regionName + " does not exist."));
            return;
        }

        FileConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFile);
        String worldName = regionConfig.getString("world");

        if (worldName == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cWorld not specified in the region file."));
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cWorld " + worldName + " is not loaded or does not exist."));
            return;
        }

        Location pos1 = new Location(world, regionConfig.getInt("region.pos1.x"), regionConfig.getInt("region.pos1.y"), regionConfig.getInt("region.pos1.z"));
        Location pos2 = new Location(world, regionConfig.getInt("region.pos2.x"), regionConfig.getInt("region.pos2.y"), regionConfig.getInt("region.pos2.z"));

        Location teleportLocation = pos1.clone().add(pos2).multiply(0.5);
        teleportLocation.setY(world.getHighestBlockYAt(teleportLocation));

        player.teleport(teleportLocation);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aTeleported to region " + regionName + "."));
    }

    private void displayHelp(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6CleverRegen Commands:"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/cr wand &f- Give yourself the CleverRegen wand."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/cr save <regionName> &f- Save a region."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/cr reload &f- Reload the config and regions."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/cr forceregen <regionName> &f- Forcefully regenerate a region."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/cr delete <regionName> &f- Delete a region."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/cr list &f- List all saved regions."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/cr tp <regionName> &f- Teleport to a region."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/cr help &f- Display this help message."));
    }

    private void reloadAllConfigs() {
        reloadConfig();
        saveDefaultConfig();
        loadAllRegions();
    }

    private void loadAllRegions() {
        File regionsFolder = new File(getDataFolder(), "Regions");
        File[] regionFiles = regionsFolder.listFiles((dir, name) -> name.endsWith(".yml"));

        if (regionFiles != null) {
            for (File regionFile : regionFiles) {
                FileConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFile);
                String regionName = regionFile.getName().replace(".yml", "");
                scheduleSingleRegionRegeneration(regionName, regionConfig);
                loadRegionBlocksIntoMemory(regionName, regionConfig);
            }
        }
    }

    private void loadRegionBlocksIntoMemory(String regionName, FileConfiguration regionConfig) {
        String worldName = regionConfig.getString("world");
        if (worldName == null) {
            getLogger().log(Level.WARNING, "World name is not specified in " + regionName + ".yml.");
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().log(Level.WARNING, "World " + worldName + " is not loaded or does not exist.");
            return;
        }

        Location pos1 = new Location(world, regionConfig.getInt("region.pos1.x"), regionConfig.getInt("region.pos1.y"), regionConfig.getInt("region.pos1.z"));
        Location pos2 = new Location(world, regionConfig.getInt("region.pos2.x"), regionConfig.getInt("region.pos2.y"), regionConfig.getInt("region.pos2.z"));

        saveRegionBlocksToMemory(regionName, pos1, pos2);
    }
    private void saveAllRegions() {
        for (Map.Entry<String, Map<String, BlockState>> entry : regionBlocks.entrySet()) {
            String regionName = entry.getKey();
            File regionFile = new File(getDataFolder(), "Regions/" + regionName + ".yml");
            FileConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFile);

            try {
                regionConfig.save(regionFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void scheduleSingleRegionRegeneration(String regionName, FileConfiguration regionConfig) {
        int delay = regionConfig.getInt("delay", 60);
        int interval = delay * 20;

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> regenerateRegion(regionName, true), interval, interval);
        regionTasks.put(regionName, taskId);
    }

    private class CommandTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (command.getName().equalsIgnoreCase("cr")) {
                List<String> completions = new ArrayList<>();
                if (args.length == 1) {
                    completions.add("wand");
                    completions.add("save");
                    completions.add("reload");
                    completions.add("forceregen");
                    completions.add("delete");
                    completions.add("list");
                    completions.add("tp");
                    completions.add("help");
                } else if (args.length == 2 && (args[0].equalsIgnoreCase("forceregen") || args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("tp"))) {
                    File regionsFolder = new File(getDataFolder(), "Regions");
                    String[] regionFiles = regionsFolder.list((dir, name) -> name.endsWith(".yml"));

                    if (regionFiles != null) {
                        for (String regionFile : regionFiles) {
                            completions.add(regionFile.replace(".yml", ""));
                        }
                    }
                }
                return completions;
            }
            return null;
        }
    }
}