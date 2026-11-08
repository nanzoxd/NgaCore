package me.smpcore.module;

import io.papermc.paper.event.player.PlayerTradeEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;

/**
 * Insta Restock (like the InstaRestock plugin): villagers and wandering traders get every trade
 * back as soon as a player opens the trade screen or finishes a trade. Switch it on or off in /smp.
 */
public final class InstaRestockModule implements Listener {
    private final SMPCore plugin;
    private final Map<UUID, Long> last = new HashMap<>();

    public InstaRestockModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    private boolean eligible(AbstractVillager v) {
        if (!c().getBoolean("instarestock.enabled")) return false;
        if (v instanceof WanderingTrader && !c().getBoolean("instarestock.wandering-traders", true)) return false;
        if (v instanceof Villager vi && c().getStringList("instarestock.villager-blacklist").stream()
                .anyMatch(s -> s.equalsIgnoreCase(vi.getProfession().getKey().getKey()))) return false;
        return true;
    }

    /** Resets uses on every trade (and the price penalty if configured). Respects the optional cooldown. */
    private void restock(AbstractVillager v) {
        if (!eligible(v)) return;
        long wait = (long) (c().getDouble("instarestock.cooldown-seconds", 0) * 1000);
        long now = System.currentTimeMillis();
        Long prev = last.get(v.getUniqueId());
        if (wait > 0 && prev != null && now - prev < wait) return;
        boolean noPenalty = c().getBoolean("instarestock.disable-price-penalty", true);
        List<MerchantRecipe> recipes = v.getRecipes();
        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe r = v.getRecipe(i);
            r.setUses(0);
            if (noPenalty) r.setDemand(0);
            v.setRecipe(i, r);
        }
        last.put(v.getUniqueId(), now);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (e.getInventory() instanceof MerchantInventory mi && mi.getMerchant() instanceof AbstractVillager v) restock(v);
    }

    @EventHandler
    public void onTrade(PlayerTradeEvent e) {
        AbstractVillager v = e.getVillager();
        // next tick, so the trade that just happened is counted first and then wiped
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (v.isValid()) restock(v);
        });
    }
}
