package com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.effect;

import com.minecraft.core.api.collectible.util.particle.ParticleEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.function.IntConsumer;

/**
 * Ferramentas das animações dos cosméticos de Bed Wars (destruição de cama e abate final).
 * Tudo é feito com partículas por pacote, então não pesa no servidor.
 */
public final class BedFx {

    private static final double RANGE = 48;

    private BedFx() {
    }

    /**
     * Roda {@code frame} uma vez por tick, de 0 até {@code duration - 1}. {@code end} roda no final (ou se der erro).
     */
    public static void animate(int duration, IntConsumer frame, Runnable end) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                try {
                    if (tick >= duration) {
                        finish();
                        return;
                    }

                    frame.accept(tick++);
                } catch (Exception exception) {
                    finish();
                }
            }

            private void finish() {
                cancel();

                if (end != null)
                    end.run();
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(BedFx.class), 0L, 1L);
    }

    public static void animate(int duration, IntConsumer frame) {
        animate(duration, frame, null);
    }

    public static void particle(ParticleEffect effect, Location location, float offset, float speed, int amount) {
        effect.display(offset, offset, offset, speed, amount, location, RANGE);
    }

    public static void particle(ParticleEffect effect, Location location) {
        particle(effect, location, 0F, 0F, 1);
    }

    /* Partícula que voa numa direção (fagulhas, chamas, nuvens) */
    public static void shoot(ParticleEffect effect, Location location, Vector direction, float speed) {
        effect.display(direction, speed, location, RANGE);
    }

    /* Pó colorido (redstone) */
    public static void color(Location location, int red, int green, int blue) {
        ParticleEffect.REDSTONE.display(new ParticleEffect.OrdinaryColor(Math.max(1, red), green, blue), location, RANGE);
    }

    public static void hue(Location location, float hue) {
        int rgb = java.awt.Color.HSBtoRGB(hue, 1F, 1F);

        color(location, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /* Explosão esférica de partículas direcionais, espalhadas por igual (espiral de Fibonacci) */
    public static void burst(ParticleEffect effect, Location center, int count, float speed) {
        double golden = Math.PI * (3 - Math.sqrt(5));

        for (int i = 0; i < count; i++) {
            double y = 1 - (i / (double) (count - 1)) * 2;
            double radius = Math.sqrt(1 - y * y);
            double theta = golden * i;

            shoot(effect, center, new Vector(Math.cos(theta) * radius, y, Math.sin(theta) * radius), speed);
        }
    }

    /* Anel horizontal de pó colorido */
    public static void ring(Location center, double radius, int points, float hueOffset) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;

            hue(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), (hueOffset + i / (float) points) % 1F);
        }
    }

    public static void sound(Location location, Sound sound, float volume, float pitch) {
        location.getWorld().playSound(location, sound, volume, pitch);
    }
}
