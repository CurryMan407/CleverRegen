package org.curryman;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class PushPlayers implements Listener {

    @EventHandler
    public void onPlayerSuffocating(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
                pushPlayerUpwards(player);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isSuffocating(player)) {
            pushPlayerUpwards(player);
        }
    }

    private boolean isSuffocating(Player player) {
        return player.getLocation().getBlock().getType().isSolid();
    }

    private void pushPlayerUpwards(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isSuffocating(player)) {
                    player.setVelocity(player.getVelocity().setY(1.0));
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(getClass()), 1L, 1L);
    }
}
