package org.curryman;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WandListener implements Listener {
    private final CleverRegen plugin;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public WandListener(CleverRegen plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerUseWand(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.STONE_AXE) {
            ItemMeta meta = item.getItemMeta();
            String wandName = plugin.getConfig().getString("wand-name", "&aCleverRegen Wand");

            if (meta != null && meta.getDisplayName() != null &&
                    ChatColor.stripColor(meta.getDisplayName()).equals(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', wandName)))) {

                event.setCancelled(true);

                if (event.getAction().toString().contains("LEFT_CLICK")) {
                    pos1.put(player.getUniqueId(), player.getLocation());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.pos1-set", "&aPosition 1 set!")));
                } else if (event.getAction().toString().contains("RIGHT_CLICK")) {
                    pos2.put(player.getUniqueId(), player.getLocation());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.pos2-set", "&aPosition 2 set!")));
                }
            }
        }
    }

    public Location getPos1(Player player) {
        return pos1.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2.get(player.getUniqueId());
    }
}