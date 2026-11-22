package com.minecraft.arcade.bedwars.structure.zombie;

import com.minecraft.arcade.bedwars.BedWars;
import com.minecraft.arcade.bedwars.arcade.arena.Arena;
import com.minecraft.arcade.bedwars.structure.team.Team;
import com.minecraft.arcade.bedwars.user.User;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.room.map.rollback.RollbackBlock;
import com.minecraft.core.arcade.room.phase.RoomPhase;
import com.minecraft.core.arcade.route.state.ArcadeState;
import com.minecraft.core.util.list.bukkit.ColorUtil;
import lombok.Getter;
import net.minecraft.server.v1_8_R3.EntityInsentient;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.NavigationAbstract;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zumbinho: mini zumbi do time que caça inimigos.
 * <p>
 * O que ele faz sozinho:
 * - escolhe o melhor alvo (quem bateu no dono/nele primeiro, depois o inimigo mais perto e mais fraco);
 * - fareja jogadores invisíveis de perto;
 * - constrói pontes de lã do time quando não há caminho e sobe em torre quando o alvo está no alto;
 * - dá um bote quando está perto do alvo e salva a si mesmo colocando bloco se cair para o void;
 * - sem alvo, segue o dono (ou volta para a base se o dono morrer).
 */
@Getter
public class MiniZombie extends BukkitRunnable {

    public static final String KEY = "miniZombie";

    private static final int LIFETIME = 30 * 20, PERIOD = 2, MAX_BLOCKS = 32;
    private static final double SEARCH_RADIUS = 24, SNIFF_RADIUS = 6;

    /* Último jogador que bateu em cada dono de zumbinho (id do dono -> ataque) */
    private static final Map<UUID, Hit> LAST_HITS = new ConcurrentHashMap<>();

    /* Zumbinhos vivos (id da entidade -> controlador) */
    private static final Map<UUID, MiniZombie> ACTIVE = new ConcurrentHashMap<>();

    private final Zombie zombie;
    private final UUID owner;
    private final Team team;
    private final Arena arena;

    private Player target;
    private Block towerBase;

    private int ticks, blocks, leapCooldown, blockCooldown, stuckTicks;
    private Location lastLocation;

    private MiniZombie(Zombie zombie, Player owner, Team team, Arena arena) {
        this.zombie = zombie;
        this.owner = owner.getUniqueId();
        this.team = team;
        this.arena = arena;
    }

    private static class Hit {
        private final UUID attacker;
        private final long time;

        private Hit(UUID attacker) {
            this.attacker = attacker;
            this.time = System.currentTimeMillis();
        }
    }

    /* Criação */

    public static MiniZombie spawn(Location location, Player owner, Team team, Arena arena) {
        Zombie zombie = location.getWorld().spawn(location, Zombie.class);

        zombie.setBaby(true);
        zombie.setVillager(false);
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setMaxHealth(14);
        zombie.setHealth(14);
        zombie.setCustomNameVisible(true);

        zombie.setMetadata(KEY, new FixedMetadataValue(BedWars.getInstance(), owner.getUniqueId()));
        zombie.setMetadata("teamOwner", new FixedMetadataValue(BedWars.getInstance(), team));

        EntityEquipment equipment = zombie.getEquipment();

        /* Capacete do time: mostra de quem é e impede que queime no sol */
        equipment.setHelmet(Item.of(Material.LEATHER_HELMET).leatherColor(team.getRgb()).unbreakable());
        equipment.setChestplate(Item.of(Material.LEATHER_CHESTPLATE).leatherColor(team.getRgb()).unbreakable());
        equipment.setHelmetDropChance(0);
        equipment.setChestplateDropChance(0);
        equipment.setItemInHandDropChance(0);

        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, LIFETIME + 40, 0, false, false));

        EntityInsentient handle = ((CraftZombie) zombie).getHandle();

        handle.getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(SEARCH_RADIUS + 8);
        handle.getAttributeInstance(GenericAttributes.c).setValue(0.4); // resistência a repulsão

        MiniZombie mini = new MiniZombie(zombie, owner, team, arena);

        mini.updateName();

        ACTIVE.put(zombie.getUniqueId(), mini);
        mini.runTaskTimer(BedWars.getInstance(), 1L, PERIOD);

        location.getWorld().playSound(location, Sound.ZOMBIE_REMEDY, 0.8F, 1.8F);
        location.getWorld().playEffect(location, Effect.MOBSPAWNER_FLAMES, 0);

        return mini;
    }

    public static boolean isMiniZombie(org.bukkit.entity.Entity entity) {
        return entity instanceof Zombie && entity.hasMetadata(KEY);
    }

    public static MiniZombie of(org.bukkit.entity.Entity entity) {
        return ACTIVE.get(entity.getUniqueId());
    }

    public static UUID getOwner(org.bukkit.entity.Entity entity) {
        if (!entity.hasMetadata(KEY)) return null;

        Object value = entity.getMetadata(KEY).get(0).value();

        return value instanceof UUID ? (UUID) value : null;
    }

    /**
     * Guarda quem bateu no dono para o zumbinho vingar.
     */
    public static void registerHit(Player victim, Player attacker) {
        LAST_HITS.put(victim.getUniqueId(), new Hit(attacker.getUniqueId()));
    }

    /* Loop */

    @Override
    public void run() {
        ticks += PERIOD;

        if (!zombie.isValid() || zombie.isDead() || ticks >= LIFETIME || !arena.isPhase(RoomPhase.PLAYING)) {
            despawn();
            return;
        }

        if (leapCooldown > 0) leapCooldown -= PERIOD;
        if (blockCooldown > 0) blockCooldown -= PERIOD;

        Location location = zombie.getLocation();

        if (location.getY() < 0) {
            despawn();
            return;
        }

        if (saveFromVoid(location)) return;

        if (ticks % 10 == 0 || !isValidTarget(target))
            target = findTarget();

        if (target != null)
            chase(location);
        else
            followOwner(location);

        finishTower();

        if (ticks % 20 == 0)
            updateName();

        lastLocation = location;
    }

    public void despawn() {
        cancel();

        ACTIVE.remove(zombie.getUniqueId());

        if (zombie.isValid()) {
            Location location = zombie.getLocation();

            location.getWorld().playEffect(location, Effect.SMOKE, 4);
            location.getWorld().playSound(location, Sound.ZOMBIE_DEATH, 0.6F, 2F);

            zombie.remove();
        }
    }

    /* Alvo */

    private Player findTarget() {
        Location location = zombie.getLocation();

        /* 1º: quem atacou o dono nos últimos 6 segundos */
        Hit hit = LAST_HITS.get(owner);

        if (hit != null && System.currentTimeMillis() - hit.time < 6000) {
            Player attacker = org.bukkit.Bukkit.getPlayer(hit.attacker);

            if (isValidTarget(attacker) && attacker.getLocation().distanceSquared(location) < SEARCH_RADIUS * SEARCH_RADIUS)
                return attacker;
        }

        /* 2º: o inimigo com a melhor pontuação (perto, com pouca vida, perto da nossa cama) */
        Player best = null;
        double bestScore = Double.MAX_VALUE;

        for (User user : arena.getAliveUsers()) {
            Player player = user.getPlayer();

            if (!isValidTarget(player)) continue;

            double distance = player.getLocation().distance(location);

            if (distance > SEARCH_RADIUS) continue;

            double score = distance + player.getHealth() * 0.4;

            if (team.getBase() != null && player.getWorld().equals(team.getBase().getWorld()) && player.getLocation().distance(team.getBase()) < 12)
                score -= 8;

            if (target != null && target.equals(player))
                score -= 3; // evita ficar trocando de alvo

            if (score < bestScore) {
                bestScore = score;
                best = player;
            }
        }

        return best;
    }

    private boolean isValidTarget(Player player) {
        if (player == null || !player.isOnline() || player.isDead() || !player.getWorld().equals(zombie.getWorld())) return false;

        User user = (User) User.of(player.getUniqueId());

        if (user == null || !user.isPlayer() || !user.inState(ArcadeState.ALIVE) || user.isProtected()) return false;

        if (user.getTeam() == null || user.getTeam().equals(team)) return false;

        /* Invisíveis só são farejados de perto */
        return !player.hasPotionEffect(PotionEffectType.INVISIBILITY) || player.getLocation().distance(zombie.getLocation()) <= SNIFF_RADIUS;
    }

    /**
     * Quando alguém bate no zumbinho, ele passa a caçar essa pessoa.
     */
    public void revenge(Player attacker) {
        if (isValidTarget(attacker))
            target = attacker;
    }

    /* Movimento */

    private void chase(Location location) {
        zombie.setTarget(target);

        Location goal = target.getLocation();
        double horizontal = horizontalDistance(location, goal), height = goal.getY() - location.getY();

        look(goal);

        boolean path = navigation().a(goal.getX(), goal.getY(), goal.getZ(), 1.25D);

        /* Bote: perto o bastante, pula no alvo */
        if (zombie.isOnGround() && leapCooldown <= 0 && horizontal > 2.2 && horizontal < 4.5 && Math.abs(height) < 1.5 && zombie.hasLineOfSight(target)) {
            Vector leap = direction(location, goal).multiply(0.65).setY(0.36);

            zombie.setVelocity(leap);
            leapCooldown = 30;
            return;
        }

        /* Alvo no alto e perto: sobe em torre */
        if (height > 1.5 && horizontal < 2.5) {
            tower(location);
            return;
        }

        /* Sem caminho (ilha separada pelo void) ou parado: faz ponte */
        if (!path || isStuck(location)) {
            if (height > 1.5 && horizontal < 6)
                tower(location);
            else
                bridge(location, goal);
        }
    }

    private void followOwner(Location location) {
        zombie.setTarget(null);

        Player player = org.bukkit.Bukkit.getPlayer(owner);
        User user = player != null ? (User) User.of(owner) : null;

        Location goal = player != null && user != null && user.inState(ArcadeState.ALIVE) && player.getWorld().equals(zombie.getWorld())
                ? player.getLocation()
                : team.getBase();

        if (goal == null || !goal.getWorld().equals(zombie.getWorld())) return;

        double distance = horizontalDistance(location, goal);

        if (distance < 3) {
            navigation().n();
            return;
        }

        boolean path = navigation().a(goal.getX(), goal.getY(), goal.getZ(), 1.15D);

        /* Ficou longe demais do dono: teleporta até ele, como um pet */
        if (player != null && goal.equals(player.getLocation()) && (distance > 28 || !path && distance > 10) && player.isOnGround()) {
            zombie.teleport(player.getLocation().clone().add(direction(goal, location).multiply(1.5)));
            zombie.getWorld().playEffect(zombie.getLocation(), Effect.ENDER_SIGNAL, 0);
        }
    }

    /* Construção */

    /**
     * Coloca lã no buraco à frente, na direção do alvo.
     */
    private void bridge(Location location, Location goal) {
        if (!zombie.isOnGround() || blockCooldown > 0) return;

        Vector direction = direction(location, goal);

        Block feet = location.getBlock();
        Block ahead = feet.getRelative(face(direction));
        Block below = ahead.getRelative(BlockFace.DOWN);

        /* Bloco sólido na frente na altura dos pés: pula por cima */
        if (ahead.getType().isSolid() && !ahead.getRelative(BlockFace.UP).getType().isSolid()) {
            zombie.setVelocity(direction.clone().multiply(0.2).setY(0.42));
            return;
        }

        if (below.getType().isSolid()) {
            /* Já tem chão: só empurra para frente */
            zombie.setVelocity(direction.clone().multiply(0.22).setY(zombie.getVelocity().getY()));
            return;
        }

        if (place(below)) {
            zombie.setVelocity(direction.clone().multiply(0.2).setY(zombie.getVelocity().getY()));
            blockCooldown = 4;
        }
    }

    private void tower(Location location) {
        if (!zombie.isOnGround() || blockCooldown > 0 || blocks >= MAX_BLOCKS) return;

        Block feet = location.getBlock();

        if (feet.getRelative(BlockFace.UP, 2).getType().isSolid()) return; // teto em cima

        towerBase = feet;
        zombie.setVelocity(new Vector(0, 0.45, 0));
        blockCooldown = 8;
    }

    private void finishTower() {
        if (towerBase == null) return;

        if (zombie.getLocation().getY() >= towerBase.getY() + 0.95) {
            place(towerBase);
            towerBase = null;
        } else if (zombie.isOnGround() && blockCooldown <= 4) {
            towerBase = null;
        }
    }

    /**
     * Caindo para o void: tenta colocar um bloco embaixo para se salvar.
     */
    private boolean saveFromVoid(Location location) {
        if (zombie.isOnGround() || zombie.getVelocity().getY() > -0.3) return false;

        Block floor = location.getBlock();

        for (int i = 0; i < 40 && floor.getY() > 0; i++) {
            floor = floor.getRelative(BlockFace.DOWN);

            if (floor.getType().isSolid()) return false;
        }

        Block below = location.getBlock().getRelative(BlockFace.DOWN);

        if (place(below)) {
            zombie.setVelocity(new Vector(0, 0, 0));
            zombie.setFallDistance(0);
            return true;
        }

        return false;
    }

    private boolean place(Block block) {
        if (blocks >= MAX_BLOCKS || !block.isEmpty() && block.getType() != Material.LONG_GRASS) return false;

        Location location = block.getLocation();

        if (arena.isProtectedArea(location) || location.getBlockY() >= arena.getMap().getBuildLimit()) return false;

        /* Não prende ninguém dentro do bloco */
        for (org.bukkit.entity.Entity entity : block.getWorld().getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 0.6, 0.9, 0.6)) {
            if (entity instanceof Player) return false;
        }

        block.setType(Material.WOOL);
        block.setData((byte) ColorUtil.getIdByColor(team.getColor()));

        arena.addRollBack(block, RollbackBlock.RollbackType.PLACE_BLOCK);

        block.getWorld().playSound(location, Sound.DIG_WOOL, 1F, 1F);

        blocks++;
        return true;
    }

    /* Utilidades */

    private boolean isStuck(Location location) {
        if (lastLocation == null || !lastLocation.getWorld().equals(location.getWorld())) return false;

        stuckTicks = lastLocation.distanceSquared(location) < 0.0025 ? stuckTicks + PERIOD : 0;

        return stuckTicks >= 10;
    }

    private void look(Location goal) {
        ((CraftZombie) zombie).getHandle().getControllerLook().a(goal.getX(), goal.getY() + 1.4, goal.getZ(), 30F, 30F);
    }

    private NavigationAbstract navigation() {
        return ((CraftZombie) zombie).getHandle().getNavigation();
    }

    private static double horizontalDistance(Location a, Location b) {
        double x = a.getX() - b.getX(), z = a.getZ() - b.getZ();

        return Math.sqrt(x * x + z * z);
    }

    private static Vector direction(Location from, Location to) {
        Vector vector = new Vector(to.getX() - from.getX(), 0, to.getZ() - from.getZ());

        return vector.lengthSquared() < 0.0001 ? new Vector(0, 0, 0) : vector.normalize();
    }

    private static BlockFace face(Vector direction) {
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ()))
            return direction.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;

        return direction.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private void updateName() {
        int seconds = Math.max(0, (LIFETIME - ticks) / 20);

        zombie.setCustomName(team.getColor() + "Zumbinho §c" + (int) Math.ceil(zombie.getHealth()) + "❤ §7" + seconds + "s");
    }
}
