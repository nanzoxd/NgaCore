package me.smpcore.util;

import java.util.Map;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

/**
 * Vanilla-texture icons for the Enchant and Potion limiter dialogs. No resource pack needed: they are
 * Adventure sprite components (MiniMessage {@code <sprite>}) that the client draws from its own built-in
 * atlases: an item per enchantment (protection = chestplate, blast protection = TNT ...) and the mob effect
 * sprite per potion effect. Needs Minecraft / Paper 1.21.9 or newer; on older servers the icons are skipped.
 */
public final class Icons {
    private Icons() {}

    /** True when the server's Adventure knows sprite (object) components. */
    public static final boolean SUPPORTED = supported();

    private static boolean supported() {
        try {
            Class.forName("net.kyori.adventure.text.object.ObjectContents");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static final String BOOK = "<sprite:items:item/enchanted_book>";

    /** The clock sprite used by the Grace sidebar (12 o'clock), or a plain hourglass on old servers. */
    public static final String CLOCK = SUPPORTED ? "<sprite:items:item/clock_00>" : "\u231b";

    private static final Map<String, String> ENCHANTS = Map.ofEntries(
            Map.entry("protection", "<sprite:items:item/iron_chestplate>"),
            Map.entry("fire_protection", "<sprite:items:item/magma_cream>"),
            Map.entry("feather_falling", "<sprite:items:item/feather>"),
            Map.entry("blast_protection", "<sprite:blocks:block/tnt_side>"),
            Map.entry("projectile_protection", "<sprite:items:item/arrow>"),
            Map.entry("respiration", "<sprite:items:item/turtle_helmet>"),
            Map.entry("aqua_affinity", "<sprite:items:item/water_bucket>"),
            Map.entry("thorns", "<sprite:blocks:block/cactus_side>"),
            Map.entry("depth_strider", "<sprite:items:item/diamond_boots>"),
            Map.entry("frost_walker", "<sprite:blocks:block/ice>"),
            Map.entry("soul_speed", "<sprite:blocks:block/soul_sand>"),
            Map.entry("swift_sneak", "<sprite:items:item/iron_leggings>"),
            Map.entry("binding_curse", "<sprite:items:item/lead>"),
            Map.entry("vanishing_curse", "<sprite:items:item/ender_eye>"),
            Map.entry("sharpness", "<sprite:items:item/diamond_sword>"),
            Map.entry("smite", "<sprite:items:item/rotten_flesh>"),
            Map.entry("bane_of_arthropods", "<sprite:items:item/spider_eye>"),
            Map.entry("knockback", "<sprite:items:item/slime_ball>"),
            Map.entry("fire_aspect", "<sprite:items:item/blaze_powder>"),
            Map.entry("looting", "<sprite:items:item/emerald>"),
            Map.entry("sweeping_edge", "<sprite:items:item/golden_sword>"),
            Map.entry("efficiency", "<sprite:items:item/iron_pickaxe>"),
            Map.entry("silk_touch", "<sprite:items:item/string>"),
            Map.entry("unbreaking", "<sprite:blocks:block/obsidian>"),
            Map.entry("fortune", "<sprite:items:item/diamond>"),
            Map.entry("power", "<sprite:items:item/bow>"),
            Map.entry("punch", "<sprite:items:item/spectral_arrow>"),
            Map.entry("flame", "<sprite:items:item/flint_and_steel>"),
            Map.entry("infinity", "<sprite:items:item/ender_pearl>"),
            Map.entry("luck_of_the_sea", "<sprite:items:item/fishing_rod>"),
            Map.entry("lure", "<sprite:items:item/cod>"),
            Map.entry("loyalty", "<sprite:items:item/trident>"),
            Map.entry("impaling", "<sprite:items:item/prismarine_shard>"),
            Map.entry("riptide", "<sprite:items:item/heart_of_the_sea>"),
            Map.entry("channeling", "<sprite:blocks:block/lightning_rod>"),
            Map.entry("multishot", "<sprite:items:item/crossbow_standby>"),
            Map.entry("quick_charge", "<sprite:items:item/crossbow_standby>"),
            Map.entry("piercing", "<sprite:items:item/iron_nugget>"),
            Map.entry("density", "<sprite:items:item/mace>"),
            Map.entry("breach", "<sprite:items:item/breeze_rod>"),
            Map.entry("wind_burst", "<sprite:items:item/wind_charge>"),
            Map.entry("mending", "<sprite:items:item/experience_bottle>"),
            Map.entry("lunge", "<sprite:items:item/iron_spear>"));

    private static final Map<String, String> EFFECTS = Map.ofEntries(
            Map.entry("absorption", "<sprite:gui:mob_effect/absorption>"),
            Map.entry("bad_omen", "<sprite:gui:mob_effect/bad_omen>"),
            Map.entry("blindness", "<sprite:gui:mob_effect/blindness>"),
            Map.entry("breath_of_the_nautilus", "<sprite:gui:mob_effect/breath_of_the_nautilus>"),
            Map.entry("conduit_power", "<sprite:gui:mob_effect/conduit_power>"),
            Map.entry("darkness", "<sprite:gui:mob_effect/darkness>"),
            Map.entry("dolphins_grace", "<sprite:gui:mob_effect/dolphins_grace>"),
            Map.entry("fire_resistance", "<sprite:gui:mob_effect/fire_resistance>"),
            Map.entry("glowing", "<sprite:gui:mob_effect/glowing>"),
            Map.entry("haste", "<sprite:gui:mob_effect/haste>"),
            Map.entry("health_boost", "<sprite:gui:mob_effect/health_boost>"),
            Map.entry("hero_of_the_village", "<sprite:gui:mob_effect/hero_of_the_village>"),
            Map.entry("hunger", "<sprite:gui:mob_effect/hunger>"),
            Map.entry("infested", "<sprite:gui:mob_effect/infested>"),
            Map.entry("instant_damage", "<sprite:gui:mob_effect/instant_damage>"),
            Map.entry("instant_health", "<sprite:gui:mob_effect/instant_health>"),
            Map.entry("invisibility", "<sprite:gui:mob_effect/invisibility>"),
            Map.entry("jump_boost", "<sprite:gui:mob_effect/jump_boost>"),
            Map.entry("levitation", "<sprite:gui:mob_effect/levitation>"),
            Map.entry("luck", "<sprite:gui:mob_effect/luck>"),
            Map.entry("mining_fatigue", "<sprite:gui:mob_effect/mining_fatigue>"),
            Map.entry("nausea", "<sprite:gui:mob_effect/nausea>"),
            Map.entry("night_vision", "<sprite:gui:mob_effect/night_vision>"),
            Map.entry("oozing", "<sprite:gui:mob_effect/oozing>"),
            Map.entry("poison", "<sprite:gui:mob_effect/poison>"),
            Map.entry("raid_omen", "<sprite:gui:mob_effect/raid_omen>"),
            Map.entry("regeneration", "<sprite:gui:mob_effect/regeneration>"),
            Map.entry("resistance", "<sprite:gui:mob_effect/resistance>"),
            Map.entry("saturation", "<sprite:gui:mob_effect/saturation>"),
            Map.entry("slow_falling", "<sprite:gui:mob_effect/slow_falling>"),
            Map.entry("slowness", "<sprite:gui:mob_effect/slowness>"),
            Map.entry("speed", "<sprite:gui:mob_effect/speed>"),
            Map.entry("strength", "<sprite:gui:mob_effect/strength>"),
            Map.entry("trial_omen", "<sprite:gui:mob_effect/trial_omen>"),
            Map.entry("unluck", "<sprite:gui:mob_effect/unluck>"),
            Map.entry("water_breathing", "<sprite:gui:mob_effect/water_breathing>"),
            Map.entry("weakness", "<sprite:gui:mob_effect/weakness>"),
            Map.entry("weaving", "<sprite:gui:mob_effect/weaving>"),
            Map.entry("wind_charged", "<sprite:gui:mob_effect/wind_charged>"),
            Map.entry("wither", "<sprite:gui:mob_effect/wither>"));

    private static String wrap(String sprite) { return "<color:#ffffff>" + sprite + "</color>"; }

    /** MiniMessage icon for an enchantment (enchanted book when it has no own icon), or "" if unsupported. */
    public static String enchant(Enchantment e) {
        return SUPPORTED ? wrap(ENCHANTS.getOrDefault(e.getKey().getKey(), BOOK)) : "";
    }

    /** MiniMessage icon for a potion effect, or "" when it has none. */
    public static String effect(PotionEffectType t) {
        String g = SUPPORTED ? EFFECTS.get(t.getKey().getKey()) : null;
        return g == null ? "" : wrap(g);
    }
}
