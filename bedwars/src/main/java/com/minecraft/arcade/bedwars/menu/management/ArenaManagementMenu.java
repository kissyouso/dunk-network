package com.minecraft.arcade.bedwars.menu.management;

import com.minecraft.arcade.bedwars.arcade.arena.Arena;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.room.phase.RoomPhase;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ArenaManagementMenu extends Menu {

    private final Arena arena;

    public ArenaManagementMenu(Player player, Arena arena) {
        super(player, "Gerenciar Sala", 4);

        this.arena = arena;
    }

    @Override
    public void handle() {
        clear();

        addItem(4, Item.of(Material.SKULL_ITEM, 3, "§aInformações da Sala",
                        "§7ID: §f" + arena.getIdentifier(),
                        "§7Fase: §f" + getPhaseName(arena.getPhase()),
                        "§7Jogadores: §f" + arena.getTotalPlayers() + "/" + arena.getMaxPlayers(),
                        "§7Tempo: §f" + (arena.getTime() > 0 ? arena.getTime() + "s" : "N/A"))
                .skullByBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzI0NTAzNjc4MmM2YTdlMjVmZjg0YzUwODdhNjU5NjZkNjUwYzAzM2JhMzdlMDA5NjVlOGI5OTAyMWIzZWNhZSJ9fX0="));

        boolean isCountdownRunning = arena.isPhase(RoomPhase.STARTING);
        if (isCountdownRunning) {
            boolean counterEnabled = arena.getContext().isCounterEnabled();
            addItem(10, Item.of(Material.WATCH, counterEnabled ? "§cPausar Contador" : "§aRetomar Contador",
                            "§7Status: " + (counterEnabled ? "§aAtivo" : "§cPausado"),
                            "",
                            counterEnabled ? "§eClique para pausar o contador." : "§eClique para retomar o contador.")
                    .click(event -> {
                        close();
                        sound(MenuSound.DONE);

                        arena.getContext().setCounterEnabled(!counterEnabled);

                        arena.send(arena.getContext().isCounterEnabled()
                                ? "§aO temporizador foi ativado."
                                : "§cO temporizador foi pausado.");

                        getPlayer().sendMessage(arena.getContext().isCounterEnabled()
                                ? "§aContador retomado com sucesso!"
                                : "§cContador pausado com sucesso!");
                    }));
        }

        /* O piso antigo pedia 2 jogadores até no atalho administrativo, mas quem
         * força o início está testando sozinho: basta haver alguém na sala
         * (Arena.canBeCount respeita o ignoreMinPlayers). */
        boolean canStartCountdown = arena.isPhase(RoomPhase.WAITING) && arena.getTotalPlayers() >= 1;
        addItem(11, Item.of(Material.GOLD_INGOT, canStartCountdown ? "§aIniciar Contador" : "§cNão disponível",
                        canStartCountdown ? "§7Force o início do contador." : "§7A sala está vazia ou a partida já iniciou.",
                        "",
                        "§7Ignora o número mínimo de jogadores.",
                        "",
                        canStartCountdown ? "§eClique para iniciar!" : "§cNão é possível iniciar.")
                .click(event -> {
                    if (!canStartCountdown) {
                        sound(MenuSound.ERROR);

                        getPlayer().sendMessage("§cA sala está vazia.");
                        return;
                    }

                    close();
                    sound(MenuSound.DONE);

                    arena.getContext().setCounterEnabled(true);
                    arena.getContext().setIgnoreMinPlayers(true);
                    
                    arena.setPhase(RoomPhase.STARTING);
                    
                    arena.send("§a" + getPlayer().getName() + " §einiciou o contador manualmente!");
                    getPlayer().sendMessage("§aContador iniciado com sucesso!");
                }));

        boolean canStart = (arena.isPhase(RoomPhase.WAITING) || arena.isPhase(RoomPhase.STARTING)) && arena.getTotalPlayers() >= 1;
        addItem(12, Item.of(Material.DIAMOND_SWORD, canStart ? "§aIniciar Sala" : "§cNão disponível",
                        canStart ? "§7Inicie a partida imediatamente." : "§7A partida já está em andamento ou a sala está vazia.",
                        "",
                        "§7Jogadores: §f" + arena.getTotalPlayers(),
                        "",
                        canStart ? "§eClique para iniciar!" : "§cNão é possível iniciar.")
                .click(event -> {
                    if (!canStart) {
                        sound(MenuSound.ERROR);
                        getPlayer().sendMessage("§cA sala está vazia.");
                        return;
                    }
                    close();
                    sound(MenuSound.DONE);

                    arena.setPhase(RoomPhase.PLAYING);
                    arena.send("§a" + getPlayer().getName() + " §einiciou a partida manualmente!");
                }));

        addItem(14, Item.of(Material.BARRIER, "§cExpulsar Jogadores",
                        "§7Expulse jogadores da sala.",
                        "",
                        "§7Jogadores na sala: §f" + arena.getTotalPlayers(),
                        "",
                        "§eClique para ver a lista!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new PlayerKickMenu(getPlayer(), arena, this).handle();
                }));

        addItem(16, Item.of(Material.REDSTONE, "§eReiniciar Sala",
                        "§7Reinicie a sala completamente.",
                        "",
                        "§cAtenção: Todos os jogadores serão expulsos!",
                        "",
                        "§eClique para reiniciar!")
                .click(event -> {
                    close();

                    if (arena.getTotalPlayers() > 0) {
                        sound(MenuSound.ERROR);
                        getPlayer().sendMessage("§cVocê precisa expulsar todos os jogadores antes de reiniciar a sala.");
                        return;
                    }

                    sound(MenuSound.DONE);
                    arena.reload(getPlayer());
                    getPlayer().sendMessage("§aSala reiniciada com sucesso!");
                }));

        addItem(16, Item.of(Material.MONSTER_EGG, 54, "§aBots de Treino",
                        "§7Controle a IA que completa a sala.",
                        "",
                        "§7Entra no lugar de quem falta para a",
                        "§7partida começar e treinar ponte e PvP.",
                        "",
                        "§eClique para abrir!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new BotMenu(getPlayer(), arena).handle();
                }));

        addItem(31, Item.of(Material.ARROW, "§7Voltar")
                .click(event -> {
                    close();
                    sound(MenuSound.PAGINATED);
                }));

        display();
    }

    private String getPhaseName(RoomPhase phase) {
        switch (phase) {
            case WAITING:
                return "Aguardando";
            case STARTING:
                return "Iniciando";
            case PLAYING:
                return "Em Jogo";
            case ENDING:
                return "Finalizando";
            default:
                return "Desconhecida";
        }
    }
}

