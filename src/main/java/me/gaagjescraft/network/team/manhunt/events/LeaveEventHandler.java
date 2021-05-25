package me.gaagjescraft.network.team.manhunt.events;

import me.gaagjescraft.network.team.manhunt.Manhunt;
import me.gaagjescraft.network.team.manhunt.games.Game;
import me.gaagjescraft.network.team.manhunt.utils.Util;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LeaveEventHandler implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
        Game game = Game.getGame(e.getPlayer());
        if (game == null) return;

        game.removePlayer(e.getPlayer());

        Bukkit.getScheduler().runTaskLater(Manhunt.get(), () -> {
            // removing the user after 5 minutes if they haven't rejoined yet.
            Player p = Bukkit.getPlayer(e.getPlayer().getUniqueId());
            if (p == null || !p.isOnline()) {
                Manhunt.get().getPlayerStorage().unloadUser(e.getPlayer().getUniqueId());
            }
        }, 20 * 300L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);

        Manhunt.get().getPlayerStorage().loadUser(e.getPlayer().getUniqueId());

        if (Manhunt.get().getCfg().joinGameOnServerJoin || Manhunt.get().getCfg().teleportLobbyOnServerJoin) {
            Bukkit.getScheduler().runTaskLater(Manhunt.get(), () -> {
                if (Manhunt.get().getCfg().joinGameOnServerJoin) {
                    Game game = Game.getGame(e.getPlayer());
                    if (game == null) {
                        for (Game g : Game.getGames()) {
                            if (g.addPlayer(e.getPlayer())) {
                                for (String s : Manhunt.get().getCfg().autoRejoinMessage) {
                                    e.getPlayer().sendMessage(Util.c(s.replaceAll("%host%", g.getIdentifier())));
                                }
                                return;
                            }
                        }
                    } else {
                        if (!game.getPlayer(e.getPlayer()).isOnline()) {
                            if (game.addPlayer(e.getPlayer())) {
                                e.getPlayer().sendMessage(Util.c(Manhunt.get().getCfg().playerRejoinedMessage));
                                return;
                            }
                        }
                    }
                }
                if (Manhunt.get().getCfg().teleportLobbyOnServerJoin && Manhunt.get().getCfg().lobby != null) {
                    e.getPlayer().teleport(Manhunt.get().getCfg().lobby);
                }
            }, 10L);
        }

    }

}
