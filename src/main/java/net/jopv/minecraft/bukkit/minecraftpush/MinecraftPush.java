package net.jopv.minecraft.bukkit.minecraftpush;

import net.pushover.client.MessagePriority;
import net.pushover.client.PushoverException;
import net.pushover.client.PushoverMessage;
import net.pushover.client.PushoverRestClient;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * @author Jop Vernooij, Jeff Alexander
 */
public class MinecraftPush extends JavaPlugin implements Listener {
    protected PushoverRestClient pushoverRestClient = new PushoverRestClient();

    private FileConfiguration userKeysFileConfiguration = null;
    private File userKeysFile = null;

    public int invalidUsers;
    public int bannedUsers;

    public String titleEnd = "";

    boolean canPush = true;
    boolean canCheckOfflinePlayers = true;

    protected Map<Player,Long> timeLog = new HashMap<>();

    @Override
    public void onEnable() {
        // Online mode check
        if (!getServer().getOnlineMode()) {
            getLogger().severe("Server is in offline mode! Can't send notifications.");
            canPush = false;
            return;
        }

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // User count
        int users = getUserKeysFileConfiguration().getKeys(false).size();
        int bannedUsers = getAmountOfBannedUsers();
        if (bannedUsers == 0) {
            if (users != 1)
                getLogger().info(Integer.toString(users) + " users registered.");
            else
                getLogger().info("1 user registered.");
        } else {
            if (users != 1)
                getLogger().info(Integer.toString(users) + " users registered, of which " + Integer.toString(bannedUsers) + " are banned.");
            else
                getLogger().info("1 user registered, which is banned.");
        }

        // Invalid-user count
        int invalidUsers = getAmountOfInvalidUsers();
        if (invalidUsers > 0) {
            if (invalidUsers != 1)
                getLogger().warning(Integer.toString(invalidUsers) + " users have apparently entered invalid Pushover user keys! They will not receive any notifications. Their user keys will be deleted as soon as they connect with the server again, so they can be warned.");
            else
                getLogger().warning("1 user has apparently entered an invalid Pushover user key! It will not receive any notifications. It's user key will be deleted as soon as it connects with the server again, so it can be warned.");
        }

        // Server name
        if (getServer().getName().equals("Unknown Server"))
            getLogger().info("Your Minecraft is unnamed. If your Minecraft has a name, you should set it in server.properties, and then reload or restart your server.");
        else
            titleEnd = " (" + getServer().getName() + ")";

        // RAM checker
        try {
            BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
            scheduler.scheduleSyncRepeatingTask(this, new Runnable() {
                long mb = 1048576;

                @Override
                public void run() {
                    if (Runtime.getRuntime().freeMemory() < (20 * mb))
                        push("Server almost out of memory!", true, true);
                }
            }, 0L, 3000L);
        } catch (Error e) {
            getLogger().warning("Your Bukkit version is too old. Cannot send out-of-memory warnings to operators.");
        }

        canCheckOfflinePlayers = bukkitCompatibilityCheck();

    }

    protected boolean bukkitCompatibilityCheck() {
        try {
            getServer().getOfflinePlayers()[0].getUniqueId();
            return true;
        } catch (NoSuchMethodError e) {
            getLogger().warning("Your Bukkit version is too old. Cannot use /warnops, and performance will be lower.");
            getAmountOfBannedUsers();
            return false;
        }
    }

    protected int getAmountOfInvalidUsers() {
        int counter = 0;

        for (Map.Entry entry : getUserKeysFileConfiguration().getValues(false).entrySet()) {
            if (entry.getValue().equals("INVALID")) {
                counter++;
            }
        }

        invalidUsers = counter;
        return counter;
    }

    protected int getAmountOfBannedUsers() {
        int counter = 0;

        for (Map.Entry entry : getUserKeysFileConfiguration().getValues(false).entrySet()) {
            if (((String) entry.getValue()).endsWith("BANNED")) {
                counter++;
            }
        }

        bannedUsers = counter;
        return counter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("pushover")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("You can only enable or disable notifications as a player.");
                return true;
            }

            if (getCmdArgsLength(args) == 1) {
                if (args[0].equalsIgnoreCase("enable")) {
                    if (sender.hasPermission("minecraftpush.receive")) {
                        // Unfortunately, Pushover user key validation is not yet possible: https://github.com/Jop-V/MinecraftPush/issues/2
                        getUserKeysFileConfiguration().set(((Player) sender).getUniqueId().toString(), args[1]);
                        saveUserKeysFile();

                        StringBuilder messageBuilder = new StringBuilder("§aPush notifications enabled!§f ");
                        if (!canPush)
                            messageBuilder.append("§6You won't receive any at the moment though, because the server is in offline mode.§f ");
                        messageBuilder.append("You can disable them at any time using /pushover disable.");
                        sender.sendMessage(messageBuilder.toString());
                        return true;
                    } else {
                        sender.sendMessage("§cYou don't have permission to receive push notifications!");
                        return true;
                    }
                }
            } else if (getCmdArgsLength(args) == 0)
                if (args[0].equalsIgnoreCase("disable")) {
                    getUserKeysFileConfiguration().set(((Player) sender).getUniqueId().toString(), null);
                    saveUserKeysFile();

                    sender.sendMessage("§aPush notifications disabled.");
                    return true;
                }

            return false;
        }

        if (cmd.getName().equalsIgnoreCase("minecraftpush")) {
            sender.sendMessage(new String[]{
                    "Use the /pushover command to enable or disable push notifications.",
                    "MinecraftPush, Copyright (C) Jop Vernooij, 2014. http://www.jopv.net/ " +
                            "This is NOT an official Pushover app. Pushover is a trademark and product of Superblock, LLC. " +
                            "This plugin is powered by pushover4j: https://github.com/sps/pushover4j " +
                            "Source available at https://github.com/Jop-V/MinecraftPush/. " +
                            "Forked by jhalexand at https://github.com/jhalexand/MinecraftPush"});
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("broadcast")) {
            if (getCmdArgsLength(args) < 1)
                return false;

            if (sender.hasPermission("minecraftpush.broadcast")) {
                if (!canPush) sender.sendMessage("§cServer is in offline mode, can't notify offline players!");
                String message = messageFromCmdAgs(args);
                getServer().broadcastMessage("§d[Broadcasting] " + message);
                push(message);
            } else sender.sendMessage("§cYou don't have permission to broadcast!");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("warnops")) {
            if (getCmdArgsLength(args) < 1)
                return false;

            if (sender.hasPermission("minecraftpush.warnops")) {
                if (!canPush) sender.sendMessage("§cServer is in offline mode, can't notify offline operators!");
                if (!canCheckOfflinePlayers)
                    sender.sendMessage("§cBukkit version too old, can't notify offline operators!");
                String message = messageFromCmdAgs(args);

                int counter = 0;

                // Warn online operators
                for (Player p : getServer().getOnlinePlayers()) {
                    if (p.isOp()) {
                        p.sendMessage(sender.getName() + " warned: " + message);
                        counter++;
                    }
                }

                // Warn offline operators
                counter += push(sender.getName() + " warned: " + message, true, true);

                sender.sendMessage("§aWarned " + Integer.toString(counter) + " operators.");
            } else if (sender.isOp())
                sender.sendMessage("§cYou are an operator yourself...");
            else
                sender.sendMessage("§cYou don't have permission to warn operators!");

            return true;
        }

        return false;
    }

    public String messageFromCmdAgs(String[] args) {
        StringBuilder builder = new StringBuilder();

        for (String s : args)
            builder.append(s).append(' ');

        return builder.toString().trim();
    }

    /**
     * Workaround for some really weird behavior.
     */
    public int getCmdArgsLength(String[] args) {
        if (args != null)
            return args.length - 1;
        else
            return -1;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        push(e.getJoinMessage());
        timeLog.put(e.getPlayer(), System.currentTimeMillis());

        if (invalidUsers == 0 && canCheckOfflinePlayers)
            return;
        Map map = getUserKeysFileConfiguration().getValues(false);
        UUID uuid = e.getPlayer().getUniqueId();
        if (map.containsKey(uuid.toString())) {
            String s = (String) map.get(uuid.toString());

            if (s.endsWith("BANNED"))
                getUserKeysFileConfiguration().set(uuid.toString(), s.substring(0, s.indexOf("BANNED")));

            if (s.equals("INVALID")) {
                getOnlinePlayerByUuid(uuid).sendMessage("§cYour Pushover user key turned out to be invalid! Sorry for not telling you earlier. Please re-enable notifications with a valid Pushover user key.");
                getUserKeysFileConfiguration().set(uuid.toString(), null);
                saveUserKeysFile();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Long startTime = timeLog.get(e.getPlayer());
        String duration = "";
        if (startTime != null) {
            long diff = System.currentTimeMillis() - startTime;
            long oneDay = TimeUnit.DAYS.toMillis(1);
            if (diff >= oneDay) {
                long fullDays = diff / oneDay;
                long remainder = diff % oneDay;
                long hours = remainder / TimeUnit.HOURS.toMillis(1);
                if (hours > 0) {
                    duration = String.format(" (after %d days, %d hours)", fullDays, hours);
                } else {
                    duration = String.format(" (after %d days)", fullDays);
                }
            } else {
                duration = String.format(" (after %02d:%02d:%02d)",
                        TimeUnit.MILLISECONDS.toHours(diff),
                        TimeUnit.MILLISECONDS.toMinutes(diff) -
                                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(diff)),
                        TimeUnit.MILLISECONDS.toSeconds(diff) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diff)));
            }
        }
        push(e.getQuitMessage() + duration);

        if (!canCheckOfflinePlayers) {
            if (e.getPlayer().isBanned()) {
                getUserKeysFileConfiguration().set(e.getPlayer().getUniqueId().toString(), getUserKeysFileConfiguration().get(e.getPlayer().getUniqueId().toString()).toString() + "BANNED");
            }
        }
    }

    /**
     * Push a message to all offline players with push notifications enabled.
     *
     * @param message The message to be sent. Color codes will be automatically stripped out.
     * @return The amount of players notified.
     */
    public int push(String message) {
        return push(message, false, false);
    }

    /**
     * Push a message to all offline players with push notifications enabled.
     *
     * @param message      The message to be sent. Color codes will be automatically stripped out.
     * @param opOnly       Whether only operators should receive this message.
     * @param highPriority Whether this message is of high priority.
     * @return The amount of players notified.
     */
    @SuppressWarnings("SuspiciousMethodCalls")
    public int push(String message, boolean opOnly, boolean highPriority) {
        if (!canPush) return 0;
        if (!canCheckOfflinePlayers && opOnly) return 0;

        if (!opOnly && highPriority)
            getLogger().warning("A high priority message is being sent to regular players. This is probably a bad thing.");

        Map<String, Object> map = getUserKeysFileConfiguration().getValues(false);
        int counter = 0;

        for (String playerUuidString : map.keySet()) {
            UUID playerUuid = UUID.fromString(playerUuidString);
            Player onlinePlayer = getOnlinePlayerByUuid(playerUuid);
            String playerPushoverKey = String.valueOf(map.get(playerUuidString));

            if (playerPushoverKey.equals("INVALID") || playerPushoverKey.endsWith("BANNED"))
                continue;

            if (onlinePlayer == null /* Player is offline, so a notification needs to be sent */) {
                if (canCheckOfflinePlayers) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
                    if (offlinePlayer.isBanned()) {
                        getUserKeysFileConfiguration().set(playerUuidString, map.get(playerUuidString) + "BANNED");
                        continue;
                    } else if (((String) map.get(playerUuidString)).endsWith("BANNED")) {
                        String s = (String) map.get(playerUuidString);
                        getUserKeysFileConfiguration().set(playerUuidString, s.substring(0, s.indexOf("BANNED")));
                    }

                    if (opOnly && !offlinePlayer.isOp())
                        continue;
                } else if (((String) map.get(playerUuidString)).endsWith("BANNED")) {
                    continue;
                }

                try {
                    PushoverMessage.Builder pm = PushoverMessage.builderWithApiToken("autoU13MYYXYxMaeupaqF7U7mBe9Bj")
                            .setUserId(playerPushoverKey)
                            .setSound("gamelan")
                            .setTitle("Minecraft" + titleEnd)
                            .setMessage(ChatColor.stripColor(message));

                    if (highPriority)
                        pm.setPriority(MessagePriority.HIGH);

                    pushoverRestClient.pushMessage(pm.build());
                    counter++;
                } catch (PushoverException e) {
                    if (e.getMessage().equals("user identifier is invalid")) {
                        getUserKeysFileConfiguration().set(playerUuidString, "INVALID");
                        getAmountOfInvalidUsers();
                    } else
                        e.printStackTrace();
                }
            }
        }

        return counter;
    }

    public void loadUserKeysFile() {
        if (userKeysFile == null) {
            userKeysFile = new File(getDataFolder(), "pushoveruserkeys.dat");
        }
        userKeysFileConfiguration = YamlConfiguration.loadConfiguration(userKeysFile);
    }

    public FileConfiguration getUserKeysFileConfiguration() {
        if (userKeysFileConfiguration == null) {
            loadUserKeysFile();
        }
        return userKeysFileConfiguration;
    }

    public void saveUserKeysFile() {
        if (userKeysFileConfiguration == null || userKeysFile == null) {
            return;
        }
        try {
            getUserKeysFileConfiguration().save(userKeysFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save " + userKeysFile, ex);
        }
    }

    public Player getOnlinePlayerByUuid(UUID uuid) {
        Player player = null;
        try {
            player = getServer().getPlayer(uuid);
        } catch (NoSuchMethodError e) {
            for (Player p : getServer().getOnlinePlayers())
                if (p.getUniqueId().equals(uuid))
                    return p;
        }
        return player;
    }
}
