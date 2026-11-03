package me.smpcore.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.module.CooldownModule;
import me.smpcore.module.DamageLimiterModule;
import me.smpcore.module.EnchantModule;
import me.smpcore.module.PotionModule;
import me.smpcore.util.Items;
import me.smpcore.util.Lore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/** Factory + small menus. */
public final class Menus {
    private Menus() {}

    public static Gui damage(SMPCore plugin, Player p, Supplier<Gui> parent) {
        List<ValueMenu.Row> rows = new ArrayList<>();
        for (DamageLimiterModule.Limiter l : DamageLimiterModule.LIMITERS) {
            rows.add(new ValueMenu.Row("damage-limiters.limits." + l.key(), "max", l.icon(), l.label(), 20, 1, 1000));
        }
        return new ValueMenu(plugin, p, "Damage Limiters", "damage-limiters.enabled", "Damage limiters", rows,
                v -> Theme.PLAIN_NUM(v) + " HP (" + Theme.PLAIN_NUM(v / 2) + " hearts)", parent);
    }

    public static Gui cooldowns(SMPCore plugin, Player p, Supplier<Gui> parent) {
        List<ValueMenu.Row> rows = new ArrayList<>();
        for (CooldownModule.Cd c : CooldownModule.ITEMS) {
            rows.add(new ValueMenu.Row("cooldowns.items." + c.key(), "seconds", c.icon(), c.label(), 10, 1, 86400));
        }
        return new ValueMenu(plugin, p, "Cooldowns", "cooldowns.enabled", "Cooldowns", rows,
                v -> Theme.PLAIN_NUM(v) + "s", parent);
    }

    public static Gui dimensions(SMPCore plugin, Player p, Supplier<Gui> parent) {
        return new ToggleMenu(plugin, p, "Dimension Ban", List.of(
                new ToggleMenu.Toggle(Material.NETHERRACK, "Ban the Nether", "dimension-ban.nether", "Players can't enter the Nether."),
                new ToggleMenu.Toggle(Material.END_PORTAL_FRAME, "Ban the End", "dimension-ban.end", "Players can't enter the End."),
                new ToggleMenu.Toggle(Material.ENDER_PEARL, "Block teleports", "dimension-ban.block-teleports",
                        "Also blocks commands and plugins from sending players into a closed dimension.")), parent);
    }

    public static Gui itemBanOptions(SMPCore plugin, Player p, Supplier<Gui> parent) {
        return new ToggleMenu(plugin, p, "Item Ban Options", List.of(
                new ToggleMenu.Toggle(Material.BARRIER, "Item ban", "item-ban.enabled", "Master switch (also controls Netherite ban)."),
                new ToggleMenu.Toggle(Material.HOPPER, "Remove from inventories", "item-ban.remove-from-inventories", "Banned items are deleted every 5 seconds."),
                new ToggleMenu.Toggle(Material.IRON_SWORD, "Block use", "item-ban.block-use", "Right-clicking a banned item does nothing."),
                new ToggleMenu.Toggle(Material.GRASS_BLOCK, "Block placing", "item-ban.block-placing", "Banned blocks can't be placed.")), parent);
    }

    public static Gui deathBan(SMPCore plugin, Player p, Supplier<Gui> parent) {
        return new ToggleMenu(plugin, p, "Death Ban", List.of(
                new ToggleMenu.Toggle(Material.SKELETON_SKULL, "Death ban", "death-ban.enabled", "Players are temp-banned when they die."),
                new ToggleMenu.Toggle(Material.DIAMOND_SWORD, "PvP deaths only", "death-ban.pvp-only", "Only ban when another player gets the kill."),
                new ToggleMenu.Toggle(Material.ENDER_EYE, "Death spectator", "death-ban.spectator", "Instead of a ban, the player watches in spectator mode until the duration ends.")), parent);
    }

    public static Gui chat(SMPCore plugin, Player p, Supplier<Gui> parent) {
        return new ToggleMenu(plugin, p, "Chat Moderation", List.of(
                new ToggleMenu.Toggle(Material.WRITABLE_BOOK, "Chat moderation", "chat.enabled", "Master switch."),
                new ToggleMenu.Toggle(Material.OAK_SIGN, "Chat format", "chat.format.enabled", "Use the format from config.yml."),
                new ToggleMenu.Toggle(Material.CLOCK, "Cooldown", "chat.cooldown.enabled", "Delay between messages."),
                new ToggleMenu.Toggle(Material.PAPER, "Duplicate protection", "chat.duplicate.enabled", "Blocks the same message twice in a row."),
                new ToggleMenu.Toggle(Material.NAME_TAG, "Mentions", "chat.mentions.enabled", "@name highlight with a sound."),
                new ToggleMenu.Toggle(Material.ANVIL, "Caps filter", "chat.caps.enabled", "Lowercases shouting."),
                new ToggleMenu.Toggle(Material.CHAIN, "Link filter", "chat.links.enabled", "Blocks links."),
                new ToggleMenu.Toggle(Material.BARRIER, "Blocked words", "chat.blocked-words.enabled", "Word list lives in config.yml."),
                new ToggleMenu.Toggle(Material.BELL, "Private messages", "chat.private-messages.enabled", "/msg and /reply."),
                new ToggleMenu.Toggle(Material.OAK_DOOR, "Join / leave messages", "chat.join-leave.enabled", "Custom join and leave text."),
                new ToggleMenu.Toggle(Material.SKELETON_SKULL, "Death messages", "chat.death-messages.enabled", "Hover a name to see combat stats."),
                new ToggleMenu.Toggle(Material.ENCHANTED_BOOK, "Hide advancements", "chat.hide-advancements", "Advancement toasts stay, chat spam goes.")), parent);
    }

    // ------------------------------------------------------------------ netherite

    public static final class Netherite extends Gui {
        private static final Material[] ALL = {
                Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                Material.NETHERITE_SWORD, Material.NETHERITE_AXE, Material.NETHERITE_PICKAXE, Material.NETHERITE_SHOVEL,
                Material.NETHERITE_HOE, Items.mat("NETHERITE_SPEAR", Material.NETHERITE_SWORD), Material.NETHERITE_INGOT,
                Material.NETHERITE_SCRAP, Material.NETHERITE_BLOCK, Material.ANCIENT_DEBRIS,
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE};
        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        private final Supplier<Gui> parent;

        public Netherite(SMPCore plugin, Player viewer, Supplier<Gui> parent) {
            super(plugin, viewer, 4, "Netherite Ban");
            this.parent = parent;
        }

        @Override protected void build() {
            border();
            backButton(parent);
            List<Material> mats = new ArrayList<>();
            for (Material m : ALL) if (!mats.contains(m)) mats.add(m);
            for (int i = 0; i < mats.size() && i < SLOTS.length; i++) {
                Material m = mats.get(i);
                boolean banned = plugin.itemBan.banned().contains(m);
                String extra = m == Material.ANCIENT_DEBRIS ? "Banned = can't be mined." : "Banned = can't be crafted, held or picked up.";
                set(SLOTS[i], Items.of(m).name(Theme.pretty(m.name()))
                        .lore(Lore.opt("Ban", banned, !banned)).glow(banned).build(), e -> {
                    if (e.isRightClick()) plugin.itemBan.set(m, false);
                    else if (e.isLeftClick()) plugin.itemBan.set(m, true);
                    refresh();
                });
            }
            set(30, Items.of(Material.LIME_DYE).name(Theme.green("Allow all")).build(), e -> {
                for (Material m : ALL) plugin.itemBan.banned().remove(m);
                plugin.itemBan.set(Material.NETHERITE_INGOT, false);
                refresh();
            });
            set(32, Items.of(Material.RED_DYE).name(Theme.red("Ban all")).build(), e -> {
                for (Material m : ALL) plugin.itemBan.banned().add(m);
                plugin.itemBan.set(Material.NETHERITE_INGOT, true);
                refresh();
            });
            toggleButton(31, Material.LEVER, "Item ban module", "item-ban.enabled", "Master switch for all banned items.");
        }
    }

    // ------------------------------------------------------------------ item ban list

    public static final class ItemBanList extends PagedGui<Material> {
        private final Supplier<Gui> parent;

        public ItemBanList(SMPCore plugin, Player viewer, Supplier<Gui> parent) {
            super(plugin, viewer, "Item Ban");
            this.parent = parent;
        }

        @Override protected Supplier<Gui> parent() { return parent; }

        @Override protected List<Material> entries() {
            List<Material> l = new ArrayList<>(plugin.itemBan.banned());
            l.sort(Comparator.comparing(Enum::name));
            return l;
        }

        @Override protected ItemStack icon(Material m) {
            return Items.of(m).name(Theme.red(Theme.pretty(m.name())))
                    .lore(Theme.blue("Click") + " to allow again").glow(true).build();
        }

        @Override protected void onClick(Material m, InventoryClickEvent e) {
            plugin.itemBan.set(m, false);
            refresh();
        }

        @Override protected void extra() {
            set(46, Items.of(Material.HOPPER).name("Ban held item")
                    .lore("Hold an item and click here.", "Or use " + Theme.blue("/smp itemban add") + ".").build(), e -> {
                Material m = viewer.getInventory().getItemInMainHand().getType();
                if (m.isAir()) {
                    Msg.send(viewer, "Hold the item you want to ban.");
                    return;
                }
                plugin.itemBan.set(m, true);
                refresh();
            });
            set(51, Items.of(Material.COMPARATOR).name("Options").build(),
                    e -> itemBanOptions(plugin, viewer, () -> this).open());
        }
    }

    // ------------------------------------------------------------------ extra menus

    public static Gui dragon(SMPCore plugin, Player p, Supplier<Gui> parent) {
        List<ValueMenu.Row> rows = List.of(new ValueMenu.Row("dragon-health", "health", Material.DRAGON_HEAD,
                "Ender Dragon health", 200, 50, 1024));
        return new ValueMenu(plugin, p, "Dragon Health", "dragon-health.enabled", "Dragon health", rows,
                v -> Theme.PLAIN_NUM(v) + " HP", parent);
    }

    // ------------------------------------------------------------------ spear ban

    public static final class Spears extends Gui {
        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        private final Supplier<Gui> parent;

        public Spears(SMPCore plugin, Player viewer, Supplier<Gui> parent) {
            super(plugin, viewer, 4, "Spear Ban");
            this.parent = parent;
        }

        @Override protected void build() {
            border();
            backButton(parent);
            Material[] sp = Items.spears();
            if (sp.length == 0) {
                set(22, Items.of(Material.BARRIER).name("No spears").lore("This server version has no spear items.").build());
            }
            for (int i = 0; i < sp.length && i < SLOTS.length; i++) {
                Material m = sp[i];
                boolean banned = plugin.itemBan.banned().contains(m);
                set(SLOTS[i], Items.of(m).name(Theme.pretty(m.name()))
                        .lore(Lore.opt("Ban", banned, !banned)).glow(banned).build(), e -> {
                    if (e.isRightClick()) plugin.itemBan.set(m, false);
                    else if (e.isLeftClick()) plugin.itemBan.set(m, true);
                    refresh();
                });
            }
            set(30, Items.of(Material.LIME_DYE).name(Theme.green("Allow all spears")).build(), e -> {
                for (Material m : sp) plugin.itemBan.set(m, false);
                refresh();
            });
            set(32, Items.of(Material.RED_DYE).name(Theme.red("Ban all spears")).build(), e -> {
                for (Material m : sp) plugin.itemBan.set(m, true);
                refresh();
            });
            toggleButton(31, Material.LEVER, "Item ban module", "item-ban.enabled", "Spear bans use the item ban engine.");
        }
    }
}
