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
import me.smpcore.module.ItemBanModule;
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
    public PunishManager punish;
    private DragonModule dragon;
    public ItemBanModule itemBan;
    private PotionModule potions;
    private EnchantModule enchants;
    public XaeroModule xaero;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateLookAndFeel();
        Msg.init(this);

        prompts = new PromptManager(this);
        tempBans = new TempBanManager(this);
        punish = new PunishManager(this);
        dragon = new DragonModule(this);
        TweaksModule tweaks = new TweaksModule(this);
        itemBan = new ItemBanModule(this);
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
        pm.registerEvents(potions, this);
        pm.registerEvents(enchants, this);
        pm.registerEvents(chat, this);
        pm.registerEvents(xaero, this);
        pm.registerEvents(new DamageLimiterModule(this), this);
        pm.registerEvents(new CooldownModule(this), this);
        pm.registerEvents(new DimensionModule(this), this);
        pm.registerEvents(new DeathBanModule(this), this);

        bind("smp", new SmpCommand(this));
        bind("settings", new SmpCommand(this));
        bind("tempban", new TempBanCommand(this));
        bind("punish", new PunishCommand(this));
        PluginCommand pvp = getCommand("pvp");
        if (pvp != null) pvp.setExecutor(tweaks);
        ChatCommands cc = new ChatCommands(this, chat);
        bind("msg", cc);
        bind("reply", cc);
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
        punish.load();
        dragon.applyAll();
        xaero.applyAll();
    }
}
