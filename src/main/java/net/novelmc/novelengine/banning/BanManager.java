package net.novelmc.novelengine.banning;

import java.util.Objects;
import lombok.Getter;
import net.novelmc.novelengine.util.NLog;
import net.novelmc.novelengine.util.NovelBase;
import net.novelmc.novelengine.util.SQLManager;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class BanManager extends NovelBase
{

    @Getter
    private static List<Ban> bans;
    private static String uuid = null;

    public BanManager()
    {
        bans = new ArrayList<>();
        bans.clear();
        loadBans();
    }

    public final void loadBans()
    {
        bans.clear();

        if (config.isSQLEnabled())
        {
            Connection c = SQLManager.getConnection();

            try
            {
                ResultSet result = c.prepareStatement("SELECT * FROM bans").executeQuery();
                while (result.next())
                {
                    addBan(result.getString("uuid"),
                            result.getString("ip"),
                            result.getString("by"),
                            result.getString("reason"),
                            result.getString("uid"),
                            new Date(result.getLong("expiry")),
                            BanType.valueOf(result.getString("type")));
                }
            } catch (SQLException ex)
            {
                NLog.severe(ex);
                return;
            }
        } else
        {
            JSONObject banJson = plugin.sqlManager.getDatabase().getJSONObject("bans");
            banJson.keySet().stream().map((key) -> banJson.getJSONObject(key)).forEachOrdered((obj) ->
            {
                addBan(obj.getString("uuid"),
                        obj.getString("ip"),
                        obj.getString("by"),
                        obj.getString("reason"),
                        obj.getString("uid"),
                        new Date(obj.getLong("expiry")),
                        BanType.valueOf(obj.getString("type")));
            });
        }

        removeExpiredBans();
        NLog.info("Successfully loaded " + bans.size() + " bans!");
    }

    public static void addBan(String name, String ip, String by, String reason, String uid, Date expiry, BanType type)
    {
        Ban ban = new Ban();
        ban.setUUID(retUUID(name));
        ban.setIp(ip);
        ban.setBy(by);
        ban.setReason(reason);
        ban.setUID(uid);
        ban.setExpiry(expiry);
        ban.setType(type);

        if ( ! isBanned(ban))
        {
            bans.add(ban);
        }
    }

    public static void addBan(Ban ban)
    {
        if (isBanned(ban))
        {
            return;
        }

        bans.add(ban);
        ban.save();
    }

    public static void removeExpiredBans()
    {
        bans.stream().filter(Ban :: isExpired).forEach(bans :: remove);
    }

    public static void removeBan(Ban ban)
    {
        if ( ! isBanned(ban))
        {
            return;
        }

        bans.remove(ban);
        ban.delete();
    }

    public static boolean isBanned(Ban ban)
    {
        removeExpiredBans();
        return bans.stream().anyMatch((b) -> (b.getUUID().equals(ban.getUUID()) || b.getIp().equals(ban.getIp())));
    }

    public static boolean isBanned(Player player)
    {
        removeExpiredBans();
        return getBan(player) != null;
    }

    public static boolean isIPBanned(String ip)
    {
        return getBansByType(BanType.IP).stream().anyMatch((ban) -> (ban.getIp().equals(ip)))
                || getBansByType(BanType.NORMAL).stream().anyMatch((ban) -> (ban.getIp().equals(ip)));
    }

    public static boolean isIPPermBanned(String ip)
    {
        return getBansByType(BanType.PERMANENT_IP).stream().anyMatch((ban) -> (ban.getIp().equals(ip)));
    }

    public static boolean isNamePermBanned(String name)
    {
        return getBansByType(BanType.PERMANENT_NAME).stream().anyMatch((ban) -> (ban.getUUID().equals(name)));
    }

    public static Ban getBan(Player player)
    {
        for (Ban ban : bans)
        {
            if (ban.getUUID() != null)
            {
                if (ban.getUUID().equals(retUUID(player.getName())));
                {
                    return ban;
                }
            }

            if (ban.getIp() != null)
            {
                if (ban.getIp().equals(player.getAddress().getHostString()))
                {
                    return ban;
                }
            }
        }
        return null;
    }

    public static Ban getBan(String name, String ip)
    {
        for (Ban ban : bans)
        {
            if (ban.getUUID() != null)
            {
                if (ban.getUUID().equals(retUUID(name)))
                {
                    return ban;
                }
            }

            if (ban.getIp() != null)
            {
                if (ban.getIp().equals(ip))
                {
                    return ban;
                }
            }
        }
        return null;
    }

    public static Ban getBan(PlayerLoginEvent event)
    {
        return getBan(event.getPlayer().getName(), event.getAddress().getHostAddress());
    }

    public static Ban getBan(String name)
    {
        for (Ban ban : bans)
        {
            if (ban.getUUID().equals(name))
            {
                return ban;
            }
        }
        return null;
    }

    public static List<Ban> getBansByType(BanType type)
    {
        List<Ban> banType = new ArrayList<>();

        bans.stream().filter((ban) -> (ban.getType().equals(type))).forEachOrdered(banType::add);

        return banType;
    }
    
    public static String retUUID(String name) {
        Player player = Bukkit.getPlayer(name);
        if (player == null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
            uuid = offlinePlayer.getUniqueId().toString();
        } else {
            uuid = player.getUniqueId().toString();
        }
        
        return uuid;
    }
}
