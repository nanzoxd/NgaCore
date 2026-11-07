package me.smpcore;

import java.util.List;

import me.smpcore.command.ChatCommands;
import me.smpcore.command.SmpCommand;
import me.smpcore.command.PunishCommand;
import me.smpcore.command.TempBanCommand;
import me.smpcore.gui.GuiListener;
import me.smpcore.gui.Feature;
import me.smpcore.gui.FeatureListMenu;
import me.smpcore.gui.Features;
import me.smpcore.module.ChatModule;
import me.smpcore.module.CooldownModule;
import me.smpcore.module.DamageLimiterModule;
import me.smpcore.module.DeathBanModule;
import me.smpcore.module.DimensionModule;
import me.smpcore.module.DragonModule;
import me.smpcore.module.PunishManager;
import me.smpcore.module.MoreTweaksModule;
import me.smpcore.module.TweaksModule;
import me.smpcore.module.XaeroModule;
import me.smpcore.module.EnchantModule;
import me.smpcore.module.GraceModule;
import me.smpcore.module.InstaRestockModule;
import me.smpcore.module.ItemBanModule;
import me.smpcore.module.NetheriteBanModule;
import me.smpcore.module.PotionModule;
import me.smpcore.module.PromptManager;
import me.smpcore.module.TempBanManager;
import me.smpcore.util.Msg;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class SMPCore extends JavaPlugin {
    public PromptManager prompts;
    public TempBanManager tempBans;
    public me.smpcore.module.BanAnimation banAnimation;
    public PunishManager punish;
    private DragonModule dragon;
    public ItemBanModule itemBan;
    public NetheriteBanModule netherite;
    public GraceModule grace;
    public me.smpcore.module.SpawnModule spawn;
    public me.smpcore.module.StaffModule staff;
    public me.smpcore.module.RandomTpModule randomTp;
    private PotionModule potions;
    private EnchantModule enchants;
    public XaeroModule xaero;
    public me.smpcore.module.CombatModule combat;
    public me.smpcore.module.AnnounceModule announce;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateLookAndFeel();
        migrateColours();
        migrateBanScreen();
        migrateCombatMessages();
        migrateNetheriteBan();
        Msg.init(this);

        prompts = new PromptManager(this);
        tempBans = new TempBanManager(this);
        banAnimation = new me.smpcore.module.BanAnimation(this);
        punish = new PunishManager(this);
        dragon = new DragonModule(this);
        TweaksModule tweaks = new TweaksModule(this);
        itemBan = new ItemBanModule(this);
        netherite = new NetheriteBanModule(this);
        potions = new PotionModule(this);
        enchants = new EnchantModule(this);
        ChatModule chat = new ChatModule(this);
        xaero = new XaeroModule(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new GuiListener(), this);
        pm.registerEvents(prompts, this);
        pm.registerEvents(tempBans, this);
        pm.registerEvents(punish, this);
        pm.registerEvents(dragon, this);
        pm.registerEvents(tweaks, this);
        pm.registerEvents(new MoreTweaksModule(this), this);
        pm.registerEvents(itemBan, this);
        pm.registerEvents(netherite, this);
        pm.registerEvents(new InstaRestockModule(this), this);
        staff = new me.smpcore.module.StaffModule(this);
        pm.registerEvents(staff, this);
        spawn = new me.smpcore.module.SpawnModule(this);
        pm.registerEvents(spawn, this);
        randomTp = new me.smpcore.module.RandomTpModule(this);
        pm.registerEvents(randomTp, this);
        grace = new GraceModule(this);
        pm.registerEvents(grace, this);
        pm.registerEvents(potions, this);
        pm.registerEvents(enchants, this);
        pm.registerEvents(chat, this);
        pm.registerEvents(xaero, this);
        combat = new me.smpcore.module.CombatModule(this);
        pm.registerEvents(combat, this);
        pm.registerEvents(new DamageLimiterModule(this), this);
        pm.registerEvents(new CooldownModule(this), this);
        pm.registerEvents(new DimensionModule(this), this);
        pm.registerEvents(new DeathBanModule(this), this);

        bind("smp", new SmpCommand(this));
        bind("settings", new SmpCommand(this));
        TempBanCommand tb = new TempBanCommand(this);
        bind("tempban", tb);
        // these replace the vanilla /ban and /pardon
        me.smpcore.command.BanCommand bc = new me.smpcore.command.BanCommand(this, tb);
        bind("ban", bc);
        bind("unban", bc);
        bind("pardon", bc);
        bind("punish", new PunishCommand(this));
        me.smpcore.command.StaffCommand stc = new me.smpcore.command.StaffCommand(this);
        for (String n : new String[] {"vanish", "freeze", "invsee", "endersee"}) bind(n, stc);
        me.smpcore.command.SpawnCommand sc = new me.smpcore.command.SpawnCommand(this);
        for (String n : new String[] {"spawn", "setspawn", "warp", "warps", "setwarp", "delwarp"}) bind(n, sc);
        PluginCommand pvp = getCommand("pvp");
        if (pvp != null) pvp.setExecutor(tweaks);
        ChatCommands cc = new ChatCommands(this, chat);
        bind("msg", cc);
        bind("reply", cc);
        announce = new me.smpcore.module.AnnounceModule(this);
        me.smpcore.command.AnnounceCommand ac = new me.smpcore.command.AnnounceCommand(this);
        bind("announce", ac);
        bind("broadcast", ac);
    }

    /** Servers upgrading from 1.1.x still have the old gray prefix saved: switch it to the new [ꜱᴍᴘ] one. */
    private void migrateLookAndFeel() {
        String old = getConfig().getString("prefix", "");
        if (old.contains("gradient:#EEEEEE") || old.isBlank()) {
            getConfig().set("prefix", getConfig().getDefaults() != null
                    ? getConfig().getDefaults().getString("prefix") : old);
            saveConfig();
        }
    }

    /** 1.3.0 ban screen: add the time left / appeal lines to a saved screen that doesn't have them. */
    /** 1.4.1: new "out of combat" messages and titles replace the old saved text once. */
    private void migrateCombatMessages() {
        var c = getConfig();
        if (c.getInt("combat.messages-version", 1) >= 3 || getConfig().getDefaults() == null) return;
        for (String k : new String[] {"ended", "ended-title", "ended-subtitle", "tagged-title", "tagged-subtitle", "safe-enter", "safe-leave",
                "rejoin-quit", "rejoin-expired", "rejoin-ok", "rejoin-ok-title", "rejoin-ok-subtitle", "rejoin-late", "rejoin-late-title", "rejoin-late-subtitle"}) {
            String def = getConfig().getDefaults().getString("combat.messages." + k);
            if (def != null) c.set("combat.messages." + k, def);
        }
        c.set("combat.messages-version", 3);
        saveConfig();
    }

    private void migrateBanScreen() {
        var c = getConfig();
        if (c.getInt("tempban.screen-version", 1) >= 2 || getConfig().getDefaults() == null) return;
        c.set("tempban.screen", getConfig().getDefaults().getStringList("tempban.screen"));
        c.set("tempban.screen-version", 2);
        saveConfig();
    }

    /** 1.3.0 blue theme: recolour messages already saved in config.yml and set the new prefix. */
    private void migrateColours() {
        var c = getConfig();
        if (c.getInt("theme-version", 1) >= 2) return;
        String[][] map = {{"#3a5a73", "#052e74"}, {"#6d93ab", "#6981ab"}, {"#abc4d1", "#9babc7"}, {"#e3dccf", "#cdd5e3"},
                {"#f2ede4", "#cdd5e3"}, {"#E8847C", "#FF5555"}, {"#EBB071", "#FFAA33"}, {"#F2DC84", "#FFE45C"},
                {"#97D69A", "#55FF7F"}, {"#8FC3E0", "#55B8FF"}};
        for (String key : c.getKeys(true)) {
            Object v = c.get(key);
            if (!(v instanceof String str) || key.equals("prefix")) continue;
            String n = str;
            for (String[] m : map) n = n.replaceAll("(?i)" + java.util.regex.Pattern.quote(m[0]), m[1]);
            n = n.replace("<#FFAA33>[MSG]", "<#850120>[MSG]");
            if (!n.equals(str)) c.set(key, n);
        }
        if (getConfig().getDefaults() != null) c.set("prefix", getConfig().getDefaults().getString("prefix"));
        if ("Grace".equals(c.getString("grace.scoreboard.title"))) c.set("grace.scoreboard.title", "ɢʀᴀᴄᴇ");
        c.set("theme-version", 2);
        saveConfig();
    }

    /**
     * Before 1.3.0 the Netherite menu used the item-ban list (which also blocked holding, using and picking up).
     * Move armour and tools to the new smithing-only list and clear every netherite entry from the item-ban list.
     */
    private void migrateNetheriteBan() {
        var c = getConfig();
        if (c.getBoolean("netherite-ban.migrated", false)) return;
        List<String> old = c.getStringList("item-ban.banned");
        List<String> keep = new java.util.ArrayList<>();
        java.util.Set<String> moved = new java.util.TreeSet<>(c.getStringList("netherite-ban.items"));
        for (String s : old) {
            String n = s.toUpperCase();
            if (n.startsWith("NETHERITE_") || n.equals("ANCIENT_DEBRIS")) {
                if (!n.endsWith("_INGOT") && !n.endsWith("_SCRAP") && !n.endsWith("_BLOCK") && !n.contains("TEMPLATE")
                        && !n.equals("ANCIENT_DEBRIS")) moved.add(n);
            } else {
                keep.add(s);
            }
        }
        c.set("item-ban.banned", keep);
        c.set("netherite-ban.items", new java.util.ArrayList<>(moved));
        c.set("netherite-ban.migrated", true);
        saveConfig();
    }

    private void bind(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(ex);
            c.setTabCompleter(ex);
        }
    }

    public void openMain(Player p) {
        List<Feature> features = Features.all(this, potions, enchants);
        new FeatureListMenu(this, p, "Settings", () -> features, null).open();
    }

    public void reload() {
        reloadConfig();
        Msg.init(this);
        tempBans.load();
        itemBan.load();
        spawn.load();
        combat.load();
        netherite.load();
        punish.load();
        dragon.applyAll();
        xaero.applyAll();
    }
}
