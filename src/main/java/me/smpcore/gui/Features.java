package me.smpcore.gui;

import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.gui.Feature.Category;
import me.smpcore.module.EnchantModule;
import me.smpcore.module.PotionModule;
import me.smpcore.util.Items;
import org.bukkit.Material;

/** Every feature in one place, like the reference plugin's "All Features" list. */
public final class Features {
    private Features() {}

    private static Feature sw(Category c, Material m, String name, String desc, String key) {
        return new Feature(c, m, name, desc, "tweaks." + key, null);
    }

    /** Death Ban: left click toggles "spectator after death", right click toggles "ban after death". */
    private static Feature.Custom deathBan() {
        return new Feature.Custom() {
            private boolean spec(org.bukkit.configuration.file.FileConfiguration c) {
                return c.getBoolean("death-ban.enabled") && c.getBoolean("death-ban.spectator");
            }

            private boolean ban(org.bukkit.configuration.file.FileConfiguration c) {
                return c.getBoolean("death-ban.enabled") && !c.getBoolean("death-ban.spectator");
            }

            @Override public List<String> lore(org.bukkit.configuration.file.FileConfiguration c) {
                return List.of(me.smpcore.util.Lore.opt("Spectator after death", spec(c), true),
                        me.smpcore.util.Lore.opt("Ban after death", ban(c), false));
            }

            @Override public void click(org.bukkit.configuration.file.FileConfiguration c, boolean left) {
                boolean on = left ? spec(c) : ban(c);
                c.set("death-ban.enabled", !on);
                if (!on) c.set("death-ban.spectator", left);
            }
        };
    }

    /** Dimension Ban: left click toggles the Nether ban, right click toggles the End ban. */
    private static Feature.Custom dimensions() {
        return new Feature.Custom() {
            @Override public List<String> lore(org.bukkit.configuration.file.FileConfiguration c) {
                return List.of(me.smpcore.util.Lore.opt("Nether ban", c.getBoolean("dimension-ban.nether"), true),
                        me.smpcore.util.Lore.opt("End ban", c.getBoolean("dimension-ban.end"), false));
            }

            @Override public void click(org.bukkit.configuration.file.FileConfiguration c, boolean left) {
                String k = left ? "dimension-ban.nether" : "dimension-ban.end";
                c.set(k, !c.getBoolean(k));
            }
        };
    }

    /** Xaero's Minimap: left click toggles fair play, right click toggles "disable minimap" (for everyone). */
    private static Feature.Custom xaero(SMPCore plugin) {
        return new Feature.Custom() {
            @Override public List<String> lore(org.bukkit.configuration.file.FileConfiguration c) {
                return List.of(me.smpcore.util.Lore.opt("Fair play mode", c.getBoolean("xaero.fair-mode"), true),
                        me.smpcore.util.Lore.opt("Disable minimap", c.getBoolean("xaero.disable"), false),
                        me.smpcore.util.Theme.c(me.smpcore.util.Theme.MID, "Disable wins over fair play."));
            }

            @Override public void click(org.bukkit.configuration.file.FileConfiguration c, boolean left) {
                String k = left ? "xaero.fair-mode" : "xaero.disable";
                c.set(k, !c.getBoolean(k));
                plugin.xaero.applyAll();
            }
        };
    }

    public static List<Feature> all(SMPCore plugin, PotionModule potions, EnchantModule enchants) {
        return List.of(
                // PVP
                new Feature(Category.PVP, Material.MACE, "Damage Limiters", "Cap damage per weapon and explosive.", "damage-limiters.enabled",
                        (p, back) -> Menus.damage(plugin, p, back).open()),
                new Feature(Category.PVP, Material.CLOCK, "Cooldowns", "Item cooldowns shown on the item.", "cooldowns.enabled",
                        (p, back) -> Menus.cooldowns(plugin, p, back).open()),
                new Feature(Category.PVP, Items.mat("IRON_SPEAR", Material.TRIDENT), "Spear Ban", "Ban spears, allow the ones you pick.", null,
                        (p, back) -> new Menus.Spears(plugin, p, back).open()),
                new Feature(Category.PVP, Material.NETHERITE_CHESTPLATE, "Netherite Ban", "Ban netherite upgrades at the Smithing Table.", "netherite-ban.enabled",
                        (p, back) -> new Menus.Netherite(plugin, p, back).open()),
                new Feature(Category.PVP, Material.POTION, "Potion Limiter", "Dialog: search an effect, set a limit.", "potion-limiter.enabled",
                        (p, back) -> LimiterDialogs.potions(plugin, p, potions, () -> back.get().open())),
                new Feature(Category.PVP, Material.ENCHANTED_BOOK, "Enchant Limiter", "Dialog: search an enchant, set a limit.", "enchant-limiter.enabled",
                        (p, back) -> LimiterDialogs.enchants(plugin, p, enchants, () -> back.get().open())),
                new Feature(Category.PVP, Material.IRON_SWORD, "Combat", "Combat timer, command blocking, safe zone.", "combat.enabled",
                        (p, back) -> Menus.combat(plugin, p, back).open())
                        .hint("/smp combat seconds <n>|Combat time (default 20)", "/smp safezone add <name> <radius>|Safe zone where you stand"),
                sw(Category.PVP, Material.DIAMOND_SWORD, "Disable PvP", "No damage between players.", "pvp-off"),
                sw(Category.PVP, Material.LEATHER_CHESTPLATE, "Naked Protection", "Players with no armor can't be hit by players.", "naked-protection"),
                new Feature(Category.PVP, Material.CLOCK, "Grace", "Resistance IV, Saturation and a 30:00 clock.", "grace.enabled",
                        (p, back) -> Menus.grace(plugin, p, back).open()),
                sw(Category.PVP, Material.SUNFLOWER, "AFK Protection", "AFK players take no damage.", "afk-protection"),
                sw(Category.PVP, Material.TIPPED_ARROW, "Ban Tipped Arrows", "Tipped arrows can't be shot.", "ban-tipped-arrows"),
                sw(Category.PVP, Material.TOTEM_OF_UNDYING, "Totem Keeps Effects", "Potion effects survive a totem pop.", "totem-keep-effects"),
                sw(Category.PVP, Material.TOTEM_OF_UNDYING, "Evoker Totem Drop", "On: evokers drop a totem. Off: they drop none.", "evoker-totem-drop"),
                sw(Category.PVP, Material.END_CRYSTAL, "Ban Crystal PvP", "End crystals can't hurt players.", "ban-crystal-pvp"),
                sw(Category.PVP, Material.WHITE_BED, "Ban Bed Bombing", "Beds can't be used in the Nether or the End.", "ban-bed-bombing"),
                sw(Category.PVP, Material.ENDER_PEARL, "Ban Ender Pearls", "Pearls can't be thrown, but players can keep them.", "ban-pearl"),
                sw(Category.PVP, Material.TNT_MINECART, "Ban Minecart TNT", "TNT minecarts can't spawn.", "ban-minecart"),
                sw(Category.PVP, Material.VILLAGER_SPAWN_EGG, "Ban Villager Killing", "Players can't hurt villagers.", "ban-villager-killing"),
                // Administrator
                new Feature(Category.ADMIN, Material.BARRIER, "Item Ban", "Ban any item you want.", null,
                        (p, back) -> new Menus.ItemBanList(plugin, p, back).open()),
                new Feature(Category.ADMIN, Material.MACE, "Ban Animation", "A giant mace slams banned players.", "tempban.animation.enabled", null),
                new Feature(Category.ADMIN, Material.IRON_BARS, "Punishments", "Ban, mute, kick or warn a player.", null,
                        (p, back) -> new TempBanGuis.PlayerPicker(plugin, p, back).open()),
                new Feature(Category.ADMIN, Material.WRITABLE_BOOK, "Chat Moderation", "Filters, mentions, messages, sounds.", "chat.enabled",
                        (p, back) -> Menus.chat(plugin, p, back).open()),
                new Feature(Category.ADMIN, Material.BELL, "Announcements", "Animated title, chat line and sounds.", "announce.enabled", null)
                        .hint("/announce <message>|Title + chat + sounds for everyone", "/broadcast <message>|Chat only, no title"),
                new Feature(Category.ADMIN, Material.FILLED_MAP, "Xaero Minimap", "", null, null, xaero(plugin)),
                new Feature(Category.ADMIN, Material.SKELETON_SKULL, "Death Ban", "", "death-ban.enabled", null, deathBan()),
                new Feature(Category.ADMIN, Material.DRAGON_HEAD, "Dragon Health", "Set the Ender Dragon's health.", "dragon-health.enabled",
                        (p, back) -> Menus.dragon(plugin, p, back).open()),
                sw(Category.ADMIN, Material.BARRIER, "Maintenance Mode", "Only smpcore.maintenance.bypass can join.", "maintenance"),
                sw(Category.ADMIN, Material.ZOMBIE_HEAD, "Mob Disabler", "Blocks spawns listed in config.yml.", "mob-disabler"),
                sw(Category.ADMIN, Material.GRASS_BLOCK, "Block Placing", "Players can't place blocks.", "block-placing"),
                sw(Category.ADMIN, Material.IRON_PICKAXE, "Block Breaking", "Players can't break blocks.", "block-breaking"),
                sw(Category.ADMIN, Material.PHANTOM_MEMBRANE, "Disable Phantoms", "Phantoms never spawn.", "disable-phantoms"),
                sw(Category.ADMIN, Material.ZOMBIE_SPAWN_EGG, "No Mob Spawning", "No natural mob spawns.", "no-mob-spawning"),
                sw(Category.ADMIN, Material.CREEPER_HEAD, "No Mob Griefing", "Mobs can't change or blow up blocks.", "no-mob-griefing"),
                sw(Category.ADMIN, Material.WRITTEN_BOOK, "Book Limit", "Caps book pages and page length.", "book-limit"),
                sw(Category.ADMIN, Material.SPAWNER, "Ban Spawner Mining", "Monster spawners can't be broken.", "ban-spawner-mining"),
                sw(Category.ADMIN, Material.PLAYER_HEAD, "Player Head Drop", "Players drop their head on death.", "player-head-drop"),
                // World
                new Feature(Category.WORLD, Material.END_PORTAL_FRAME, "Dimension Ban", "", null, null, dimensions()),
                sw(Category.WORLD, Material.RED_BED, "One Player Sleep", "One sleeping player skips the night.", "one-player-sleep"),
                sw(Category.WORLD, Material.COMPASS, "Hide Locator Bar", "Turns the locator bar off in every world.", "hide-locator-bar"),
                sw(Category.WORLD, Material.SAND, "No Falling Blocks", "Sand and gravel stay put.", "no-falling-blocks"),
                sw(Category.WORLD, Material.FLINT_AND_STEEL, "No Fire Spread", "Fire can't spread or burn blocks.", "no-fire-spread"),
                sw(Category.WORLD, Material.TNT, "No Explosion Block Damage", "Explosions hurt but don't break blocks.", "no-explosion-grief"),
                sw(Category.WORLD, Material.VAULT, "Disable Vaults", "Vault blocks can't be opened.", "disable-vaults"),
                sw(Category.WORLD, Material.ARMOR_STAND, "Armor Stand Arms", "New armor stands get arms.", "armor-stand-arms"),
                sw(Category.WORLD, Material.CHEST, "Immortal Death Drops", "Death drops never despawn or burn.", "immortal-death-drops"),
                sw(Category.WORLD, Material.FIRE_CHARGE, "Ban Explosives", "TNT, creepers, crystals and anchors do nothing.", "ban-explosives"),
                sw(Category.WORLD, Material.BUCKET, "Ban Draining", "Buckets can't pick up water or lava.", "ban-draining"),
                sw(Category.WORLD, Material.BEDROCK, "Void Protection", "Players who fall into the void are lifted back up.", "void-protection"),
                sw(Category.WORLD, Material.COBWEB, "Cobweb Decay", "Placed cobwebs disappear after a few seconds.", "cobweb-decay"),
                // Util
                new Feature(Category.WORLD, Material.COMPASS, "Random TP", "New players start at a random spot.", "random-tp.enabled",
                        (p, back) -> new Menus.RandomTp(plugin, p, back).open()),
                new Feature(Category.UTIL, Material.EMERALD, "Insta Restock", "Villagers and wandering traders restock instantly.", "instarestock.enabled", null),
                sw(Category.UTIL, Material.ANVIL, "No \"Too Expensive\"", "Removes the anvil level cap.", "anvil-no-limit"),
                sw(Category.UTIL, Material.NOTE_BLOCK, "Kill Sound", "Killers hear a sound on every player kill.", "kill-sound"),
                sw(Category.UTIL, Material.EXPERIENCE_BOTTLE, "XP Clumps", "Nearby XP orbs merge into one.", "xp-clumps"),
                sw(Category.UTIL, Material.APPLE, "Apple Drops", "Oak leaves drop apples more often.", "apple-drops"),
                sw(Category.UTIL, Material.HEAVY_CORE, "Heavy Core Drop", "Ravagers can drop a heavy core.", "heavy-core-drop"));
    }
}
