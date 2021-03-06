package me.StevenLawson.TotalFreedomMod;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.StevenLawson.TotalFreedomMod.Commands.Command_logs;
import me.StevenLawson.TotalFreedomMod.Config.TFM_Config;
import me.StevenLawson.TotalFreedomMod.World.TFM_AdminWorld;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.FileUtil;

public class TFM_AdminList
{
    private static final Map<UUID, TFM_Admin> adminList;
    private static final Set<UUID> superadminUUIDs;
    private static final Set<UUID> telnetadminUUIDs;
    private static final Set<UUID> senioradminUUIDs;
    private static final Set<String> consoleAliases;
    private static final Set<String> superadminIps;
    private static int cleanThreshold = 24 * 7; // 1 Week in hours

    static
    {
        adminList = new HashMap<UUID, TFM_Admin>();
        superadminUUIDs = new HashSet<UUID>();
        telnetadminUUIDs = new HashSet<UUID>();
        senioradminUUIDs = new HashSet<UUID>();
        consoleAliases = new HashSet<String>();
        superadminIps = new HashSet<String>();
    }

    public static Set<UUID> getSuperadminUUIDs()
    {
        return Collections.unmodifiableSet(superadminUUIDs);
    }

    public static Set<UUID> getTelnetadminUUIDs()
    {
        return Collections.unmodifiableSet(telnetadminUUIDs);
    }

    public static Set<UUID> getSenioradminUUIDs()
    {
        return Collections.unmodifiableSet(senioradminUUIDs);
    }

    public static Set<String> getConsoleAliases()
    {
        return Collections.unmodifiableSet(consoleAliases);
    }

    public static Set<String> getSuperadminIps()
    {
        return Collections.unmodifiableSet(superadminIps);
    }

    public static Set<String> getSuperadminNames()
    {
        final Set<String> names = new HashSet<String>();

        for (TFM_Admin admin : adminList.values())
        {
            names.add(admin.getLastLoginName());
        }

        return Collections.unmodifiableSet(names);
    }

    public static Set<String> getLowerSuperadminNames()
    {
        final Set<String> names = new HashSet<String>();

        for (TFM_Admin admin : adminList.values())
        {
            names.add(admin.getLastLoginName().toLowerCase());
        }

        return Collections.unmodifiableSet(names);
    }

    public static void load()
    {
        try
        {
            adminList.clear();

            final TFM_Config config = new TFM_Config(TotalFreedomMod.plugin, TotalFreedomMod.SUPERADMIN_FILE, true);
            config.load();

            cleanThreshold = config.getInt("clean_threshold_hours", cleanThreshold);

            // Parse old superadmins
            if (config.isConfigurationSection("superadmins"))
            {
                TFM_Log.info("Old superadmin configuration found, parsing...");

                final ConfigurationSection section = config.getConfigurationSection("superadmins");

                int counter = 0;
                int errors = 0;

                for (String admin : config.getConfigurationSection("superadmins").getKeys(false))
                {
                    final OfflinePlayer player = Bukkit.getOfflinePlayer(admin);

                    if (player == null || player.getUniqueId() == null)
                    {
                        errors++;
                        TFM_Log.warning("Could not convert admin " + admin + ", UUID could not be found!");
                        continue;
                    }

                    final String uuid = player.getUniqueId().toString();

                    config.set("admins." + uuid + ".last_login_name", player.getName());
                    config.set("admins." + uuid + ".is_activated", section.getBoolean(admin + ".is_activated"));
                    config.set("admins." + uuid + ".is_telnet_admin", section.getBoolean(admin + ".is_telnet_admin"));
                    config.set("admins." + uuid + ".is_senior_admin", section.getBoolean(admin + ".is_senior_admin"));
                    config.set("admins." + uuid + ".last_login", section.getString(admin + ".last_login"));
                    config.set("admins." + uuid + ".custom_login_message", section.getString(admin + ".custom_login_message"));
                    config.set("admins." + uuid + ".console_aliases", section.getStringList(admin + ".console_aliases"));
                    config.set("admins." + uuid + ".ips", section.getStringList(admin + ".ips"));

                    counter++;
                }

                config.set("superadmins", null);
                config.save();

                TFM_Log.info(counter + " admins parsed, " + errors + " errors");
            }

            if (!config.isConfigurationSection("admins"))
            {
                TFM_Log.warning("Missing admins section in superadmin.yml.");
                return;
            }

            final ConfigurationSection section = config.getConfigurationSection("admins");

            for (String uuidString : section.getKeys(false))
            {
                if (!TFM_Util.isUniqueId(uuidString))
                {
                    TFM_Log.warning("Invalid Unique ID: " + uuidString + " in superadmin.yml, ignoring");
                    continue;
                }

                final UUID uuid = UUID.fromString(uuidString);

                final TFM_Admin superadmin = new TFM_Admin(uuid, section.getConfigurationSection(uuidString));
                adminList.put(uuid, superadmin);
            }

            updateIndexLists();
        }
        catch (Exception ex)
        {
            TFM_Log.severe(ex);
        }
    }

    public static void backupSavedList()
    {
        final File oldYaml = new File(TotalFreedomMod.plugin.getDataFolder(), TotalFreedomMod.SUPERADMIN_FILE);
        final File newYaml = new File(TotalFreedomMod.plugin.getDataFolder(), TotalFreedomMod.SUPERADMIN_FILE + ".bak");
        FileUtil.copy(oldYaml, newYaml);
    }

    public static void updateIndexLists()
    {
        superadminUUIDs.clear();
        telnetadminUUIDs.clear();
        senioradminUUIDs.clear();
        consoleAliases.clear();
        superadminIps.clear();

        for (TFM_Admin admin : adminList.values())
        {
            if (!admin.isActivated())
            {
                continue;
            }

            final UUID uuid = admin.getUniqueId();

            superadminUUIDs.add(uuid);

            for (String ip : admin.getIps())
            {
                superadminIps.add(ip);
            }

            if (admin.isTelnetAdmin())
            {
                telnetadminUUIDs.add(uuid);

                for (String alias : admin.getConsoleAliases())
                {
                    consoleAliases.add(alias.toLowerCase());
                }
            }


            if (admin.isSeniorAdmin())
            {
                senioradminUUIDs.add(uuid);
            }
        }

        TFM_AdminWorld.getInstance().wipeAccessCache();
    }

    public static void save()
    {
        updateIndexLists();

        final TFM_Config config = new TFM_Config(TotalFreedomMod.plugin, TotalFreedomMod.SUPERADMIN_FILE, true);
        config.load();

        config.set("clean_threshold_hours", cleanThreshold);

        Iterator<Entry<UUID, TFM_Admin>> it = adminList.entrySet().iterator();
        while (it.hasNext())
        {
            Entry<UUID, TFM_Admin> pair = it.next();

            UUID uuid = pair.getKey();
            TFM_Admin superadmin = pair.getValue();

            config.set("admins." + uuid + ".last_login_name", superadmin.getLastLoginName());
            config.set("admins." + uuid + ".is_activated", superadmin.isActivated());
            config.set("admins." + uuid + ".is_telnet_admin", superadmin.isTelnetAdmin());
            config.set("admins." + uuid + ".is_senior_admin", superadmin.isSeniorAdmin());
            config.set("admins." + uuid + ".last_login", TFM_Util.dateToString(superadmin.getLastLogin()));
            config.set("admins." + uuid + ".custom_login_message", superadmin.getCustomLoginMessage());
            config.set("admins." + uuid + ".console_aliases", TFM_Util.removeDuplicates(superadmin.getConsoleAliases()));
            config.set("admins." + uuid + ".ips", TFM_Util.removeDuplicates(superadmin.getIps()));
        }

        config.save();
    }

    public static TFM_Admin getEntry(Player player)
    {
        final UUID uuid = player.getUniqueId();

        if (Bukkit.getOnlineMode())
        {
            if (adminList.containsKey(uuid))
            {
                return adminList.get(uuid);
            }
        }

        return getEntryByIp(TFM_Util.getIp(player));
    }

    public static TFM_Admin getEntry(UUID uuid)
    {
        return adminList.get(uuid);
    }

    @Deprecated
    public static TFM_Admin getEntry(String name)
    {
        for (UUID uuid : adminList.keySet())
        {
            if (adminList.get(uuid).getLastLoginName().equalsIgnoreCase(name))
            {
                return adminList.get(uuid);
            }
        }
        return null;
    }

    public static TFM_Admin getEntryByIp(String ip)
    {
        return getEntryByIp(ip, false);
    }

    public static TFM_Admin getEntryByIp(String needleIp, boolean fuzzy)
    {
        Iterator<Entry<UUID, TFM_Admin>> it = adminList.entrySet().iterator();
        while (it.hasNext())
        {
            final Entry<UUID, TFM_Admin> pair = it.next();
            final TFM_Admin superadmin = pair.getValue();

            if (fuzzy)
            {
                for (String haystackIp : superadmin.getIps())
                {
                    if (TFM_Util.fuzzyIpMatch(needleIp, haystackIp, 3))
                    {
                        return superadmin;
                    }
                }
            }
            else
            {
                if (superadmin.getIps().contains(needleIp))
                {
                    return superadmin;
                }
            }
        }
        return null;
    }

    public static void updateLastLogin(Player player)
    {
        final TFM_Admin admin = getEntry(player);
        if (admin != null)
        {
            admin.setLastLogin(new Date());
            admin.setLastLoginName(player.getName());
            save();
        }
    }

    public static boolean isSeniorAdmin(CommandSender sender)
    {
        return isSeniorAdmin(sender, false);
    }

    public static boolean isSeniorAdmin(CommandSender sender, boolean verifySuperadmin)
    {
        if (verifySuperadmin)
        {
            if (!isSuperAdmin(sender))
            {
                return false;
            }
        }


        if (!(sender instanceof Player))
        {
            return consoleAliases.contains(sender.getName());
        }

        final TFM_Admin entry = getEntry((Player) sender);
        if (entry != null)
        {
            return entry.isSeniorAdmin();
        }

        return false;
    }

    public static boolean isSuperAdmin(CommandSender sender)
    {
        if (!(sender instanceof Player))
        {
            return true;
        }

        if (Bukkit.getOnlineMode() && superadminUUIDs.contains(((Player) sender).getUniqueId()))
        {
            return true;
        }


        if (superadminIps.contains(TFM_Util.getIp((Player) sender)))
        {
            return true;
        }

        return false;
    }

    public static boolean isIdentityMatched(Player player)
    {
        if (!isSuperAdmin(player))
        {
            return false;
        }

        if (Bukkit.getOnlineMode())
        {
            return true;
        }

        final TFM_Admin entry = getEntry(player);
        if (entry == null)
        {
            return false;
        }

        return entry.getUniqueId().equals(player.getUniqueId());
    }

    @Deprecated
    public static boolean checkPartialSuperadminIp(String ip, String name)
    {
        ip = ip.trim();

        if (superadminIps.contains(ip))
        {
            return true;
        }

        try
        {
            String matchIp = null;
            for (String testIp : superadminIps)
            {
                if (TFM_Util.fuzzyIpMatch(ip, testIp, 3))
                {
                    matchIp = testIp;
                    break;
                }
            }

            if (matchIp != null)
            {
                final TFM_Admin entry = getEntryByIp(matchIp);

                if (entry == null)
                {
                    return true;
                }

                if (entry.getLastLoginName().equalsIgnoreCase(name))
                {
                    if (!entry.getIps().contains(ip))
                    {
                        entry.addIp(ip);
                    }
                    save();
                }
                return true;

            }
        }
        catch (Exception ex)
        {
            TFM_Log.severe(ex);
        }

        return false;
    }

    public static boolean isAdminImpostor(Player player)
    {
        if (superadminUUIDs.contains(player.getUniqueId()))
        {
            return !isSuperAdmin(player);
        }

        return false;
    }

    public static void addSuperadmin(Player player)
    {
        try
        {
            final UUID uuid = player.getUniqueId();
            final String ip = TFM_Util.getIp(player);

            if (adminList.containsKey(uuid))
            {
                TFM_Admin superadmin = adminList.get(uuid);
                superadmin.setActivated(true);
                superadmin.addIp(TFM_Util.getIp(player));
                superadmin.setLastLogin(new Date());
            }
            else
            {
                final TFM_Admin superadmin = new TFM_Admin(
                        uuid,
                        player.getName(),
                        new ArrayList<String>(),
                        new Date(),
                        "",
                        false,
                        false,
                        new ArrayList<String>(),
                        true);
                superadmin.addIp(ip);
                adminList.put(uuid, superadmin);
            }

            save();
        }
        catch (Exception ex)
        {
            TFM_Log.severe("Cannot add superadmin: " + TFM_Util.formatPlayer(player));
            TFM_Log.severe(ex);
        }
    }

    public static void removeSuperadmin(OfflinePlayer player)
    {
        final UUID uuid = player.getUniqueId();

        if (adminList.containsKey(uuid))
        {
            TFM_Admin superadmin = adminList.get(uuid);
            superadmin.setActivated(false);
            Command_logs.deactivateSuperadmin(superadmin);
            save();
        }
    }

    public static void cleanSuperadminList(boolean verbose)
    {
        Iterator<Entry<UUID, TFM_Admin>> it = adminList.entrySet().iterator();
        while (it.hasNext())
        {
            final Entry<UUID, TFM_Admin> pair = it.next();
            final TFM_Admin superadmin = pair.getValue();

            if (!superadmin.isActivated() || superadmin.isSeniorAdmin())
            {
                continue;
            }

            final Date lastLogin = superadmin.getLastLogin();
            final long lastLoginHours = TimeUnit.HOURS.convert(new Date().getTime() - lastLogin.getTime(), TimeUnit.MILLISECONDS);

            if (lastLoginHours > cleanThreshold)
            {
                if (verbose)
                {
                    TFM_Util.adminAction("TotalFreedomMod", "Deactivating superadmin " + superadmin.getLastLoginName() + ", inactive for " + lastLoginHours + " hours.", true);
                }

                superadmin.setActivated(false);
                Command_logs.deactivateSuperadmin(superadmin);
                TFM_TwitterHandler.getInstance().delTwitter(superadmin.getLastLoginName());
            }
        }
        save();
    }

    private TFM_AdminList()
    {
        throw new AssertionError();
    }

    public File getConfigFile()
    {
        return new File(TotalFreedomMod.plugin.getDataFolder(), TotalFreedomMod.SUPERADMIN_FILE);
    }
}
