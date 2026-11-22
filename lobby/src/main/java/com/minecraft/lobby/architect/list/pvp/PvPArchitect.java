package com.minecraft.lobby.architect.list.pvp;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.academy.AcademyCount;
import com.minecraft.core.account.context.objects.medal.Medal;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.hologram.type.server.HologramServer;
import com.minecraft.core.bukkit.api.npc.type.server.NpcServer;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.event.type.update.type.UpdateType;
import com.minecraft.core.bukkit.event.type.update.type.list.AsyncUpdateEvent;
import com.minecraft.core.bukkit.manager.list.TagManager;
import com.minecraft.core.bukkit.menu.server.academy.AcademyRoute;
import com.minecraft.core.bukkit.menu.server.academy.AcademySkins;
import com.minecraft.core.bukkit.menu.server.academy.DamageMenu;
import com.minecraft.core.bukkit.menu.server.arcade.mode.duels.DuelsMenu;
import com.minecraft.core.member.list.pvp.PvPMember;
import com.minecraft.core.member.list.pvp.stats.list.ArenaStats;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.Util;
import com.minecraft.lobby.architect.Architect;
import com.minecraft.lobby.user.User;
import com.mojang.authlib.properties.Property;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lobby da Academy (antigo lobby de PvP).
 * NPCs (locations): npc_timers, npc_lava, npc_damage, npc_texture, npc_duels e npc_stats.
 */
public class PvPArchitect extends Architect {

    /* Treinos do servidor da Academy: chave da location -> arena */
    private static final String[][] TRAININGS = {
            {"timers", "Timers"}, {"lava", "Lava"}, {"texture", "Texture"}
    };

    public PvPArchitect() {
        super(ServerType.HUB_PVP);
    }

    @Override
    public void join(Player player) {
        UUID id = player.getUniqueId();

        PvPMember member = Core.getPvpData().of(id, true);

        if (member == null)
            member = Core.getPvpData().save(new PvPMember(id, player.getName()));

        Core.getMemberController().save(member);
        Core.getPvpData().cancelExpiration(id);

        User user = (User) User.of(player.getUniqueId());
        super.join(player);

        // Atualizar sidebar após salvar o membro para garantir que os dados estejam disponíveis
        if (user != null) {
            handleSidebar(user);
        }
    }

    @Override
    public void quit(Player player) {
        super.quit(player);

        Core.getPvpData().startExpiration(player.getUniqueId());
    }

    @Override
    public void handleSidebar(User user) {
        PvPMember member = user.getMember(PvPMember.class);

        Sidebar sidebar = user.getSidebar();

        ArenaStats stats = member != null ? member.getArenaStats() : null;

        sidebar.clear();
        sidebar.setTitle("§a§lACADEMY");
        sidebar.blankRow();

        sidebar.addRow("text_one", "§7Bem-vindo à academia,");
        sidebar.addRow("text_two", "§7molde suas habilidades!");
        sidebar.blankRow();

        sidebar.addRow("kills", "§fKills: §a" + Util.formatNumber(stats != null ? stats.getKills() : 0));
        sidebar.addRow("deaths", "§fDeaths: §a" + Util.formatNumber(stats != null ? stats.getDeaths() : 0));
        sidebar.addRow("killstreak", "§fKillstreak: §a" + Util.formatNumber(stats != null ? stats.getKillStreak() : 0));
        sidebar.blankRow();

        sidebar.addRow("coins", "§fCoins: §6" + Util.formatNumber(member != null ? member.getCoins() : 0));
        sidebar.blankRow();
        sidebar.addWebsiteRow();

        sidebar.display();
        TagManager.updateTag(user.getAccount());
    }

    @Override
    public void handleEntities() {
        super.handleEntities();

        Location statsLoc = getLocation("npc_stats");

        if (statsLoc != null)
            handleStatsNpc(statsLoc);

        /* Treinos (servidor da Academy) */
        spawnTraining("timers", "Timers", AcademySkins.EDITOR);
        spawnTraining("lava", "Lava", AcademySkins.LAVA);
        spawnTraining("texture", "Texture", null);

        Location damageLoc = getLocation("npc_damage");

        if (damageLoc != null) {
            Consumer<Player> damage = player -> {
                Account account = Core.getAccountController().of(player.getUniqueId());

                if (account != null)
                    new DamageMenu(player, account, () -> AcademyRoute.send(player, "Damage"), null, null).handle();
            };

            spawnNpc(damageLoc, AcademySkins.DAMAGE, damage);

            HologramServer hologram = BukkitCore.getManager().getHologram().spawnServer("academy_damage", damageLoc);
            hologram.setText(Arrays.asList(
                    "§a§lDAMAGE",
                    "§eClique para configurar!"
            ));
            hologram.setTouch((player, touch) -> damage.accept(player));
        }

        Location duelsLoc = getLocation("npc_duels");

        if (duelsLoc != null) {
            Consumer<Player> duels = player -> new DuelsMenu(player).handle();

            spawnNpc(duelsLoc, null, duels);
            counterHologram("duels", duelsLoc, duels);
        }
    }

    private void spawnTraining(String key, String arena, Property skin) {
        Location location = getLocation("npc_" + key);

        if (location == null) return;

        Consumer<Player> action = player -> AcademyRoute.send(player, arena);

        spawnNpc(location, skin, action);
        counterHologram(key, location, action);
    }

    private void spawnNpc(Location location, Property skin, Consumer<Player> action) {
        NpcServer npc = skin != null
                ? BukkitCore.getManager().getNpc().spawnServer(location, skin)
                : BukkitCore.getManager().getNpc().spawnServer(location);

        npc.setContact(true);
        npc.setAction((player, click) -> action.accept(player));
        npc.display();
    }

    /* Holograma "NOME / N jogando!" (atualizado a cada segundo) */
    private void counterHologram(String key, Location location, Consumer<Player> action) {
        HologramServer hologram = BukkitCore.getManager().getHologram().spawnServer("academy_" + key, location);

        hologram.setText(Arrays.asList("§a§l" + key.toUpperCase(), "§e... jogando!"));
        hologram.setTouch((player, touch) -> action.accept(player));
    }

    @EventHandler
    public void onCounterUpdate(AsyncUpdateEvent event) {
        if (!event.isType(UpdateType.SECOND)) return;

        Map<String, Integer> academy;

        try {
            academy = AcademyCount.totals();
        } catch (Exception e) {
            academy = Collections.emptyMap();
        }

        for (String[] training : TRAININGS)
            updateCounter(training[0], academy.getOrDefault(training[1], 0));

        updateCounter("duels", Core.getServerData().getOnlinePlayers(ServerType.HUB_DUELS, ServerType.DUELS));
    }

    private void updateCounter(String key, int players) {
        HologramServer hologram = BukkitCore.getManager().getHologram().getServer("academy_" + key);

        if (hologram == null || hologram.getRows().size() < 2) return;

        /* O treino roda no servidor da Academy; o atalho de Duels aponta para o Duels */
        ServerType type = key.equals("duels") ? ServerType.DUELS : ServerType.ACADEMY;

        hologram.setText(hologram.getRows().size() - 1, Architect.statusLine(type, players));
    }

    @Override
    public void chat(Player player, String message) {
        User user = (User) User.of(player.getUniqueId());

        Account account = user.getAccount();

        player.getWorld().getPlayers().forEach(worldPlayer -> {
            Account target = Core.getAccountController().of(worldPlayer.getUniqueId());
            if (target == null || (!target.equals(account) && !target.getToggle().isAllowChat())) return;

            StringBuilder builder = new StringBuilder();

            if (account.getMedal() != null && !account.getMedal().equals(Medal.NONE)) {
                builder.append(account.getMedal().getColoredSymbol()).append(" ");
            }

            builder.append(account.formatTag(target.getTagPrefix()))
                    .append(account.getNickname())
                    .append(": ")
                    .append(account.isAllowColoredChat() ? "§f" + Util.color(message) : "§7" + message);

            target.send(TextComponent.fromLegacyText(builder.toString()));
        });
    }

    @Override
    public Vector getSlimeJump(Vector direction) {
        return direction.multiply(0.8).setY(0.4);
    }
}
