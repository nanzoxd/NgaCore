package me.smpcore.module;

import me.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ban animation: a giant mace appears high above the player, charges up in red, slams down on them,
 * sends out a red shockwave and only then the player is kicked with the ban screen.
 */
public final class BanAnimation {
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0xFF2020), 1.8f);
    private static final Particle.DustOptions DARK = new Particle.DustOptions(Color.fromRGB(0x850120), 1.4f);

    /** Steepest look-up angle while following the mace. */
    private static final float UP = -80f;

    private final SMPCore plugin;

    public BanAnimation(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("tempban.animation.enabled", true);
    }

    /** Plays the slam on an online player, then runs {@code then} (the kick) on the main thread. */
    public void play(Player p, Runnable then) {
        double height = Math.max(4, plugin.getConfig().getDouble("tempban.animation.height", 12));
        float scale = (float) Math.max(2, plugin.getConfig().getDouble("tempban.animation.scale", 5));
        int windup = Math.max(0, plugin.getConfig().getInt("tempban.animation.windup-ticks", 14));
        World w = p.getWorld();
        // the mace comes in from in front of the player and slants down onto their head, so they see its face
        double lead = Math.max(0, plugin.getConfig().getDouble("tempban.animation.lead", 5));
        float yaw = p.getLocation().getYaw();
        Vector dir = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Location start = p.getLocation().add(dir.clone().multiply(lead)).add(0, height, 0);
        start.setYaw(yaw);
        start.setPitch(0);
        double topY = start.getY();
        // the mace is a diagonal sprite (head up-right); turn it 135 degrees so the head points down
        Quaternionf tilt = new Quaternionf().rotateZ((float) Math.toRadians(-135));
        ItemDisplay mace = w.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.MACE));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), tilt, new Vector3f(scale), new Quaternionf()));
            d.setTeleportDuration(1);
            d.setViewRange(2f);
            d.setPersistent(false);
        });
        // hold still while the mace comes down
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 6, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 128, false, false, false));
        nearby(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.8f);
        // the head hangs about half a scale below the centre of the item
        double impactY = p.getLocation().getY() + 1.8 + scale * 0.5;

        new BukkitRunnable() {
            int t = 0;
            double speed = 0;
            double offNow = lead;
            boolean hit = false;
            boolean trembled = false;

            @Override
            public void run() {
                t++;
                if (!mace.isValid() || !p.isOnline()) {
                    mace.remove();
                    cancel();
                    return;
                }
                Location pl = p.getLocation();
                Location cur = mace.getLocation();
                // turn the player's head up toward the mace so they watch it come down (and keep them facing it)
                double dy = cur.getY() - p.getEyeLocation().getY();
                float want = (float) Math.max(UP, Math.min(-15, -Math.toDegrees(Math.atan2(dy, Math.max(0.5, offNow)))));
                float pitch = pl.getPitch();
                p.setRotation(yaw, pitch + (want - pitch) * 0.4f);
                if (t <= windup) {
                    // charge: rise a little, red sparks swirl around the head
                    Location up = new Location(w, pl.getX() + dir.getX() * lead, topY + Math.sin(t / (double) Math.max(1, windup) * Math.PI) * 0.8,
                            pl.getZ() + dir.getZ() * lead, yaw, 0);
                    mace.teleport(up);
                    double a = t * 0.9;
                    for (int i = 0; i < 3; i++) {
                        double aa = a + i * Math.PI * 2 / 3;
                        w.spawnParticle(Particle.DUST, up.clone().add(Math.cos(aa) * 1.4, -scale * 0.4, Math.sin(aa) * 1.4), 2, 0.05, 0.05, 0.05, 0, RED);
                    }
                    return;
                }
                if (!hit) {
                    speed = Math.min(3.2, speed + 0.45);
                    double y = Math.max(impactY, cur.getY() - speed);
                    offNow = topY > impactY ? lead * Math.max(0, Math.min(1, (y - impactY) / (topY - impactY))) : 0;
                    Location next = new Location(w, pl.getX() + dir.getX() * offNow, y, pl.getZ() + dir.getZ() * offNow, yaw, 0);
                    mace.teleport(next);
                    w.spawnParticle(Particle.DUST, next.clone().add(0, -scale * 0.5, 0), 6, 0.3, 0.3, 0.3, 0, RED);
                    w.spawnParticle(Particle.CRIT, next.clone().add(0, -scale * 0.5, 0), 3, 0.2, 0.2, 0.2, 0.05);
                    if (!trembled && y - impactY < 8) {
                        trembled = true;
                        ripple(pl, Math.min(3, groundRadius()), 0.2);
                    }
                    if (y <= impactY) {
                        hit = true;
                        impact(p, w, mace);
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // kick once the shockwave has had time to play
        long delay = windup + 14 + Math.max(10, plugin.getConfig().getInt("tempban.animation.kick-delay-ticks", 30));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            mace.remove();
            if (p.isOnline()) then.run();
        }, delay);
    }

    /** Plays a sound only to players within tempban.animation.sound-radius blocks (default 24), not the whole world. */
    private void nearby(Location at, Sound sound, float volume, float pitch) {
        double r = plugin.getConfig().getDouble("tempban.animation.sound-radius", 24);
        double r2 = r * r;
        for (Player o : at.getWorld().getPlayers()) {
            if (o.getLocation().distanceSquared(at) <= r2) o.playSound(at, sound, volume, pitch);
        }
    }

    private int groundRadius() {
        return Math.max(0, Math.min(10, plugin.getConfig().getInt("tempban.animation.ground-radius", 6)));
    }

    /**
     * The ground jumps: a copy of every block around the player rises and drops back in a wave that
     * travels outward with the shockwave. Purely visual, the real blocks never change.
     */
    private void ripple(Location center, int radius, double lift) {
        if (radius <= 0) return;
        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        float s = 1.002f, o = -0.001f;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) continue;
                Block ground = null;
                for (int y = cy; y >= cy - 3; y--) {
                    Block b = w.getBlockAt(cx + dx, y, cz + dz);
                    if (b.getType().isOccluding() && !w.getBlockAt(cx + dx, y + 1, cz + dz).getType().isOccluding()) {
                        ground = b;
                        break;
                    }
                }
                if (ground == null) continue;
                BlockData data = ground.getBlockData();
                BlockDisplay d = w.spawn(ground.getLocation(), BlockDisplay.class, e -> {
                    e.setBlock(data);
                    e.setBrightness(new Display.Brightness(13, 13));
                    e.setTransformation(new Transformation(new Vector3f(o, o, o), new Quaternionf(), new Vector3f(s), new Quaternionf()));
                    e.setViewRange(1.5f);
                    e.setPersistent(false);
                });
                float up = (float) (lift * (0.25 + 0.75 * (1 - dist / (radius + 1))));
                long delay = Math.round(dist / 0.9);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!d.isValid()) return;
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(3);
                    d.setTransformation(new Transformation(new Vector3f(o, o + up, o), new Quaternionf(), new Vector3f(s), new Quaternionf()));
                }, delay + 1);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!d.isValid()) return;
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(5);
                    d.setTransformation(new Transformation(new Vector3f(o, o, o), new Quaternionf(), new Vector3f(s), new Quaternionf()));
                }, delay + 5);
                Bukkit.getScheduler().runTaskLater(plugin, d::remove, delay + 12);
            }
        }
    }

    private void impact(Player p, World w, ItemDisplay mace) {
        Location hit = p.getLocation();
        Location chest = hit.clone().add(0, 1, 0);
        BlockData ground = hit.getBlock().getRelative(0, -1, 0).getBlockData();
        if (ground.getMaterial().isAir()) ground = Material.STONE.createBlockData();
        final BlockData floor = ground;

        nearby(hit, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.9f);
        nearby(hit, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
        nearby(hit, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.6f);
        w.spawnParticle(Particle.EXPLOSION, hit.clone().add(0, 0.3, 0), 1);
        w.spawnParticle(Particle.DUST_PILLAR, hit, 80, 1.3, 0.2, 1.3, 0.1, floor);
        // red burst out of the player
        w.spawnParticle(Particle.DUST, chest, 140, 0.6, 0.9, 0.6, 0, RED);
        w.spawnParticle(Particle.DUST, chest, 60, 0.8, 1.0, 0.8, 0, DARK);
        w.spawnParticle(Particle.DAMAGE_INDICATOR, chest, 20, 0.5, 0.7, 0.5, 0.1);
        ripple(hit, groundRadius(), 0.9);
        p.playHurtAnimation(0f);
        p.setVelocity(new Vector(0, 0.8, 0));

        // shockwave: an expanding red ring of dust and ground debris
        new BukkitRunnable() {
            int r = 0;

            @Override
            public void run() {
                r++;
                if (r > 14) {
                    cancel();
                    return;
                }
                double radius = 0.8 + r * 0.9;
                int points = (int) Math.min(120, 24 + r * 8);
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    Location l = hit.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
                    w.spawnParticle(Particle.DUST, l, 1, 0.05, 0.05, 0.05, 0, RED);
                    if (i % 3 == 0) w.spawnParticle(Particle.BLOCK, l, 2, 0.1, 0.1, 0.1, 0, floor);
                    if (i % 6 == 0) w.spawnParticle(Particle.CLOUD, l.add(0, 0.2, 0), 1, 0.05, 0.05, 0.05, 0.02);
                }
                if (r == 4) mace.remove();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
