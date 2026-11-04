package me.smpcore.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.smpcore.SMPCore;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Whitelist for custom weapons. When a base item (for example every iron spear) is banned, an item that
 * matches a whitelist rule is still allowed: custom spears from your own plugin or resource pack.
 * A rule matches by material plus every detail it recorded: item model, custom model data, name and
 * text tags (PDC) that custom-item plugins write on their items.
 */
public final class WeaponWhitelist {
    public record Rule(Material material, String model, Float cmd, String name, Map<String, String> tags) {
        public String label() {
            return name != null ? name : model != null ? model : cmd != null ? "Model data " + cmd.intValue() : material.name();
        }
    }

    private final SMPCore plugin;
    private final List<Rule> rules = new ArrayList<>();

    public WeaponWhitelist(SMPCore plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        rules.clear();
        for (Map<?, ?> m : plugin.getConfig().getMapList("item-ban.whitelist")) {
            Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
            if (mat == null) continue;
            Object cmd = m.get("cmd");
            Map<String, String> tags = new LinkedHashMap<>();
            if (m.get("tags") instanceof Map<?, ?> t) t.forEach((k, v) -> tags.put(String.valueOf(k), String.valueOf(v)));
            rules.add(new Rule(mat, m.get("model") == null ? null : String.valueOf(m.get("model")),
                    cmd instanceof Number n ? n.floatValue() : null, m.get("name") == null ? null : String.valueOf(m.get("name")), tags));
        }
    }

    private void save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Rule r : rules) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("material", r.material().name());
            if (r.model() != null) m.put("model", r.model());
            if (r.cmd() != null) m.put("cmd", r.cmd());
            if (r.name() != null) m.put("name", r.name());
            if (!r.tags().isEmpty()) m.put("tags", r.tags());
            out.add(m);
        }
        plugin.getConfig().set("item-ban.whitelist", out);
        plugin.saveConfig();
    }

    public List<Rule> rules() {
        return rules;
    }

    private static String nameOf(ItemMeta meta) {
        if (meta.hasDisplayName() && meta.displayName() != null) return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        if (meta.hasItemName()) return PlainTextComponentSerializer.plainText().serialize(meta.itemName());
        return null;
    }

    private static Float cmdOf(ItemMeta meta) {
        if (!meta.hasCustomModelDataComponent()) return null;
        List<Float> f = meta.getCustomModelDataComponent().getFloats();
        return f.isEmpty() ? null : f.get(0);
    }

    private static Map<String, String> tagsOf(ItemMeta meta) {
        Map<String, String> out = new LinkedHashMap<>();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey k : pdc.getKeys()) {
            String v = pdc.get(k, PersistentDataType.STRING);
            if (v != null) out.put(k.toString(), v);
        }
        return out;
    }

    /** Builds a rule from an item, or null when it is a plain vanilla item (nothing to tell it apart by). */
    public Rule ruleOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        ItemMeta meta = it.getItemMeta();
        String model = meta.hasItemModel() ? meta.getItemModel().toString() : null;
        Float cmd = cmdOf(meta);
        String name = nameOf(meta);
        Map<String, String> tags = tagsOf(meta);
        if (model == null && cmd == null && name == null && tags.isEmpty()) return null;
        return new Rule(it.getType(), model, cmd, name, tags);
    }

    /** @return the rule that was added, or null when the item can't be told apart from a vanilla one. */
    public Rule add(ItemStack it) {
        Rule r = ruleOf(it);
        if (r == null) return null;
        if (!rules.contains(r)) {
            rules.add(r);
            save();
        }
        return r;
    }

    public void remove(int index) {
        if (index >= 0 && index < rules.size()) {
            rules.remove(index);
            save();
        }
    }

    public boolean matches(ItemStack it) {
        if (rules.isEmpty() || it == null || !it.hasItemMeta()) return false;
        ItemMeta meta = it.getItemMeta();
        String model = meta.hasItemModel() ? meta.getItemModel().toString() : null;
        Float cmd = cmdOf(meta);
        String name = nameOf(meta);
        Map<String, String> tags = null;
        for (Rule r : rules) {
            if (r.material() != it.getType()) continue;
            if (r.model() != null && !r.model().equals(model)) continue;
            if (r.cmd() != null && !r.cmd().equals(cmd)) continue;
            if (r.name() != null && !r.name().equals(name)) continue;
            if (!r.tags().isEmpty()) {
                if (tags == null) tags = tagsOf(meta);
                if (!tags.entrySet().containsAll(r.tags().entrySet())) continue;
            }
            return true;
        }
        return false;
    }
}
