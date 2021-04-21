package me.gaagjescraft.network.team.manhunt.games;

import com.google.common.collect.Lists;
import me.gaagjescraft.network.team.manhunt.Manhunt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GameScheduler {

    private Game game;

    GameScheduler(Game game) {
        this.game = game;
    }

    public void start() {
        game.setTimer(0);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (GamePlayer gp : game.getOnlinePlayers(null)) {
                    gp.updateScoreboard();
                    gp.updateHealthTag();
                    if (gp.getTracking() != null) gp.setTracking(gp.getTracking());
                }

                // todo add automatic start for minimum amount of players.
                if (game.getStatus() == GameStatus.STARTING) {
                    doStartingCountdown();
                    if (game.getTimer() == 10) {
                        game.setStatus(GameStatus.PLAYING);
                        game.setTimer(0);
                        game.getRunnerTeleporterMenu().update();
                        return;
                    }
                    game.setTimer(game.getTimer() + 1);
                } else if (game.getStatus() == GameStatus.PLAYING) {
                    doHuntersReleaseCountdown();
                    if (game.getTimer() == game.getNextEventTime() && game.isTwistsAllowed()) {
                        doEvent();
                    }

                    if (game.isEventActive() && game.getSelectedTwist() == TwistVote.ACID_RAIN && game.isTwistsAllowed()) {
                        doAcidRainEvent();
                    }

                    game.setTimer(game.getTimer() + 1);
                } else if (game.getStatus() == GameStatus.STOPPING) {
                    this.cancel();
                }
            }
        }.runTaskTimer(Manhunt.get(), 0L, 20L);
    }

    public void end() {
        this.game.setTimer(10);

        new BukkitRunnable() {
            @Override
            public void run() {
                List<GamePlayer> online = game.getOnlinePlayers(null);
                for (GamePlayer gp : online) {
                    gp.updateScoreboard();
                }

                Location loc = Manhunt.get().getLobby();
                for (GamePlayer gp : online) {
                    Player player = Bukkit.getPlayer(gp.getUuid());
                    if (player == null) continue;
                    gp.updateScoreboard();
                    player.setLevel(game.getTimer());
                    player.setExp(game.getTimer() > 10 ? 1.0f : (game.getTimer() / 10f));

                    if (game.getTimer() == 0) {
                        player.sendMessage("§bThanks for playing Manhunt on ExodusMC!");
                        player.teleport(loc);
                        gp.restoreForLobby();
                    }
                }

                if (game.getTimer() == 0) {
                    this.cancel();
                    game.delete();
                    return;
                }

                game.setTimer(game.getTimer() - 1);
            }
        }.runTaskTimer(Manhunt.get(), 0, 20L);
    }

    private void doHuntersReleaseCountdown() {
        int timer = game.getTimer();
        String mainTitle = "§a";
        if (timer == game.getHeadStart().getSeconds() - 2) {
            mainTitle = "§e";
        } else if (timer == game.getHeadStart().getSeconds() - 1) {
            mainTitle = "§c";
        }

        List<GamePlayer> online = game.getOnlinePlayers(null);

        // announce time at 60s, 30s, 10s, <5s
        int headstart = game.getHeadStart().getSeconds();
        if ((headstart >= 120 && timer == headstart - 120) || (headstart >= 90 && timer == headstart - 90) || (headstart >= 60 && timer == headstart - 60) || (headstart >= 30 && timer == headstart - 30) || (headstart >= 10 && timer == headstart - 10) || (timer >= headstart - 5 && timer < headstart)) {
            for (GamePlayer gp : online) {
                Player p = Bukkit.getPlayer(gp.getUuid());
                if (p == null) continue;
                p.sendMessage("§7Hunters will be released in §e" + Manhunt.get().getUtil().secondsToTimeString(headstart - timer, "string") + "§7!");
                if (gp.getPlayerType() == PlayerType.HUNTER) {
                    if (timer > headstart - 6)
                        p.sendTitle(mainTitle + "§l" + (game.getHeadStart().getSeconds() - timer) + "", "§7You will be released shortly!", 5, 10, 5);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                }
            }
        }
        if (timer == game.getHeadStart().getSeconds()) {
            game.getSchematic().unload();

            List<GamePlayer> runners = game.getOnlinePlayers(PlayerType.RUNNER);

            for (GamePlayer gp : online) {
                Player p = Bukkit.getPlayer(gp.getUuid());
                if (p == null) continue;
                p.sendMessage("§8§m--------------------------");
                p.sendMessage("§cThe hunters have been released from sky!");
                p.sendMessage("§8§m--------------------------");
                if (gp.getPlayerType() == PlayerType.HUNTER) {
                    p.sendTitle("§e§lRELEASED!", "§aYou have been released. Go get 'em!", 10, 50, 10);
                } else {
                    p.sendTitle("§c§lWATCH OUT!", "§eThe hunters have been released!", 10, 50, 10);
                }

                if (gp.getPlayerType() == PlayerType.HUNTER) {
                    gp.prepareForGame(GameStatus.PLAYING);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1, 1);

                if (gp.getPlayerType() == PlayerType.HUNTER) {
                    int random = ThreadLocalRandom.current().nextInt(runners.size());
                    gp.setTracking(runners.get(random));
                }
            }
        }
    }

    private void doStartingCountdown() {
        int timer = game.getTimer();
        String mainTitle = "§a";
        if (timer == 8) {
            mainTitle = "§e";
        } else if (timer == 9) {
            mainTitle = "§c";
        }

        if (timer == 10) {
            game.selectTwist();
        }

        for (GamePlayer gp : game.getOnlinePlayers(null)) {
            Player p = Bukkit.getPlayer(gp.getUuid());
            if (p == null) continue;

            if (timer == 0 || (timer >= 5 && timer < 10)) {
                p.sendMessage("§eGame is starting in " + mainTitle + (10 - timer) + " seconds!");
                p.sendTitle(mainTitle + "§l" + (10 - timer) + "", "§7Starting soon!", 5, 10, 5);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
            } else if (timer == 10) {
                p.sendMessage("§8§m--------------------------");
                p.sendMessage("§aThe game has started!");
                p.sendMessage("§bThe speedrunners will have a §c" + Manhunt.get().getUtil().secondsToTimeString(game.getHeadStart().getSeconds(), "string") + "§b headstart.");
                p.sendMessage(gp.getPlayerType() == PlayerType.HUNTER ? "§bYou will be released in §c" + Manhunt.get().getUtil().secondsToTimeString(game.getHeadStart().getSeconds(), "string") + "§b!" : "§bYou have §c" + Manhunt.get().getUtil().secondsToTimeString(game.getHeadStart().getSeconds(), "string") + "§b to prepare for the hunters!");

                if (game.isTwistsAllowed()) {
                    p.sendMessage("§8§m--------------------------");
                    p.sendMessage(null, "§eThe twist §a§l" + game.getSelectedTwist().getDisplayName() + "§e won with §6" + game.getTwistVotes(game.getSelectedTwist()) + " votes§e!");
                }
                p.sendMessage("§8§m--------------------------");

                if (gp.getPlayerType() == PlayerType.HUNTER) {
                    p.sendTitle("§a§lGAME HAS STARTED!", "§bThe speedrunners have a §e" + Manhunt.get().getUtil().secondsToTimeString(game.getHeadStart().getSeconds(), "string") + "§b headstart.", 10, 50, 10);
                    p.getInventory().setItem(0, null);
                } else {
                    p.sendTitle("§a§lGAME HAS STARTED!", "§bHunters will be released in §e"+Manhunt.get().getUtil().secondsToTimeString(game.getHeadStart().getSeconds(), "string")+"§b!", 10, 50, 10);
                    gp.prepareForGame(GameStatus.PLAYING);
                    p.teleport(game.getWorld().getSpawnLocation());
                }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2);
            }
        }
    }

    public void doEvent() {
        if (game.getSelectedTwist() == TwistVote.RANDOM_YEET) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (GamePlayer gp : game.getOnlinePlayers(null)) {
                Player player = Bukkit.getPlayer(gp.getUuid());
                if (player == null) continue;
                player.sendMessage("§aIt's time for a random yeet!");
                if (!gp.isDead()) {
                    player.setVelocity(new Vector(random.nextDouble(-5, 5.1), random.nextDouble(1, 2.3), random.nextDouble(-5, 5.1)));
                    player.sendTitle("§9§lYEET!", "§cYou got yeeted into the air!", 20, 50, 20);
                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1);
                    Bukkit.getScheduler().runTaskLater(Manhunt.get(), () -> player.stopSound(Sound.ITEM_ELYTRA_FLYING), 25L);
                }
            }
            game.determineNextEventTime();
        } else if (game.getSelectedTwist() == TwistVote.SPEED_BOOST) {
            game.setEventActive(true);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int rand = random.nextInt(1,4);

            StringBuilder a = new StringBuilder();
            for (int i = 0; i<rand; i++){
                a.append("I");
            }

            for (GamePlayer gp : game.getOnlinePlayers(null)) {
                Player player = Bukkit.getPlayer(gp.getUuid());
                if (player == null) continue;

                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 1, 1);
                player.sendTitle("§b§lSPEED!", "§aThe runners received speed for §e20 seconds§a!", 20, 50, 20);
                player.sendMessage("§bSpeed " + a.toString() + " has been applied to the runners for §e20 seconds§b!");
                if (gp.getPlayerType() == PlayerType.RUNNER && !gp.isFullyDead()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, rand));
                }
            }
            Bukkit.getScheduler().runTaskLater(Manhunt.get(),()->{
                game.setEventActive(false);
                game.determineNextEventTime();
            }, 400);
        } else if (game.getSelectedTwist() == TwistVote.BLINDNESS) {
            game.setEventActive(true);
            for (GamePlayer gp : game.getOnlinePlayers(null)) {
                Player player = Bukkit.getPlayer(gp.getUuid());
                if (player == null) continue;

                player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.1f, 1f);
                player.sendTitle("§5§lBLINDNESS!", "§cHunters are blinded for §e10 seconds§c!", 20, 50, 20);
                player.sendMessage("§5All hunters have been blinded for §e10 seconds§5!");
                if (gp.getPlayerType() == PlayerType.HUNTER && !gp.isFullyDead()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 1));
                }
            }
            Bukkit.getScheduler().runTaskLater(Manhunt.get(), () -> {
                game.setEventActive(false);
                game.determineNextEventTime();
            }, 200);
        } else if (game.getSelectedTwist() == TwistVote.ACID_RAIN) {
            game.setEventActive(true);
            game.getWorld().setStorm(true);
            game.getWorld().setThundering(true);
            for (GamePlayer gp : game.getOnlinePlayers(null)) {
                Player player = Bukkit.getPlayer(gp.getUuid());
                if (player == null) continue;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1, 1);
                player.sendMessage("§2Acid rain incoming! Seek shelter...");
                player.sendTitle("§2§lACID RAIN!", "§cYou better seek shelter!", 20, 50, 20);
            }

            Bukkit.getScheduler().runTaskLater(Manhunt.get(), () -> {
                game.sendMessage(null, "§cIt's safe outside again. Acid rain has ended!");
                game.setEventActive(false);
                game.determineNextEventTime();
                if (game.getWorld() != null) {
                    game.getWorld().setStorm(false);
                    game.getWorld().setThundering(false);
                }
            }, 600);
        } else if (game.getSelectedTwist() == TwistVote.HARDCORE) {
            game.setEventActive(true);
            for (GamePlayer gp : game.getOnlinePlayers(null)) {
                Player player = Bukkit.getPlayer(gp.getUuid());
                if (player == null) continue;
                if (!gp.isFullyDead()) player.setHealth(4);
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1, 1);
                player.sendMessage("§c§lHARDCORE activated! §cEvery player has been cursed with 2 hearts and no regeneration for 30 seconds!");
                player.sendTitle("§c§lHARDCORE!", "§7You have 2 hearts and won't regen for 30s!", 20, 50, 20);
            }

            Bukkit.getScheduler().runTaskLater(Manhunt.get(), () -> {
                game.setEventActive(false);
                game.determineNextEventTime();
                for (GamePlayer gp : game.getOnlinePlayers(null)) {
                    Player player = Bukkit.getPlayer(gp.getUuid());
                    if (player == null) continue;
                    player.setHealth(player.getMaxHealth());
                    player.sendMessage("§cHardcore mode has ended. Everyone's health has been restored and you can now regenerate health again!");
                }
            }, 600);
        }
    }

    public void doAcidRainEvent() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> addedLocations = Lists.newArrayList();

        Bukkit.getScheduler().runTaskAsynchronously(Manhunt.get(), () -> {
            for (GamePlayer gp : game.getOnlinePlayers(null)) {
                if (gp.isDead()) continue;
                Player player = Bukkit.getPlayer(gp.getUuid());
                if (!gp.isFullyDead() && player.getLocation().getY() + 1 > player.getWorld().getHighestBlockYAt(player.getLocation())) {
                    double damage = player.getInventory().getHelmet() != null ? 0.5 : 1.0;
                    Bukkit.getScheduler().runTask(Manhunt.get(), () -> {
                        player.damage(damage);
                        player.setLastDamageCause(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.CUSTOM, damage));
                    });
                    Manhunt.get().getUtil().spawnAcidParticles(player.getLocation().add(0, 1, 0), true);
                }

                for (int i = 0; i < 30; i++) {
                    int x = random.nextInt(-20, 20);
                    int z = random.nextInt(-20, 20);

                    if (!addedLocations.contains(x + ":" + z)) {
                        addedLocations.add(x + ":" + z);
                        Location loc = player.getWorld().getHighestBlockAt(player.getLocation().add(x, 0, z)).getLocation();
                        Manhunt.get().getUtil().spawnAcidParticles(loc, false);
                    }
                }
            }
        });

    }

}
