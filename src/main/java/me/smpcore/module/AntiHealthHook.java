package me.smpcore.module;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import java.util.List;
import me.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Wolf;
import org.bukkit.event.inventory.InventoryType;

/**
 * Anti Health Indicator (like the AntiHealthIndicator plugin). Health bars from mods and hacked clients read the
 * health of other entities from the entity data the server sends. This hides it by filtering those packets with
 * ProtocolLib. Also hides absorption, air, your own saturation and the enchanting seed. Needs ProtocolLib; every
 * switch is read live from the config (anti-health.*), so the menu works instantly.
 */
public final class AntiHealthHook {
    /** LivingEntity health in the entity data. */
    private static final int HEALTH_INDEX = 9;
    /** Entity air ticks. */
    private static final int AIR_INDEX = 1;

    private final SMPCore plugin;

    public AntiHealthHook(SMPCore plugin) {
        this.plugin = plugin;
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (on("enabled")) filterMetadata(event);
            }
        });
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGH, PacketType.Play.Server.UPDATE_HEALTH) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!on("enabled") || !on("saturation")) return;
                PacketContainer packet = event.getPacket().deepClone();
                // floats: health, saturation. Saturation can't be read from the hunger bar in vanilla.
                float sat = packet.getFloat().readSafely(1);
                packet.getFloat().writeSafely(1, Math.min(sat, 5.0f));
                event.setPacket(packet);
            }
        });
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGH, PacketType.Play.Server.WINDOW_DATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!on("enabled") || !on("enchantseed")) return;
                Player p = event.getPlayer();
                if (p.getOpenInventory().getType() != InventoryType.ENCHANTING) return;
                Integer property = event.getPacket().getIntegers().readSafely(1);
                if (property != null && property == 3) event.setCancelled(true);   // 3 = the enchantment seed
            }
        });
    }

    private boolean on(String key) {
        return plugin.getConfig().getBoolean("anti-health." + key, false);
    }

    /** The data index of the absorption hearts: 15 up to 1.21.8, 17 since the avatar entity data was added in 1.21.9. */
    private int absorptionIndex() {
        int cfg = plugin.getConfig().getInt("anti-health.absorption-index", 0);
        if (cfg > 0) return cfg;
        try {
            String[] v = Bukkit.getMinecraftVersion().split("\\.");
            int major = Integer.parseInt(v[0]);
            int minor = v.length > 1 ? Integer.parseInt(v[1].replaceAll("\\D.*", "")) : 0;
            int patch = v.length > 2 ? Integer.parseInt(v[2].replaceAll("\\D.*", "")) : 0;
            boolean newer = major > 1 || minor > 21 || (minor == 21 && patch >= 9);
            return newer ? 17 : 15;
        } catch (Exception e) {
            return 15;
        }
    }

    private void filterMetadata(PacketEvent event) {
        Player viewer = event.getPlayer();
        PacketContainer packet = event.getPacket().deepClone();
        int id = packet.getIntegers().read(0);
        Entity entity = ProtocolLibrary.getProtocolManager().getEntityFromID(viewer.getWorld(), id);
        if (!(entity instanceof LivingEntity living) || entity.equals(viewer)) return;   // your own data stays
        List<WrappedDataValue> values = packet.getDataValueCollectionModifier().readSafely(0);
        if (values == null) return;
        int absorption = absorptionIndex();
        boolean changed = false;
        for (WrappedDataValue v : values) {
            Object value = v.getValue();
            int index = v.getIndex();
            if (value instanceof Float f) {
                if (index == HEALTH_INDEX && on("health") && !exempt(living, viewer)) {
                    v.setValue(hide(living, f));
                    changed = true;
                } else if (index == absorption && living instanceof Player && on("absorption")) {
                    v.setValue(0.0f);
                    changed = true;
                }
            } else if (value instanceof Integer && index == AIR_INDEX && on("air")) {
                v.setValue(300);
                changed = true;
            }
        }
        if (changed) {
            packet.getDataValueCollectionModifier().writeSafely(0, values);
            event.setPacket(packet);
        }
    }

    /** Things whose health the client needs to look right. */
    private boolean exempt(LivingEntity e, Player viewer) {
        if (on("ignore-vehicles") && viewer.getVehicle() == e) return true;
        if (on("ignore-tamed-dogs") && e instanceof Wolf w && ((Tameable) w).isTamed()) return true;
        if (on("ignore-enderdragon") && e instanceof EnderDragon) return true;
        return on("ignore-wither") && e instanceof Wither;
    }

    /** What other players' clients are told instead of the real health. */
    private float hide(LivingEntity e, float real) {
        if (real <= 0.0f) return real;   // the death animation needs 0
        if (e instanceof IronGolem && on("show-irongolem-cracks")) {
            // the golem cracks at 75, 50 and 25 health, so send health in steps of 25
            return real >= 25.0f ? (float) (Math.floor(real / 25.0f) * 25.0f) : 1.0f;
        }
        return 1.0f;
    }
}
