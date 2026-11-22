package com.minecraft.arcade.bedwars.listener;

import com.minecraft.arcade.bedwars.arcade.arena.Arena;
import com.minecraft.arcade.bedwars.structure.bot.BedWarsBot;
import com.minecraft.arcade.bedwars.structure.bot.BotController;
import com.minecraft.arcade.bedwars.structure.team.Team;
import com.minecraft.arcade.bedwars.user.User;
import com.minecraft.core.arcade.route.state.ArcadeState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Regras de dano/aggro dos bots de treino.
 * <p>
 * O cérebro do bot já escolhe inimigos, mas o aggro e o dano continuam passando
 * pelo pipeline vanilla do servidor. Aqui entra o que a IA não controla: fogo
 * amigo entre o próprio time, o impulso de knockback configurado, o nome do bot
 * na mensagem de morte (bot não é {@link User}) e o crédito de abate quando o
 * jogador derruba um bot — inclusive de bot, quando os times inimigos se
 * caçam.
 */
public class BotListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        BedWarsBot bot = BotController.of(event.getEntity());

        if (bot == null) return;

        /* O goal de alvo do vanilla não conhece time: sem este filtro o bot
         * passaria a seguir qualquer entidade viva do mapa. */
        if (!(event.getTarget() instanceof LivingEntity target) || !bot.isEnemy(target))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity attacker = source(event.getDamager());

        BedWarsBot victimBot = BotController.of(victim);
        BedWarsBot attackerBot = BotController.of(attacker);

        if (victimBot == null && attackerBot == null) return;

        Team victimTeam = teamOf(victim), attackerTeam = teamOf(attacker);

        /* Fora da partida (spectator, admin, mob solto) ninguém bate em bot, e
         * companheiro de time também não. */
        if (victimTeam == null || attackerTeam == null || victimTeam.equals(attackerTeam)) {
            event.setCancelled(true);
            return;
        }

        if (attackerBot != null && victim instanceof Player player) {
            BotController.registerAttack(attackerBot, player);
            knockback(attackerBot, victim);
        }

        if (victimBot != null && attacker instanceof LivingEntity living)
            victimBot.setLastAttacker(living);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        BedWarsBot bot = BotController.of(event.getEntity());

        if (bot == null) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        LivingEntity attacker = bot.getLastAttacker();

        if (attacker instanceof Player player)
            bot.onKilledBy(player);
        else if (attacker != null && BotController.isBot(attacker))
            announce(bot.getArena(), BotController.of(attacker), bot);

        bot.handleDeath();
    }

    /**
     * Abate de bot por bot: mostra no placar da sala, senão quem está assistindo
     * só vê a entidade sumir.
     */
    private void announce(Arena arena, BedWarsBot killer, BedWarsBot victim) {
        if (killer == null) return;

        arena.send("§f§lABATE §f» " + killer.getTeam().getColor() + killer.getName()
                + " §7abateu " + victim.getTeam().getColor() + victim.getName() + "§7.");
    }

    private void knockback(BedWarsBot bot, Entity victim) {
        double strength = bot.getProfile().getKnockback();

        if (strength <= 0.0 || bot.getEntity() == null) return;

        Vector push = victim.getLocation().toVector()
                .subtract(bot.getEntity().getLocation().toVector());

        push.setY(0.0);

        if (push.lengthSquared() <= 0.0001) return;

        victim.setVelocity(victim.getVelocity().add(push.normalize().multiply(strength * 0.4)
                .setY(Math.max(0.16, strength * 0.16))));
    }

    private static Entity source(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)
            return shooter;

        return damager;
    }

    private static Team teamOf(Entity entity) {
        if (entity instanceof Player player) {
            User user = (User) User.of(player.getUniqueId());

            /* Espectador, morto e admin fora da sala não têm time: o dano é
             * cortado por cima. */
            if (user == null || !user.isPlayer() || !user.inState(ArcadeState.ALIVE)) return null;

            return user.getTeam();
        }

        BedWarsBot bot = BotController.of(entity);

        return bot == null ? null : bot.getTeam();
    }
}
