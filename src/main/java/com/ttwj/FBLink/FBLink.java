package com.ttwj.FBLink;


import de.luricos.bukkit.xAuth.PlayerManager;
import de.luricos.bukkit.xAuth.events.xAuthLoginEvent;
import de.luricos.bukkit.xAuth.xAuth;
import de.luricos.bukkit.xAuth.xAuthPlayer;
import net.milkbowl.vault.permission.Permission;
import net.spy.memcached.MemcachedClient;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

public class FBLink extends JavaPlugin implements Listener {

    public static Permission perms = null;
    private PlayerManager playerManager;
    private ArrayList<Player> playerList;
    private MemcachedClient memcache;
    private HashMap<Player, Integer>tries;

    @Override
    public void onEnable() {
        xAuth plugin = (xAuth) getServer().getPluginManager().getPlugin("xAuth");
        setupPermissions();
        tries = new HashMap<Player, Integer>();
        playerManager = plugin.getPlayerManager();
        playerList = new ArrayList<Player>();
        try {
            memcache = new MemcachedClient(new InetSocketAddress("localhost", 11211));
        } catch (IOException e) {
            e.printStackTrace();
        }
        log("PLUGIN LOADED SWAG");
        getServer().getPluginManager().registerEvents(this, this);
        setupPermissions();
        setupDatabase();
    }

    @Override
    public void onDisable() {

    }

    @EventHandler
    public void xAuthLogin(xAuthLoginEvent event) {
        log("OMG PLAYER LOGGED IN HELLO?");
        Iterator itr = playerList.iterator();
        while(itr.hasNext()) {
            Player p = (Player) itr.next();
            xAuthPlayer xp = playerManager.getPlayer(p);
            if (xp.getStatus() == xAuthPlayer.Status.AUTHENTICATED) {
                //playerList.remove(p);
                onAuthenticate(p);
                itr.remove();
            }
        }
        //playerList = playerListCopy;

    }
    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        if (playerList.contains(event.getPlayer())) {
            playerList.remove(event.getPlayer());
        }
    }
    private void setupDatabase() {
        try {
            getDatabase().find(UserInfo.class).findRowCount();
        } catch (Exception e) {
            System.out.println("Installing database for " + getDescription().getName() + " due to first time usage");
            installDDL();
        }
    }
    @EventHandler (priority =  EventPriority.LOWEST)
    public void playerLogin(PlayerLoginEvent event) {
        log("Player " + event.getPlayer().getName() + " has logged in!!!111###");
        xAuthPlayer xp = playerManager.getPlayer(event.getPlayer());
        log("Player status: " + xp.getStatus());

        if ((xp.getStatus() == xAuthPlayer.Status.GUEST) || (xp.getStatus() == xAuthPlayer.Status.REGISTERED)) {
            if (xp.isAuthenticated()) {
                onAuthenticate(event.getPlayer());
                return;
            }
            //bloody xAuth
            if (xp.isRegistered() || xAuth.getPlugin().isAuthURL()) {
                if ((!xp.isLocked()) && (playerManager.checkSession(xp))) {
                    onAuthenticate(event.getPlayer());
                    return;
                }
            }
            log("Player isn't authenticated with xAuth yet.");
            playerList.add(event.getPlayer());
        }
        else if (xp.getStatus() == xAuthPlayer.Status.AUTHENTICATED) {
            log("pLAYER IS ALREADY LGOGED IN");
            onAuthenticate(event.getPlayer());
        }
    }

    private void log(String stuff) {
        getLogger().info(stuff);
    }

    @Override
    public List<Class<?>> getDatabaseClasses() {
        List<Class<?>> list = new ArrayList<Class<?>>();
        list.add(UserInfo.class);
        return list;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
        Player p = getServer().getPlayer(sender.getName());
        if(cmd.getName().equalsIgnoreCase("auth")){
            if (!perms.playerInGroup(p, "guest")) {
                p.sendMessage(ChatColor.YELLOW + "You're already verified!");
                return true;
            }
            if(args.length != 1) {
                p.sendMessage(ChatColor.RED + "Please specify an auth key");
                p.sendMessage(ChatColor.RED + "/auth <key>");
                return true;
            }
            String key = (String) memcache.get(p.getName().toLowerCase() + "_key");
            log("Player " + p.getName() + " da key " + key);
            if (key == null) {
                p.sendMessage(ChatColor.RED + "Unknown key!");
                return true;
            }
            if (!key.equals(args[0])) {
                p.sendMessage(ChatColor.RED + "Invalid key");
                int t;
                if (tries.containsKey(p)) {
                    t = tries.get(p);
                    t++;
                }
                else {
                    t = 1;
                }
                if (t > 4) {
                    p.kickPlayer("Exceeded number of tries");
                }
            }
            else {
                String realName = (String) memcache.get(p.getName().toLowerCase() + "_name");
                String fbID = (String) memcache.get(p.getName().toLowerCase() + "_fbID");
                UserInfo info = new UserInfo();
                info.setFbID(fbID);
                info.setPlayerName(p.getName().toLowerCase());
                info.setRealName(realName);
                try {
                    new UserInfoAdder(this).addUserInfo(info);
                } catch (UserInfoAdder.DuplicateInfoException e) {
                    p.sendMessage(ChatColor.RED + "You are already authenticated!");
                    return true;
                }
                getDatabase().save(info);
                p.sendMessage(ChatColor.GREEN + "Welcome " + realName + "!");
                perms.playerAddGroup(p, "Default");
                perms.playerRemoveGroup(p, "guest");
            }
            return true;
        }
        else if(cmd.getName().equalsIgnoreCase("fbinfo")){

            if(args.length != 1) {
                p.sendMessage(ChatColor.RED + "Usage: /fbinfo <user>");
                return true;
            }
            if (!sender.hasPermission("fbinfo.info") && (!sender.isOp())) {
                p.sendMessage(ChatColor.RED + "You do not have permission");
                return true;
            }
            UserInfo info;
            try {
                log("yo 1");
                info = getDatabase().find(UserInfo.class).
                        where().
                        ieq("playerName", args[0].toLowerCase()).
                        findUnique();
                if (info == null) {
                    p.sendMessage(ChatColor.RED + "No such player found");
                    return true;
                }
            }
            catch (NullPointerException e) {
                p.sendMessage(ChatColor.RED + "No such player found");
                return true;
            }


            log("yo 2");
            log(info.toString());
            p.sendMessage(ChatColor.YELLOW + "Information on " + info.getPlayerName());
            p.sendMessage(ChatColor.YELLOW + "");
            p.sendMessage(ChatColor.YELLOW + "Real Name: " + info.getRealName());
            p.sendMessage(ChatColor.YELLOW + "Facebook ID: " + info.getFbID());
            log("yo 3");
            return true;
        }
        else if(cmd.getName().equalsIgnoreCase("fblogin")){
            if (!sender.hasPermission("fbinfo.login") && (!sender.isOp())) {
                p.sendMessage(ChatColor.RED + "You do not have permission");
                return true;
            }
            if(args.length != 1) {
                p.sendMessage(ChatColor.RED + "Usage: /fblogin <user>");
                return true;
            }
            Player player = getServer().getPlayer(args[0]);
            onAuthenticate(player);
            return true;
        }
        else if(cmd.getName().equalsIgnoreCase("fbpurge")){
            if (!sender.hasPermission("fbinfo.purge") && (!sender.isOp())) {
                p.sendMessage(ChatColor.RED + "You do not have permission");
                return true;
            }

            if(args.length != 1) {
                p.sendMessage(ChatColor.RED + "Usage: /fbpurge <user>");
                return true;
            }
            UserInfo info = getDatabase().find(UserInfo.class).
                    where().
                    ieq("playerName", args[0].toLowerCase()).
                    findUnique();
            getDatabase().delete(info);
            p.sendMessage(ChatColor.YELLOW + "Deleted information on " + args[0]);

            Player player = getServer().getPlayer(args[0]);
            if (player == null) {
                p.sendMessage(ChatColor.RED + "Could not remove permissions from player, not online");
            }
            else {
                perms.playerRemoveGroup(player, "Default");
                perms.playerAddGroup(player, "guest");
            }

            return true;
        }
        return false;
    }


    private void onAuthenticate(Player p) {
        log("Player " + p.getName() + " has logged in xAuth");
        //check group
        if (perms.playerInGroup(p, "guest")) {
            log("Player " + p.getName() + " is a guest, redirecting to Facebook SSO");
            p.sendMessage(ChatColor.GREEN + "Hello guest! Please link your account with Facebook to get");
            p.sendMessage(ChatColor.GREEN + "validated. http://fb.ttwj.tk/" + p.getName() + "/");
            p.sendMessage(" ");
            p.sendMessage(ChatColor.RED + "Please do so now or you will not have the ability to");
            p.sendMessage(ChatColor.RED + "modify blocks! (you may lose your items)!");
            p.sendMessage(" ");
            memcache.set(p.getName().toLowerCase() + "_key", 1800, generate(5));
            String key = (String) memcache.get(p.getName().toLowerCase() + "_key");
            log("Player " + p.getName() + " da key " + key);

        }

    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();
        return perms != null;
    }

    static String generate(int length){
        Random r = new Random();
        String number="";
        int counter=0;
        while(counter++< length) number+= r.nextInt(9);

        return number;

    }

}
