package com.minecraft.core.api.collectible.type.title;

import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Linha de texto acima da cabeça de um jogador, feita só com pacotes (sem entidade real no mundo).
 * Usa um armor stand "marker" invisível: sem hitbox, então não atrapalha clicar no jogador.
 */
public class TitleDisplay {

    /* Marker não tem altura: o nome aparece ~0.5 acima da posição. O nick fica em ~2.3; o título fica uma linha acima. */
    private static final double OFFSET = 2.12;
    private static final double RANGE = 48;
    private static final int VIEWERS_REFRESH = 10;

    private final Player host;
    private final Set<UUID> viewers = new HashSet<>();

    private EntityArmorStand stand;
    private World world;
    private Location last;
    private String text = "";
    private boolean wasHidden;
    private int age;

    public TitleDisplay(Player host) {
        this.host = host;
    }

    /**
     * Atualiza posição, texto e quem vê o título. Chamar na thread principal.
     */
    public void tick(String newText) {
        if (!host.isOnline()) {
            destroy();
            return;
        }

        Location location = host.getLocation();

        if (stand == null || !location.getWorld().equals(world)) {
            destroy();
            create(location, newText);
        }

        boolean textChanged = !newText.equals(text);

        if (textChanged) {
            text = newText;
            stand.setCustomName(text);
        }

        boolean moved = last == null || last.distanceSquared(location) > 0.0004;

        if (moved) {
            stand.setLocation(location.getX(), location.getY() + OFFSET, location.getZ(), 0F, 0F);
            last = location.clone();
        }

        /* Esconde junto com o nick: agachado, invisível, morto ou no vanish */
        boolean hidden = host.isSneaking() || host.isDead() || host.hasPotionEffect(PotionEffectType.INVISIBILITY);

        /* Lista de quem vê só é refeita de tempos em tempos (ou quando o dono agacha/levanta) */
        if (age++ % VIEWERS_REFRESH != 0 && hidden == wasHidden) {
            for (UUID id : viewers) {
                Player viewer = Bukkit.getPlayer(id);

                if (viewer == null) continue;

                if (moved)
                    send(viewer, new PacketPlayOutEntityTeleport(stand));

                if (textChanged)
                    send(viewer, new PacketPlayOutEntityMetadata(stand.getId(), stand.getDataWatcher(), true));
            }
            return;
        }

        wasHidden = hidden;

        Set<UUID> should = new HashSet<>();

        if (!hidden) {
            for (Player viewer : host.getWorld().getPlayers()) {
                /* O dono também vê (em F5), para saber que o título está ativo */
                if (!viewer.equals(host) && !viewer.canSee(host)) continue;
                if (viewer.getLocation().distanceSquared(location) > RANGE * RANGE) continue;

                should.add(viewer.getUniqueId());
            }
        }

        for (UUID id : new HashSet<>(viewers)) {
            if (should.contains(id)) continue;

            Player viewer = Bukkit.getPlayer(id);

            if (viewer != null)
                send(viewer, new PacketPlayOutEntityDestroy(stand.getId()));

            viewers.remove(id);
        }

        for (UUID id : should) {
            Player viewer = Bukkit.getPlayer(id);

            if (viewer == null) continue;

            if (viewers.add(id)) {
                send(viewer, new PacketPlayOutSpawnEntityLiving(stand));
                continue;
            }

            if (moved)
                send(viewer, new PacketPlayOutEntityTeleport(stand));

            if (textChanged)
                send(viewer, new PacketPlayOutEntityMetadata(stand.getId(), stand.getDataWatcher(), true));
        }
    }

    public void destroy() {
        if (stand != null) {
            PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(stand.getId());

            for (UUID id : viewers) {
                Player viewer = Bukkit.getPlayer(id);

                if (viewer != null)
                    send(viewer, packet);
            }
        }

        viewers.clear();
        stand = null;
        last = null;
        age = 0;
    }

    private void create(Location location, String initialText) {
        world = location.getWorld();

        stand = new EntityArmorStand(((CraftWorld) world).getHandle());

        stand.setInvisible(true);
        stand.setGravity(false);
        stand.n(true); // marker: sem hitbox
        stand.setCustomName(initialText);
        stand.setCustomNameVisible(true);
        stand.setLocation(location.getX(), location.getY() + OFFSET, location.getZ(), 0F, 0F);

        text = initialText;
        last = location.clone();
    }

    private static void send(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }
}
