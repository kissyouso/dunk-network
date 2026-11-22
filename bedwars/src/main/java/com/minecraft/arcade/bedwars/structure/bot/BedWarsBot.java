package com.minecraft.arcade.bedwars.structure.bot;

import com.minecraft.arcade.bedwars.BedWars;
import com.minecraft.arcade.bedwars.arcade.arena.Arena;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.top.enums.TopType;
import com.minecraft.arcade.bedwars.structure.generator.objects.level.GeneratorLevel;
import com.minecraft.arcade.bedwars.structure.generator.objects.type.ore.OreGenerator;
import com.minecraft.arcade.bedwars.structure.team.Team;
import com.minecraft.arcade.bedwars.structure.team.objects.upgrade.structure.armor.ArmorProtection;
import com.minecraft.arcade.bedwars.structure.team.objects.upgrade.structure.sword.Sword;
import com.minecraft.arcade.bedwars.user.User;
import com.minecraft.core.Core;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.room.map.rollback.RollbackBlock;
import com.minecraft.core.arcade.room.phase.RoomPhase;
import com.minecraft.core.arcade.route.state.ArcadeState;
import com.minecraft.core.util.list.bukkit.ColorUtil;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_8_R3.EntityInsentient;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.NavigationAbstract;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftZombie;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bot de treino do BedWars: uma entidade do servidor com cérebro próprio, no
 * mesmo molde do {@code MiniZombie}.
 * <p>
 * O bot enxerga a sala (inimigos vivos, invasores da própria ilha, camas que
 * ainda existem, gerador de mid, vão na frente dele) e decide uma meta pelo
 * modo configurado. A partir daí ele anda, pula, circula, constrói a ponte no
 * estilo escolhido, minerar cama, compra lã/equipamento com a própria carteira
 * de minérios e sobe upgrade de time. Cair no void conta como morte, e sem cama
 * o bot sai da partida como um jogador eliminado.
 * <p>
 * Nada aqui imita pacote de cliente: o que se vê é movimento e construção de
 * entidade, controlados pelo plugin no servidor.
 */
@Getter
public class BedWarsBot extends BukkitRunnable {

    public static final String KEY = "bedwarsBot";

    private static final int PERIOD = 1, GRACE_TICKS = 100;

    /** Raio em que o bot considera alguém um invasor da própria ilha. */
    private static final double INVADER_RADIUS = 14, SNIFF_RADIUS = 5, GUARD_RADIUS = 12;

    public enum Goal { GUARD, HUNT, BED, MINE, RETREAT, RESUPPLY }

    private final UUID id = UUID.randomUUID();
    private final Arena arena;
    private final Team team;
    private final BotProfile profile;
    private final String name;

    private Zombie entity;
    private Goal goal = Goal.GUARD;
    private LivingEntity target;
    private Team objective;
    private Location patrol;

    /* Quem bateu por último no bot (a EntityDeathEvent não traz o agressor de
     * mob), usado para dar o crédito do abate ao jogador. */
    @Setter
    private LivingEntity lastAttacker;

    private Block towerBase;
    private Location lastLocation;

    private boolean finished;
    private int gearTier;

    /* Carteira do bot: uma entidade não tem inventário para carregar a economia
     * da partida, então o ferro/ouro/diamante ficam aqui e viram lã, equipamento
     * e upgrade de time. */
    private int blocks, iron, gold, diamond;
    private double goldRemainder;

    private int ticks, thinkTicks = 1, respawnTicks, blockCooldown, leapCooldown, hitCooldown, attackCooldown;
    private int stuckTicks, bedTicks, strafeTicks;

    private BedWarsBot(Arena arena, Team team, BotProfile profile, String name) {
        this.arena = arena;
        this.team = team;
        this.profile = profile;
        this.name = name;
    }

    public static BedWarsBot spawn(Arena arena, Team team, BotProfile profile, String name) {
        if (team.getBase() == null) return null;

        BedWarsBot bot = new BedWarsBot(arena, team, profile, name);

        bot.materialize();
        bot.runTaskTimer(BedWars.getInstance(), 1L, PERIOD);

        return bot;
    }

    /* Existência */

    private void materialize() {
        World world = team.getBase().getWorld();

        entity = world.spawn(team.getBase().clone().add(0, 0.5, 0), Zombie.class);

        entity.setBaby(false);
        entity.setVillager(false);
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);
        entity.setMaxHealth(profile.getMaxHealth());
        entity.setHealth(profile.getMaxHealth());
        entity.setCustomNameVisible(true);

        entity.setMetadata(KEY, new FixedMetadataValue(BedWars.getInstance(), id));
        entity.setMetadata("teamOwner", new FixedMetadataValue(BedWars.getInstance(), team));

        applyAttributes();
        applyGear();
        updateName();

        if (blocks == 0) blocks = profile.getWoolBlocks();

        world.playEffect(entity.getLocation(), Effect.MOBSPAWNER_FLAMES, 0);
    }

    private void applyAttributes() {
        EntityInsentient handle = handle();

        handle.getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(INVADER_RADIUS * 2);
        handle.getAttributeInstance(GenericAttributes.c).setValue(0.5 + profile.getKnockback() * 0.3);

        applySpeed();
    }

    private void applySpeed() {
        handle().getAttributeInstance(GenericAttributes.MOVEMENT_SPEED)
                .setValue(profile.getSpeed() * profile.getMode().getSpeed());
    }

    /**
     * Reaplica o perfil depois de um ajuste no /bwbot: a entidade continua a
     * mesma, só muda o comportamento.
     */
    public void refresh() {
        if (entity == null || !entity.isValid()) return;

        entity.setMaxHealth(profile.getMaxHealth());

        if (entity.getHealth() > profile.getMaxHealth())
            entity.setHealth(profile.getMaxHealth());

        applyAttributes();
        applyGear();
        updateName();
    }

    public void despawn() {
        if (finished) return;

        finished = true;

        try {
            cancel();
        } catch (IllegalStateException ignored) {
        }

        BotController.unregister(this);

        if (entity != null && entity.isValid()) entity.remove();

        target = null;
        objective = null;
    }

    /**
     * Morte do bot: sem cama ele sai da partida (eliminado, como um jogador);
     * com cama ele volta para a base depois do tempo configurado.
     */
    public void handleDeath() {
        if (finished || respawnTicks > 0) return;

        if (entity != null && entity.isValid()) {
            Location location = entity.getLocation();

            entity.remove();

            location.getWorld().playEffect(location, Effect.SMOKE, 4);
            location.getWorld().playSound(location, Sound.ZOMBIE_DEATH, 0.6F, 1.4F);
        }

        target = null;
        objective = null;
        towerBase = null;

        /* O crédito já foi lido pelo listener: sem limpar, a próxima morte do
         * bot renascido seria atribuída a quem bateu nele nesta. */
        lastAttacker = null;

        if (!team.hasBed()) {
            despawn();
            return;
        }

        respawnTicks = Math.max(1, profile.getRespawnSeconds() * 20);
    }

    public boolean isAlive() {
        return !finished && respawnTicks <= 0 && entity != null && entity.isValid() && !entity.isDead();
    }

    public String getDisplayName() {
        return team.getColor() + name + " " + profile.getSuffix();
    }

    /* Loop */

    @Override
    public void run() {
        ticks += PERIOD;

        if (!arena.isPhase(RoomPhase.PLAYING)) {
            despawn();
            return;
        }

        if (respawnTicks > 0) {
            respawnTicks -= PERIOD;

            if (respawnTicks <= 0) materialize();

            return;
        }

        if (entity == null || !entity.isValid() || entity.isDead()) {
            handleDeath();
            return;
        }

        if (blockCooldown > 0) blockCooldown -= PERIOD;
        if (leapCooldown > 0) leapCooldown -= PERIOD;
        if (hitCooldown > 0) hitCooldown -= PERIOD;
        if (attackCooldown > 0) attackCooldown -= PERIOD;

        Location location = entity.getLocation();

        /* Caiu do mapa: conta como morte, sem creditar abate para ninguém. */
        if (location.getY() < 1) {
            handleDeath();
            return;
        }

        if (--thinkTicks <= 0 || !isValid(target)) think(location);

        if (!saveFromVoid(location)) act(location);

        finishTower();

        if (ticks % 20 == 0) {
            income(location);
            regenerate(location);
            updateName();
        }

        lastLocation = location;
    }

    /* Decisão */

    private void think(Location location) {
        thinkTicks = 10 + Core.RANDOM.nextInt(11);

        BotMode mode = profile.getMode();

        /* Ninguém invade a base nos primeiros segundos: a largada da partida é
         * do jogador, não do bot. */
        if (ticks < GRACE_TICKS) {
            target = null;
            objective = null;
            goal = Goal.GUARD;
            return;
        }

        LivingEntity invader = findEnemy(location, INVADER_RADIUS, true);

        /* Fora do alcance de combate o bot continua enxergando a sala inteira:
         * com o recorte limitado ao engage, o inimigo do outro lado do mapa não
         * era alvo e o time inteiro ficava parado na própria ilha. */
        target = invader != null ? invader : findEnemy(location, Double.MAX_VALUE, false);
        objective = profile.isAttackBeds() ? pickObjective(location) : null;

        double engage = Math.max(4.0, profile.getEngageDistance());

        double guard = mode.getGuard() * (invader != null ? 2.4 : 0.45);
        double hunt = target == null ? 0 : mode.getHunt()
                * clamp(2.0 - horizontalDistance(location, target.getLocation()) / engage, 0.75, 2.0);
        double bed = objective == null ? 0 : mode.getBed();
        double mine = diamond < 4 && target == null && objective == null ? mode.getGuard() * 0.8 : 0;
        double supply = blocks < profile.getWoolBlocks() ? (iron >= profile.getWoolCostIron() ? 1.4 : 0.7) : 0;
        double retreat = mode.isRetreat() && entity.getHealth() <= profile.getRetreatHealth() ? 3.5 : 0;

        Goal chosen = Goal.GUARD;
        double best = guard;

        if (hunt > best) { chosen = Goal.HUNT; best = hunt; }
        if (bed > best) { chosen = Goal.BED; best = bed; }
        if (mine > best) { chosen = Goal.MINE; best = mine; }
        if (supply > best) { chosen = Goal.RESUPPLY; best = supply; }
        if (retreat > best) chosen = Goal.RETREAT;

        if ((chosen == Goal.MINE && midSpot() == null) || (chosen == Goal.BED && objective == null))
            chosen = Goal.GUARD;

        goal = chosen;

        applySpeed();
    }

    private LivingEntity findEnemy(Location from, double radius, boolean islandOnly) {
        LivingEntity best = null;
        double bestSquared = radius * radius;

        for (User user : arena.getAliveUsers()) {
            Player player = user.getPlayer();

            if (player == null || user.getTeam() == null || user.getTeam().equals(team)) continue;

            if (!isHumanTarget(user, player)) continue;

            double squared = from.distanceSquared(player.getLocation());

            if (squared > bestSquared) continue;
            if (islandOnly && !isInvader(player.getLocation())) continue;

            best = player;
            bestSquared = squared;
        }

        for (BedWarsBot bot : BotController.bots(arena)) {
            if (bot.equals(this) || !bot.isAlive() || bot.getTeam().equals(team)) continue;

            LivingEntity other = bot.getEntity();
            double squared = from.distanceSquared(other.getLocation());

            if (squared > bestSquared) continue;
            if (islandOnly && !isInvader(other.getLocation())) continue;

            best = other;
            bestSquared = squared;
        }

        return best;
    }

    private boolean isHumanTarget(User user, Player player) {
        if (!player.isOnline() || player.isDead() || !player.getWorld().equals(entity.getWorld())) return false;

        return user.isPlayer() && user.inState(ArcadeState.ALIVE) && !user.isProtected()
                && (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || player.getLocation().distance(entity.getLocation()) <= SNIFF_RADIUS);
    }

    private boolean isInvader(Location location) {
        if (team.inIsland(location)) return true;

        Location home = team.getBase();

        return home != null && location.getWorld().equals(home.getWorld())
                && location.distance(home) <= INVADER_RADIUS;
    }

    private Team pickObjective(Location from) {
        List<Team> targets = arena.getTeamList().stream()
                .filter(candidate -> !candidate.equals(team))
                .filter(Team::hasBed)
                .filter(candidate -> candidate.getBedLocation() != null || candidate.getBase() != null)
                .collect(Collectors.toList());

        if (targets.isEmpty()) return null;

        if (objective != null && targets.contains(objective) && Core.RANDOM.nextInt(100) > 25) return objective;

        targets.sort(Comparator.comparingDouble(candidate -> from.distanceSquared(objectiveLocation(candidate))));

        /* Rush e Pontador atacam a cama mais perto; os outros variam para não
         * formarem fila no mesmo corredor. */
        BotMode mode = profile.getMode();

        int index = mode == BotMode.RUSH || mode == BotMode.BRIDGER ? 0
                : Math.min(targets.size() - 1, Core.RANDOM.nextInt(2));

        return targets.get(index);
    }

    private static Location objectiveLocation(Team team) {
        return team.getBedLocation() != null ? team.getBedLocation() : team.getBase();
    }

    /* Ação */

    private void act(Location location) {
        switch (goal) {
            case HUNT -> {
                if (target == null) {
                    goal = Goal.GUARD;
                    return;
                }

                if (horizontalDistance(location, target.getLocation()) <= profile.getEngageDistance())
                    fight(location);
                else
                    moveTo(location, target.getLocation());
            }
            case BED -> {
                if (objective == null) {
                    goal = Goal.GUARD;
                    return;
                }

                if (target != null && horizontalDistance(location, target.getLocation())
                        <= profile.getAttackDistance() * 2.0) {
                    fight(location);
                    return;
                }

                Location bed = objectiveLocation(objective).clone().add(0.5, 1.0, 0.5);

                if (location.distance(bed) < 3.0) mineBed(bed);
                else moveTo(location, bed);
            }
            case MINE -> {
                Location mid = midSpot();

                if (mid == null) {
                    goal = Goal.GUARD;
                    return;
                }

                if (location.distance(mid) < 2.5) {
                    navigation().n();
                    entity.setVelocity(new Vector(0, entity.getVelocity().getY(), 0));
                } else
                    moveTo(location, mid);
            }
            case RESUPPLY -> {
                Location base = team.getBase();

                if (base == null) {
                    goal = Goal.GUARD;
                    return;
                }

                if (location.distance(base) < 4.0) buy();
                else moveTo(location, base);
            }
            case RETREAT -> {
                Location base = team.getBase();

                if (base == null) {
                    goal = Goal.GUARD;
                    return;
                }

                if (entity.getHealth() >= profile.getMaxHealth() - 1) {
                    goal = Goal.GUARD;
                    return;
                }

                if (target != null && horizontalDistance(location, target.getLocation()) < 2.5) fight(location);
                else moveTo(location, base);
            }
            default -> guard(location);
        }
    }

    private void guard(Location location) {
        Location base = team.getBase();

        if (base == null) return;

        if (target != null && location.getWorld().equals(target.getWorld())
                && horizontalDistance(location, target.getLocation()) <= profile.getEngageDistance()) {
            fight(location);
            return;
        }

        /*Patrulha: alterna entre a cama e a borda da ilha, de frente para o
         * caminho por onde o inimigo costuma chegar. */
        if (patrol == null || ticks % 120 == 0 || location.distance(patrol) < 1.5) {
            BlockFace face = face(direction(base, nearestEnemyEdge(base)));

            patrol = base.clone().add(face.getModX() * 4 + Core.RANDOM.nextDouble() * 2 - 1, 0,
                    face.getModZ() * 4 + Core.RANDOM.nextDouble() * 2 - 1);
        }

        if (location.distance(patrol) > 1.0) moveTo(location, patrol);
        else navigation().n();
    }

    private Location nearestEnemyEdge(Location base) {
        return arena.getTeamList().stream()
                .filter(candidate -> !candidate.equals(team))
                .map(Team::getBase)
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(base::distanceSquared))
                .orElse(base);
    }

    private void fight(Location location) {
        Location goalLocation = target.getLocation();

        double distance = horizontalDistance(location, goalLocation);

        look(goalLocation.clone().add(0, 1.2, 0), false);

        /* Contra jogador vale o melee vanilla (é o mesmo caminho do MiniZombie).
         * Contra outro bot não: zumbi não mira zumbi, então o golpe é aplicado
         * direto pela IA — e o goal vanilla fica desligado para não bater em
         * dobro. */
        if (BotController.isBot(target)) {
            entity.setTarget(null);

            if (distance <= profile.getAttackDistance() && attackCooldown <= 0 && entity.hasLineOfSight(target)) {
                target.damage(strikeDamage(), entity);

                attackCooldown = 10;
            }
        } else
            entity.setTarget(target);

        strafe(location, distance);

        if (profile.isLeap() && entity.isOnGround() && leapCooldown <= 0 && distance > 2.2 && distance < 4.5
                && Math.abs(location.getY() - goalLocation.getY()) < 1.5 && entity.hasLineOfSight(target)) {
            entity.setVelocity(direction(location, goalLocation).multiply(0.6).setY(0.36));

            leapCooldown = 30;
            return;
        }

        /* Block hit / S-Tap: um recuo curto depois de bater, para o bot não
         * colar o corpo no alvo e perder o timing. */
        if (profile.isBlockHit() && distance <= profile.getAttackDistance() && hitCooldown <= 0) {
            entity.setVelocity(direction(location, goalLocation).multiply(-0.18)
                    .setY(entity.getVelocity().getY()));

            hitCooldown = 12;
        }
    }

    private void strafe(Location location, double distance) {
        double intensity = profile.getStrafe() * profile.getMode().getStrafe();

        if (intensity <= 0.01 || !entity.isOnGround() || distance > profile.getAttackDistance() * 2.5) return;

        strafeTicks++;

        Vector side = perpendicular(direction(location, target.getLocation()));

        /* Círculo: onda lenta e contínua. Strafe simples: troca brusca de lado. */
        int cycle = profile.isCircleStrafe() ? 16 : 90;

        double wave = Math.sin(Math.toRadians(strafeTicks * cycle)) * Math.min(1.0, intensity) * 0.12;

        Vector velocity = entity.getVelocity();

        entity.setVelocity(new Vector(velocity.getX() + side.getX() * wave, velocity.getY(),
                velocity.getZ() + side.getZ() * wave));
    }

    private void moveTo(Location from, Location to) {
        if (to == null || to.getWorld() == null || !to.getWorld().equals(from.getWorld())) return;

        Vector direction = direction(from, to);

        look(to, profile.getBridgeStyle().isFaceBackward() && target == null);

        /* Beira sem lã: o bot para na borda em vez de se jogar no void. */
        if (blocks <= 0 && horizontalDistance(from, to) > 1.0 && isVoidAhead(from, direction)) {
            navigation().n();
            entity.setVelocity(new Vector(0, entity.getVelocity().getY(), 0));

            goal = Goal.RESUPPLY;
            return;
        }

        boolean path = navigation().a(to.getX(), Math.max(to.getY(), from.getY()), to.getZ(), 1.3);

        if (path && !isStuck(from)) return;

        /* Sem caminho possível entre as ilhas só resta construir. */
        double height = to.getY() - from.getY();

        if (height > 1.5 && horizontalDistance(from, to) < 3.0) tower();
        else bridge(from, to);
    }

    private boolean isVoidAhead(Location from, Vector direction) {
        if (direction.lengthSquared() < 0.0001) return false;

        Block ahead = from.getBlock().getRelative(face(direction));

        if (ahead.getType().isSolid()) return false;

        Block floor = ahead;

        for (int i = 0; i < 12 && floor.getY() > 1; i++) {
            floor = floor.getRelative(BlockFace.DOWN);

            if (floor.getType().isSolid()) return false;
        }

        return true;
    }

    /* Construção */

    private void bridge(Location from, Location to) {
        if (blockCooldown > 0) return;

        BridgeStyle style = profile.getBridgeStyle();

        Vector direction = direction(from, to);

        if (direction.lengthSquared() < 0.0001) return;

        navigation().n();

        double speed = 0.24 * style.getSpeedFactor() * profile.getMode().getSpeed();

        if (style.isJump() && entity.isOnGround())
            entity.setVelocity(direction.clone().multiply(speed).setY(0.42));
        else
            entity.setVelocity(direction.clone().multiply(speed).setY(entity.getVelocity().getY()));

        Block feet = from.getBlock();

        int placed = 0;

        for (int[] offset : style.getOffsets()) {
            if (placed >= style.getBlocksPerCycle() || blocks <= 0) break;

            int forward = style.isFaceBackward() ? -offset[0] : offset[0];

            Block candidate = relative(feet, direction, forward, offset[1]).getRelative(BlockFace.DOWN);

            if (place(candidate)) {
                blocks--;
                placed++;
            }
        }

        blockCooldown = profile.bridgeCooldown();
    }

    private void tower() {
        if (!entity.isOnGround() || blockCooldown > 0 || blocks <= 0) return;

        Block feet = entity.getLocation().getBlock();

        if (feet.getRelative(BlockFace.UP, 2).getType().isSolid()) return;

        towerBase = feet;

        entity.setVelocity(new Vector(0, 0.45, 0));

        blockCooldown = profile.bridgeCooldown();
    }

    private void finishTower() {
        if (towerBase == null) return;

        if (entity.getLocation().getY() >= towerBase.getY() + 0.95) {
            if (place(towerBase)) blocks--;

            towerBase = null;
        } else if (entity.isOnGround()) {
            towerBase = null;
        }
    }

    private boolean saveFromVoid(Location location) {
        if (blocks <= 0 || entity.isOnGround() || entity.getVelocity().getY() > -0.3) return false;

        Block floor = location.getBlock();

        for (int i = 0; i < 40 && floor.getY() > 1; i++) {
            floor = floor.getRelative(BlockFace.DOWN);

            if (floor.getType().isSolid()) return false;
        }

        if (!place(location.getBlock().getRelative(BlockFace.DOWN))) return false;

        blocks--;

        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0F);

        return true;
    }

    private boolean place(Block block) {
        if (!block.isEmpty() && block.getType() != Material.LONG_GRASS) return false;

        Location location = block.getLocation();

        if (arena.isProtectedArea(location) || location.getY() >= arena.getMap().getBuildLimit()) return false;

        /* Não prende ninguém dentro do bloco. */
        for (Entity nearby : block.getWorld().getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 0.6, 0.9, 0.6)) {
            if (nearby instanceof Player) return false;
        }

        block.setType(Material.WOOL);
        block.setData((byte) ColorUtil.getIdByColor(team.getColor()));

        arena.addRollBack(block, RollbackBlock.RollbackType.PLACE_BLOCK);

        block.getWorld().playSound(location, Sound.DIG_WOOL, 0.7F, 1F);

        return true;
    }

    private void mineBed(Location bed) {
        bedTicks += PERIOD;

        navigation().n();
        look(bed, false);

        if (bedTicks < (int) (profile.getBedBreakSeconds() * 20)) return;

        bedTicks = 0;

        BotController.destroyBed(arena, objective, this);

        objective = null;
        goal = Goal.HUNT;
    }

    /* Economia */

    private void income(Location location) {
        if (team.inIsland(location)) {
            iron += profile.getIronPerSecond();

            goldRemainder += profile.getGoldPerSecond()
                    * (team.getForge().isLevel(GeneratorLevel.ONE) ? 1.5 : 1.0);

            if (goldRemainder >= 1) {
                int amount = (int) goldRemainder;

                gold += amount;
                goldRemainder -= amount;
            }
        }

        OreGenerator generator = nearGenerator(location);

        if (generator != null)
            diamond += generator.getLevel().ordinal() >= GeneratorLevel.TWO.ordinal() ? 2 : 1;
    }

    private OreGenerator nearGenerator(Location location) {
        for (OreGenerator generator : arena.getGenerators()) {
            Location spot = generator.getLocation();

            if (spot != null && spot.getWorld().equals(location.getWorld()) && spot.distance(location) <= 3.5)
                return generator;
        }

        return null;
    }

    private Location midSpot() {
        Location base = team.getBase();

        if (base == null) return null;

        return arena.getGenerators().stream()
                .map(OreGenerator::getLocation)
                .filter(Objects::nonNull)
                .filter(location -> location.getWorld().equals(base.getWorld()))
                .min(Comparator.comparingDouble(base::distanceSquared))
                .orElse(null);
    }

    private void buy() {
        while (iron >= profile.getWoolCostIron()
                && blocks + profile.getWoolBlocks() <= profile.getCapacity()) {
            iron -= profile.getWoolCostIron();
            blocks += profile.getWoolBlocks();
        }

        int tier = gold >= 32 ? 3 : gold >= 16 ? 2 : gold >= 8 ? 1 : 0;

        if (tier > gearTier && gold >= gearCost(tier)) {
            gold -= gearCost(tier);

            gearTier = tier;
            applyGear();
        }

        if (!profile.isBuyUpgrades() || !team.hasBed()) return;

        if (!team.isHighestSword()) {
            Sword next = team.getNextSword();

            if (diamond >= next.getAdjustedPrice(arena)) {
                diamond -= next.getAdjustedPrice(arena);

                team.getUpgrade().setSword(next);
                team.getPlayers().forEach(next::applyEnchantment);

                applyGear();
            }
        }

        if (!team.isHighestArmor()) {
            ArmorProtection next = team.getNextArmor();

            if (diamond >= next.getAdjustedPrice(arena)) {
                diamond -= next.getAdjustedPrice(arena);

                team.getUpgrade().setArmor(next);
                team.getPlayers().forEach(next::applyEnchantment);

                applyGear();
            }
        }

        if (profile.isBuyUpgrades()) {
            var forge = team.getForge();

            if (!forge.isHighest() && diamond >= forge.getNextCost()) {
                diamond -= forge.getNextCost();

                forge.upgrade();
            }
        }
    }

    private static int gearCost(int tier) {
        return switch (tier) {
            case 3 -> 32;
            case 2 -> 16;
            default -> 8;
        };
    }

    private void regenerate(Location location) {
        if (entity.getHealth() >= profile.getMaxHealth()) return;

        Location base = team.getBase();

        if (base == null || location.distance(base) > GUARD_RADIUS) return;

        entity.setHealth(Math.min(profile.getMaxHealth(), entity.getHealth() + 1));
    }

    /* Equipamento */

    private void applyGear() {
        if (entity == null || !entity.isValid()) return;

        EntityEquipment equipment = entity.getEquipment();

        if (equipment == null) return;

        Material sword = switch (gearTier) {
            case 3 -> Material.DIAMOND_SWORD;
            case 2 -> Material.IRON_SWORD;
            case 1 -> Material.STONE_SWORD;
            default -> Material.WOOD_SWORD;
        };

        equipment.setItemInHand(Item.of(sword).unbreakable());
        equipment.setItemInHandDropChance(0F);

        int armor = team.hasArmor() ? team.getArmor().ordinal() : 0;

        Material type = armor >= 4 ? Material.DIAMOND_HELMET
                : armor >= 3 ? Material.IRON_HELMET
                : armor >= 2 ? Material.CHAINMAIL_HELMET
                : Material.LEATHER_HELMET;

        equipment.setHelmet(armorPiece(type, "HELMET"));
        equipment.setChestplate(armorPiece(type, "CHESTPLATE"));
        equipment.setLeggings(armorPiece(type, "LEGGINGS"));
        equipment.setBoots(armorPiece(type, "BOOTS"));

        equipment.setHelmetDropChance(0F);
        equipment.setChestplateDropChance(0F);
        equipment.setLeggingsDropChance(0F);
        equipment.setBootsDropChance(0F);
        equipment.setItemInHandDropChance(0F);

        handle().getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).setValue(strikeDamage());
    }

    /** Dano do golpe: atributo base + equipamento próprio + espada do time. */
    private double strikeDamage() {
        return profile.getAttackDamage() + gearTier + (team.hasSword() ? team.getSword().ordinal() + 1 : 0);
    }

    private ItemStack armorPiece(Material helmetType, String slot) {
        Material material = Material.getMaterial(helmetType.name().replace("_HELMET", "_" + slot));

        if (material == null) return null;

        Item item = Item.of(material).unbreakable();

        return material == Material.LEATHER_HELMET || material == Material.LEATHER_CHESTPLATE
                || material == Material.LEATHER_LEGGINGS || material == Material.LEATHER_BOOTS
                ? item.leatherColor(team.getRgb()) : item;
    }

    /* Utilidades */

    private void updateName() {
        if (entity == null || !entity.isValid()) return;

        entity.setCustomName(getDisplayName() + " §c" + (int) Math.ceil(entity.getHealth()) + "❤");
    }

    private boolean isStuck(Location location) {
        if (lastLocation == null || !lastLocation.getWorld().equals(location.getWorld())) return false;

        stuckTicks = lastLocation.distanceSquared(location) < 0.0025 ? stuckTicks + PERIOD : 0;

        return stuckTicks >= 8;
    }

    private void look(Location goal, boolean invert) {
        double x = goal.getX(), y = goal.getY(), z = goal.getZ();

        /* Moonwalk: o bot segue o caminho olhando para trás, como o estilo pede. */
        if (invert) {
            x = entity.getLocation().getX() * 2 - goal.getX();
            z = entity.getLocation().getZ() * 2 - goal.getZ();
        }

        handle().getControllerLook().a(x, y + 1.4, z, 30F, 30F);
    }

    private NavigationAbstract navigation() {
        return handle().getNavigation();
    }

    private EntityInsentient handle() {
        return ((CraftZombie) entity).getHandle();
    }

    private static double horizontalDistance(Location a, Location b) {
        double x = a.getX() - b.getX(), z = a.getZ() - b.getZ();

        return Math.sqrt(x * x + z * z);
    }

    private static Vector direction(Location from, Location to) {
        Vector vector = new Vector(to.getX() - from.getX(), 0, to.getZ() - from.getZ());

        return vector.lengthSquared() < 0.0001 ? new Vector(0, 0, 0) : vector.normalize();
    }

    private static Vector perpendicular(Vector vector) {
        return new Vector(-vector.getZ(), 0, vector.getX());
    }

    private static BlockFace face(Vector direction) {
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ()))
            return direction.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;

        return direction.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static BlockFace right(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private static Block relative(Block feet, Vector direction, int forward, int side) {
        BlockFace face = face(direction);

        return feet.getRelative(face, forward).getRelative(right(face), side);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Se a entidade é um alvo legítimo para este bot (inimigo vivo da partida).
     * Usado pelo listener para não deixar o aggro vanilla do mob furar a regra
     * de time.
     */
    public boolean isEnemy(LivingEntity other) {
        return other != null && !other.equals(entity) && isValid(other);
    }

    private boolean isValid(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) return false;

        if (entity instanceof Player player) {
            User user = (User) User.of(player.getUniqueId());

            return user != null && isHumanTarget(user, player);
        }

        BedWarsBot bot = BotController.of(entity);

        return bot != null && bot.isAlive() && !bot.getTeam().equals(team);
    }

    /**
     * Guarda quem causou a morte do bot e devolve o crédito ao jogador.
     */
    public void onKilledBy(Player killer) {
        User user = (User) User.of(killer.getUniqueId());

        if (user == null || !arena.equals(user.getArena())) return;

        /* Abater bot sempre aparece no placar da partida. Estatística global,
         * moeda e TOP ficam atrás de kills-in-top para o bot não virar farm. */
        if (profile.isKillsInTop()) {
            user.setKills();
            arena.verifyTop(TopType.KILL, user.getAccount().getId(), user.getKills());
        } else
            user.getContext().setKills(user.getKills() + 1);

        if (profile.isGiveXp()) {
            int xp = Core.RANDOM.ints(4, 9).findFirst().orElse(4);

            user.getMember().addLevelXp(xp);
            user.setXpGain(xp);
            killer.sendMessage("§b+" + xp + " XP §7(abate de bot)");
        }

        arena.getArcade().updateSidebar(user);
    }
}
