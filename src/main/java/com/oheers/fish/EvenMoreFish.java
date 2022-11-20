package com.oheers.fish;

import com.oheers.fish.api.EMFAPI;
import com.oheers.fish.baits.Bait;
import com.oheers.fish.competition.AutoRunner;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.CompetitionQueue;
import com.oheers.fish.competition.JoinChecker;
import com.oheers.fish.config.*;
import com.oheers.fish.config.messages.Messages;
import com.oheers.fish.database.DatabaseV3;
import com.oheers.fish.database.FishReport;
import com.oheers.fish.database.Table;
import com.oheers.fish.database.UserReport;
import com.oheers.fish.events.*;
import com.oheers.fish.exceptions.InvalidTableException;
import com.oheers.fish.fishing.FishingProcessor;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Names;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.gui.FillerStyle;
import com.oheers.fish.selling.InteractHandler;
import com.oheers.fish.selling.SellGUI;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EvenMoreFish extends JavaPlugin {

    public final static Semaphore v3Semaphore = new Semaphore(1);
    public static final int METRIC_ID = 11054;
    public static final int MSG_CONFIG_VERSION = 12;
    public static final int MAIN_CONFIG_VERSION = 11;
    public static final int COMP_CONFIG_VERSION = 1;
    public static FishFile fishFile;
    public static RaritiesFile raritiesFile;
    public static BaitFile baitFile;
    public static Messages msgs;
    public static MainConfig mainConfig;
    public static CompetitionConfig competitionConfig;
    public static GUIConfig guiConfig;
    public static List<String> competitionWorlds = new ArrayList<>();
    public static Permission permission = null;
    public static Economy econ = null;
    public static Map<Integer, Set<String>> fish = new HashMap<>();
    public static Map<String, Bait> baits = new HashMap<>();
    public static Map<Rarity, List<Fish>> fishCollection = new HashMap<>();
    public static Map<UUID, List<FishReport>> fishReports = new HashMap<>();
    public static Map<UUID, UserReport> userReports = new HashMap<>();
    public static List<UUID> disabledPlayers = new ArrayList<>();
    public static boolean checkingEatEvent;
    public static boolean checkingIntEvent;
    // Do some fish in some rarities have the comp-check-exempt: true.
    public static boolean raritiesCompCheckExempt = false;
    public static Competition active;
    public static CompetitionQueue competitionQueue;
    public static Logger logger;
    public static PluginManager pluginManager;
    public static ArrayList<SellGUI> guis;
    public static int metric_fishCaught = 0;
    public static int metric_baitsUsed = 0;
    public static int metric_baitsApplied = 0;
    // this is for pre-deciding a rarity and running particles if it will be chosen
    // it's a work-in-progress solution and probably won't stick.
    public static Map<UUID, Rarity> decidedRarities;
    public static boolean isUpdateAvailable;
    public static boolean usingPAPI;
    public static boolean usingMcMMO;

    public static boolean itemsAdderLoaded = false;
    public static WorldGuardPlugin wgPlugin;
    public static String guardPL;
    public static boolean papi;
    public static DatabaseV3 databaseV3;
    private static EvenMoreFish instance;
    public static FillerStyle guiFillerStyle;
    private EMFAPI api;

    public static EvenMoreFish getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.api = new EMFAPI();

        guis = new ArrayList<>();
        decidedRarities = new HashMap<>();

        logger = getLogger();
        pluginManager = getServer().getPluginManager();

        getConfig().options().copyDefaults();
        saveDefaultConfig();

        mainConfig = new MainConfig(this);
        msgs = new Messages(this);

        fishFile = new FishFile(this);
        raritiesFile = new RaritiesFile(this);
        baitFile = new BaitFile(this);
        competitionConfig = new CompetitionConfig(this);
        if (mainConfig.debugSession()) guiConfig = new GUIConfig(this);
        if (guiConfig != null) guiFillerStyle = guiConfig.getFillerStyle("main-menu");

        usingPAPI = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;

        if (mainConfig.isEconomyEnabled()) {
            // could not set up economy.
            if (!setupEconomy()) {
                EvenMoreFish.logger.log(Level.WARNING, "EvenMoreFish won't be hooking into economy. If this wasn't by choice in config.yml, please install Economy handling plugins.");
            }
        }

        if (!setupPermissions()) {
            Bukkit.getServer().getLogger().log(Level.SEVERE, "EvenMoreFish couldn't hook into Vault permissions. Disabling to prevent serious problems.");
            getServer().getPluginManager().disablePlugin(this);
        }

        // checks against both support region plugins and sets an active plugin (worldguard is priority)
        if (checkWG()) {
            guardPL = "worldguard";
        } else if (checkRP()) {
            guardPL = "redprotect";
        }

        competitionWorlds = competitionConfig.getRequiredWorlds();

        Names names = new Names();
        names.loadRarities(fishFile.getConfig(), raritiesFile.getConfig());
        names.loadBaits(baitFile.getConfig());

        if (!names.regionCheck && !(mainConfig.getAllowedRegions().size() > 0)) guardPL = null;

        competitionQueue = new CompetitionQueue();
        competitionQueue.load();

        listeners();
        commands();

        AutoRunner.init();

        wgPlugin = null;
        checkPapi();

        if (EvenMoreFish.mainConfig.databaseEnabled()) {

            Map<UUID, UserReport> newReports = new HashMap<>();

            databaseV3 = new DatabaseV3(this);
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        v3Semaphore.acquire();
                        databaseV3.getConnection();
                        try {
                            EvenMoreFish.databaseV3.createTables(false);
                        } catch (SQLException exception) {
                            EvenMoreFish.logger.log(Level.SEVERE, "Failed to create new tables or check for present tables.");
                            exception.printStackTrace();
                        }

                        for (Player player : getServer().getOnlinePlayers()) {
                            newReports.put(player.getUniqueId(), databaseV3.readUserReport(player.getUniqueId()));
                        }

                        databaseV3.closeConnection();
                        v3Semaphore.release();
                    } catch (SQLException exception) {
                        logger.log(Level.SEVERE, "Failed SQL operations whilst enabling plugin. Try restarting or contacting support.");
                        exception.printStackTrace();
                    } catch (InterruptedException exception) {
                        logger.log(Level.SEVERE, "Severe interruption when fetching user reports for online users. If you are using PlugMan, you should restart the plugin completely.");
                        exception.printStackTrace();
                    }
                }
            }.runTaskAsynchronously(this);

            userReports.putAll(newReports);

        }

        logger.log(Level.INFO, "EvenMoreFish by Oheers : Enabled");
    }

    @Override
    public void onDisable() {

        terminateSellGUIS();
        saveUserData();

        // Ends the current competition in case the plugin is being disabled when the server will continue running
        if (Competition.isActive()) {
            active.end();
        }

        logger.log(Level.INFO, "EvenMoreFish by Oheers : Disabled");

    }

    private void listeners() {

        getServer().getPluginManager().registerEvents(new JoinChecker(), this);
        getServer().getPluginManager().registerEvents(new FishingProcessor(), this);
        getServer().getPluginManager().registerEvents(new InteractHandler(this), this);
        //getServer().getPluginManager().registerEvents(new BaitApplicationListener(), this);

        optionalListeners();
    }

    private void optionalListeners() {
        if (checkingEatEvent) {
            getServer().getPluginManager().registerEvents(FishEatEvent.getInstance(), this);
        }

        if (checkingIntEvent) {
            getServer().getPluginManager().registerEvents(FishInteractEvent.getInstance(), this);
        }
    }

    private void commands() {
        getCommand("fish").setExecutor(new CommandCentre(this));
        CommandCentre.loadTabCompletes();
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        permission = rsp.getProvider();
        return permission != null;
    }

    private boolean setupEconomy() {
        if (mainConfig.isEconomyEnabled()) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            }
            econ = rsp.getProvider();
            return econ != null;
        } else return false;

    }

    // gets called on server shutdown to simulate all player's closing their /emf shop GUIs
    private void terminateSellGUIS() {
        getServer().getOnlinePlayers().forEach(player -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SellGUI) {
                player.closeInventory();
            }
        });
    }

    private void saveUserData() {
        if (mainConfig.isDatabaseOnline()) {
            try {
                v3Semaphore.acquire();
                databaseV3.getConnection();
                for (UUID uuid : fishReports.keySet()) {
                    databaseV3.writeFishReports(uuid, fishReports.get(uuid));

                    try {
                        if (!databaseV3.hasUser(uuid, Table.EMF_USERS)) {
                            databaseV3.createUser(uuid);
                        }
                    } catch (InvalidTableException exception) {
                        logger.log(Level.SEVERE, "Fatal error when storing data for " + uuid + ", their data in primary storage has been deleted.");
                    }
                }

                for (UUID uuid : userReports.keySet()) {
                    databaseV3.writeUserReport(uuid, userReports.get(uuid));
                }
                databaseV3.closeConnection();
                v3Semaphore.release();

            } catch (SQLException exception) {
                logger.log(Level.SEVERE, "Failed to save all user data.");
                exception.printStackTrace();
            } catch (InterruptedException exception) {
                logger.log(Level.SEVERE, "Data saving interrupted. Data not saved from previous session.");
                exception.printStackTrace();
            }
        }
        EvenMoreFish.userReports.clear();
    }

    public void reload() {

        terminateSellGUIS();

        setupEconomy();

        fish = new HashMap<>();
        fishCollection = new HashMap<>();

        reloadConfig();
        saveDefaultConfig();

        Names names = new Names();
        names.loadRarities(fishFile.getConfig(), raritiesFile.getConfig());
        names.loadBaits(baitFile.getConfig());

        HandlerList.unregisterAll(FishEatEvent.getInstance());
        HandlerList.unregisterAll(FishInteractEvent.getInstance());
        optionalListeners();

        mainConfig.reload();
        msgs.reload();
        competitionConfig.reload();
        if (mainConfig.debugSession()) guiConfig.reload();

        competitionWorlds = competitionConfig.getRequiredWorlds();

        competitionQueue.load();
    }

    private void checkConfigVers() throws IOException {

        if (msgs.configVersion() < MSG_CONFIG_VERSION) {
            ConfigUpdater.updateMessages(msgs.configVersion());
            getLogger().log(Level.WARNING, "Your messages.yml config is not up to date. The plugin may have automatically added the extra features but you may wish to" +
                    " modify them to suit your server.");

            msgs.reload();
        }

        if (mainConfig.configVersion() < MAIN_CONFIG_VERSION) {
            ConfigUpdater.updateConfig(mainConfig.configVersion());
            getLogger().log(Level.WARNING, "Your config.yml config is not up to date. The plugin may have automatically added the extra features but you may wish to" +
                    " modify them to suit your server.");

            reloadConfig();
        }

        if (competitionConfig.configVersion() < COMP_CONFIG_VERSION) {
            getLogger().log(Level.WARNING, "Your competitions.yml config is not up to date. Certain new configurable features may have been added, and without" +
                    " an updated config, you won't be able to modify them. To update, either delete your competitions.yml file and restart the server to create a new" +
                    " fresh one, or go through the recent updates, adding in missing values. https://www.spigotmc.org/resources/evenmorefish.91310/updates/");

            competitionConfig.reload();
        }

        ConfigUpdater.clearUpdaters();
    }

    /* Gets the worldguard plugin, returns null and assumes the player has this functionality disabled if it
       can't find the plugin. */
    private WorldGuardPlugin getWorldGuard() {
        return (WorldGuardPlugin) this.getServer().getPluginManager().getPlugin("WorldGuard");
    }

    private void checkPapi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papi = true;
            new PlaceholderReceiver(this).register();
        }

    }

    private boolean checkRP() {
        Plugin pRP = Bukkit.getPluginManager().getPlugin("RedProtect");
        return (pRP != null);
    }

    private boolean checkWG() {
        Plugin pWG = Bukkit.getPluginManager().getPlugin("WorldGuard");
        return (pWG != null);
    }

    public EMFAPI getAPI() {
        return this.api;
    }
}
