package com.minecraft.arcade.bedwars.menu.management;

import com.minecraft.arcade.bedwars.arcade.arena.Arena;
import com.minecraft.arcade.bedwars.structure.bot.BotController;
import com.minecraft.arcade.bedwars.structure.bot.BotMode;
import com.minecraft.arcade.bedwars.structure.bot.BotProfile;
import com.minecraft.arcade.bedwars.structure.bot.BridgeStyle;
import com.minecraft.arcade.bedwars.structure.team.Team;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.room.phase.RoomPhase;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Painel de controle da IA de treino da sala: cada aba é uma categoria do
 * comportamento e os ajustes caem no perfil da sala
 * ({@link BotController#profile(Arena)}), nunca no config.yml do servidor.
 */
public class BotMenu extends Menu {

    private enum Section {
        PAINEL("§rBots de Treino"),
        PONTE("Bots » Ponte"),
        COMBATE("Bots » Combate"),
        MOVIMENTO("Bots » Movimento"),
        INTELIGENCIA("Bots » Inteligência"),
        ESTRATEGIA("Bots » Estratégia"),
        INVENTARIO("Bots » Inventário"),
        AJUSTES("Bots » Ajustes");

        private final String title;

        Section(String title) {
            this.title = title;
        }
    }

    private final Arena arena;
    private final BotProfile profile;
    private final Section section;

    public BotMenu(Player player, Arena arena) {
        this(player, arena, Section.PAINEL);
    }

    private BotMenu(Player player, Arena arena, Section section) {
        super(player, section.title, 5);

        this.arena = arena;
        this.profile = BotController.profile(arena);
        this.section = section;
    }

    @Override
    public void handle() {
        clear();

        switch (section) {
            case PAINEL -> panel();
            case PONTE -> bridging();
            case COMBATE -> combat();
            case MOVIMENTO -> movement();
            case INTELIGENCIA -> intelligence();
            case ESTRATEGIA -> strategy();
            case INVENTARIO -> inventory();
            case AJUSTES -> settings();
        }

        footer();

        display();
    }

    /* Painel */

    private void panel() {
        addItem(4, Item.of(Material.SKULL_ITEM, 3, "§6§lBOTS DE TREINO",
                        "§7Ativos: " + (profile.isEnabled() ? "§asim" : "§cnão"),
                        "§7Vivos na sala: §f" + BotController.alive(arena) + "§7/" + profile.getMaxTotal(),
                        "§7Humanos: §f" + arena.getTotalPlayers(),
                        "",
                        "§7Modo: §f" + profile.getMode().getLabel(),
                        "§7Ponte: §f" + profile.getBridgeStyle().getLabel(),
                        "§7Time: " + (profile.isGroupInOneTeam()
                                ? " " + teamLabel(profile.getTeam())
                                : " §fespalhado por time"),
                        "",
                        "§eClique para chamar um bot.")
                .skullByBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzI0NTAzNjc4MmM2YTdlMjVmZjg0YzUwODdhNjU5NjZkNjUwYzAzM2JhMzdlMDA5NjVlOGI5OTAyMWIzZWNhZSJ9fX0=")
                .click(event -> summon(event.isRightClick() ? 2 : 1)));

        addItem(10, page(Section.PONTE, Material.BRICK, "Padrão de ponte",
                "§7" + profile.getBridgeStyle().getLabel() + " §7a §f" + format(profile.getBlocksPerSecond()) + "§7 blocos/s"));
        addItem(11, page(Section.COMBATE, Material.DIAMOND_SWORD, "Combate",
                "§7Vida §f" + format(profile.getMaxHealth()) + "§7❤ §7| Dano §f" + format(profile.getAttackDamage())));
        addItem(12, page(Section.MOVIMENTO, Material.FEATHER, "Movimento",
                "§7Velocidade §f" + format(profile.getSpeed()) + " §7| Strafe §f" + format(profile.getStrafe())));
        addItem(13, page(Section.INTELIGENCIA, Material.BOOK, "Inteligência",
                "§7" + profile.getMode().getLabel() + " §7| Engaje §f" + format(profile.getEngageDistance()) + " blocos"));
        addItem(14, page(Section.ESTRATEGIA, Material.BED_BLOCK, "Estratégia de cama",
                "§7Guardar " + on(profile.isGuardBed()) + " §7| Atacar " + on(profile.isAttackBeds())));
        addItem(15, page(Section.INVENTARIO, Material.CHEST, "Inventário e economia",
                "§7" + profile.getIronPerSecond() + " ferro/s §7| " + format(profile.getGoldPerSecond()) + " ouro/s"));
        addItem(16, page(Section.AJUSTES, Material.COMPASS, "Ajustes da sala",
                "§7Máximo §f" + profile.getMaxTotal() + " §7| Por time §f" + profile.getMaxPerTeam()));

        addItem(28, toggle(Material.REDSTONE, "Bots ligados", "§7Liga ou desliga a IA desta sala.",
                profile::isEnabled, profile::setEnabled));

        addItem(30, Item.of(Material.IRON_SWORD, "§aChamar bot",
                        "§7Manda um bot agora, sem esperar a próxima partida.",
                        "",
                        "§eEsquerdo: §f1 bot",
                        "§eDireito: §f2 bots")
                .click(event -> summon(event.isRightClick() ? 2 : 1)));

        addItem(32, Item.of(Material.BARRIER, "§cDispensar bots",
                        "§7Tira todos os bots da sala.",
                        "",
                        "§7Vivos agora: §f" + BotController.alive(arena),
                        "",
                        "§eClique para dispensar.")
                .click(event -> {
                    int alive = BotController.alive(arena);

                    BotController.despawn(arena);

                    sound(alive > 0 ? MenuSound.ERROR : MenuSound.DONE);
                    getPlayer().sendMessage("§a" + alive + " bot(s) saíram da sala.");

                    apply();
                }));
    }

    private Item page(Section target, Material material, String name, String status) {
        return Item.of(material, "§a" + name, status, "", "§eClique para abrir.")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new BotMenu(getPlayer(), arena, target).handle();
                });
    }

    private void summon(int count) {
        if (!arena.isPhase(RoomPhase.PLAYING)) {
            sound(MenuSound.ERROR);
            getPlayer().sendMessage("§cSó é possível mandar bots durante a partida.");
            return;
        }

        int created = BotController.add(arena, count);

        sound(created > 0 ? MenuSound.SUCCESS : MenuSound.ERROR);

        getPlayer().sendMessage(created > 0
                ? "§a" + created + " bot(s) entraram na sala."
                : "§cNenhum time com base disponível para o bot.");

        handle();
    }

    /* Abas */

    private void bridging() {
        cycle(11, Material.BRICK, "Estilo de ponte", "§7Forma como o bot cruza um vão.",
                profile.getBridgeStyle(), BridgeStyle.values(), profile::setBridgeStyle,
                BridgeStyle::getLabel, style -> "§7" + style.getBlocksPerCycle() + " bloco(s) por ciclo");

        step(13, Material.SUGAR, "Blocos por segundo", "§7Ritmo da construção.", "",
                profile::getBlocksPerSecond, profile::setBlocksPerSecond, 0.5, 8.0, 0.25);

        step(15, Material.CHEST, "Blocos carregados", "§7Quanta lã o bot leva no inventário.", "",
                () -> (double) profile.getCapacity(), value -> profile.setCapacity((int) Math.round(value)), 16.0, 256.0, 16.0);
    }

    private void combat() {
        step(11, Material.POTION, "Vida máxima", "§7Coração do bot.", "❤",
                profile::getMaxHealth, profile::setMaxHealth, 6.0, 40.0, 2.0);

        step(13, Material.DIAMOND_SWORD, "Dano do golpe", "§7Dano base, sem contar a espada do time.", "",
                profile::getAttackDamage, profile::setAttackDamage, 1.0, 10.0, 1.0);

        step(15, Material.ARROW, "Alcance do golpe", "§7Distância em que o bot acerta o alvo.", " blocos",
                profile::getAttackDistance, profile::setAttackDistance, 2.0, 6.0, 0.5);

        step(20, Material.SPECKLED_MELON, "Knockback", "§7Força do empurrão no acerto.", "",
                profile::getKnockback, profile::setKnockback, 0.0, 1.0, 0.05);

        step(22, Material.GOLDEN_APPLE, "Vida de retirada", "§7Abaixo disto o bot recua, se o modo permitir.", "❤",
                profile::getRetreatHealth, profile::setRetreatHealth, 1.0, 20.0, 1.0);

        addItem(24, toggle(Material.IRON_DOOR, "Block hit",
                "§7Trocar de item ao bater, como no combo de PvP.", profile::isBlockHit, profile::setBlockHit));
    }

    private void movement() {
        step(11, Material.SUGAR, "Velocidade", "§7Deslocamento da entidade.", "",
                profile::getSpeed, profile::setSpeed, 0.1, 0.6, 0.01);

        step(13, Material.RABBIT_FOOT, "Strafe", "§7Desvio lateral durante a luta.", "",
                profile::getStrafe, profile::setStrafe, 0.0, 1.0, 0.05);

        addItem(15, toggle(Material.DIAMOND_BOOTS, "Circle strafe",
                "§7Contornar o inimigo em vez de ficar em linha.", profile::isCircleStrafe, profile::setCircleStrafe));

        addItem(20, toggle(Material.FEATHER, "Salto de ataque",
                "§7Pular ao iniciar o golpe.", profile::isLeap, profile::setLeap));
    }

    private void intelligence() {
        cycle(11, Material.BOOK, "Modo de jogo", "§7Pesos de decisão: caça, cama e defesa.",
                profile.getMode(), BotMode.values(), profile::setMode,
                BotMode::getLabel, mode -> "§7" + mode.getDescription());

        step(13, Material.EYE_OF_ENDER, "Distância de engaje", "§7De onde o bot parte para cima do alvo.", " blocos",
                profile::getEngageDistance, profile::setEngageDistance, 4.0, 40.0, 1.0);

        addItem(15, toggle(Material.SLIME_BLOCK, "Preencher companheiros",
                "§7Deixar bots no time de quem já tem jogador.",
                profile::isFillTeammates, profile::setFillTeammates));
    }

    private void strategy() {
        addItem(11, toggle(Material.BED_BLOCK, "Guardar a própria cama",
                "§7Voltar para casa quando alguém invade a ilha.",
                profile::isGuardBed, profile::setGuardBed));

        addItem(13, toggle(Material.TNT, "Atacar camas",
                "§7Cruzar o mapa para desmontar a cama inimiga.",
                profile::isAttackBeds, profile::setAttackBeds));

        addItem(15, toggle(Material.DIAMOND, "Comprar melhorias",
                "§7Gastar ferro e ouro no upgrade do time.",
                profile::isBuyUpgrades, profile::setBuyUpgrades));

        step(20, Material.IRON_PICKAXE, "Tempo para quebrar cama", "§7Segundos martelando a cama.", "s",
                profile::getBedBreakSeconds, profile::setBedBreakSeconds, 0.5, 8.0, 0.5);
    }

    private void inventory() {
        step(11, Material.IRON_INGOT, "Ferro por segundo", "§7Renda que o bot acumula.", "",
                () -> (double) profile.getIronPerSecond(), value -> profile.setIronPerSecond((int) Math.round(value)), 0.0, 5.0, 1.0);

        step(13, Material.GOLD_INGOT, "Ouro por segundo", "§7Renda de ouro do bot.", "",
                profile::getGoldPerSecond, profile::setGoldPerSecond, 0.0, 2.0, 0.1);

        step(15, Material.WOOL, "Custo da lã", "§7Ferro necessário para reabastecer.", "",
                () -> (double) profile.getWoolCostIron(), value -> profile.setWoolCostIron((int) Math.round(value)), 1.0, 16.0, 1.0);

        step(20, Material.STAINED_CLAY, "Lã por compra", "§7Blocos recebidos em cada compra.", "",
                () -> (double) profile.getWoolBlocks(), value -> profile.setWoolBlocks((int) Math.round(value)), 8.0, 64.0, 4.0);

        addItem(22, toggle(Material.PAPER, "Abate no placar",
                "§7Contar o abate de bot como kill do jogador e no TOP.",
                profile::isKillsInTop, profile::setKillsInTop));

        addItem(24, toggle(Material.EXP_BOTTLE, "XP por abate",
                "§7Dar experiência a quem mata o bot.",
                profile::isGiveXp, profile::setGiveXp));
    }

    private void settings() {
        addItem(11, toggle(Material.REDSTONE, "Bots ligados",
                "§7Desligado, a sala volta a exigir jogadores reais.",
                profile::isEnabled, profile::setEnabled));

        addItem(13, toggle(Material.GOLD_INGOT, "Completar salas curtas",
                "§7Mandar bots mesmo com mais de um humano na sala.",
                profile::isFillShortRooms, profile::setFillShortRooms));

        addItem(15, toggle(Material.STORAGE_MINECART, "Juntos no mesmo time",
                "§7Todos os bots no mesmo time adversário. Espalhados, eles se matam entre si antes de chegar em alguém.",
                profile::isGroupInOneTeam, profile::setGroupInOneTeam));

        team(20);

        step(22, Material.IRON_SWORD, "Bots no total", "§7Limite de bots por sala.", "",
                () -> (double) profile.getMaxTotal(), value -> profile.setMaxTotal((int) Math.round(value)), 1.0, 8.0, 1.0);

        step(24, Material.ARMOR_STAND, "Bots por time", "§7Limite de bots em cada time distribuído.", "",
                () -> (double) profile.getMaxPerTeam(), value -> profile.setMaxPerTeam((int) Math.round(value)), 1.0, 4.0, 1.0);

        step(26, Material.WATCH, "Renascimento", "§7Segundos até o bot voltar.", "s",
                () -> (double) profile.getRespawnSeconds(), value -> profile.setRespawnSeconds((int) Math.round(value)), 0.0, 30.0, 1.0);
    }

    /** Time do agrupamento: roda os times da sala e o modo "primeiro time livre". */
    private void team(int slot) {
        List<Team> teams = arena.getTeamList();

        String current = profile.getTeam();

        addItem(slot, Item.of(Material.WOOL, "§aTime dos bots",
                        "§7Para onde vão os bots agrupados.",
                        "",
                        "§7" + teamLabel(current),
                        "",
                        "§eEsquerdo: §fpróximo",
                        "§eDireito: §fanterior")
                .click(event -> {
                    if (teams.isEmpty()) {
                        sound(MenuSound.ERROR);
                        return;
                    }

                    int index = teamIndex(teams, current) + (event.isRightClick() ? -1 : 1);

                    /* Um item a mais na roda: o "livre", que deixa a sala escolher. */
                    index = Math.floorMod(index, teams.size() + 1);

                    profile.setGroupInOneTeam(true);
                    profile.setTeam(index >= teams.size() ? "" : teams.get(index).getTypeName());

                    sound(MenuSound.DONE);
                    handle();
                }));
    }

    private String teamLabel(String name) {
        if (name.isEmpty()) return "§fo primeiro time livre";

        Team team = arena.getTeamList().stream()
                .filter(search -> search.getTypeName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);

        return team == null ? "§f" + name + " §7(não está na sala)" : team.getColor() + name;
    }

    private int teamIndex(List<Team> teams, String name) {
        for (int index = 0; index < teams.size(); index++)
            if (teams.get(index).getTypeName().equalsIgnoreCase(name)) return index;

        return teams.size();
    }

    /* Blocos reutilizáveis */

    /** Reaplica o perfil nos bots vivos e redesenha a aba. */
    private void apply() {
        BotController.reload(arena);
        handle();
    }

    private Item toggle(Material material, String name, String description,
                        Supplier<Boolean> getter, Consumer<Boolean> setter) {
        boolean enabled = getter.get();

        return Item.of(material, (enabled ? "§a" : "§c") + name, description, "",
                        "§7Estado: " + (enabled ? "§aAtivo" : "§cInativo"),
                        "",
                        "§eClique para alternar!")
                .click(event -> {
                    setter.accept(!getter.get());

                    sound(MenuSound.DONE);
                    apply();
                });
    }

    private <T> void cycle(int slot, Material material, String name, String description, T current, T[] values,
                           Consumer<T> setter, Function<T, String> label, Function<T, String> detail) {
        addItem(slot, Item.of(material, "§a" + name + ": §f" + label.apply(current),
                        "§7" + description,
                        "",
                        "§7" + detail.apply(current),
                        "",
                        "§eEsquerdo: §fpróximo",
                        "§eDireito: §fanterior")
                .click(event -> {
                    int index = Math.floorMod(indexOf(values, current) + (event.isRightClick() ? -1 : 1), values.length);

                    setter.accept(values[index]);

                    sound(MenuSound.DONE);
                    apply();
                }));
    }

    private <T> int indexOf(T[] values, T value) {
        for (int index = 0; index < values.length; index++)
            if (values[index].equals(value)) return index;

        return -1;
    }

    private void step(int slot, Material material, String name, String description, String unit,
                      Supplier<Double> getter, DoubleConsumer setter, double min, double max, double delta) {
        addItem(slot, Item.of(material, "§a" + name,
                        "§7" + description,
                        "",
                        "§7Valor: §f" + format(getter.get()) + unit,
                        "",
                        "§eEsquerdo: §f+ " + format(delta) + unit,
                        "§eDireito: §f- " + format(delta) + unit,
                        "",
                        "§7Entre §f" + format(min) + "§7 e §f" + format(max) + "§7.")
                .click(event -> {
                    double value = getter.get() + (event.isRightClick() ? -delta : delta);

                    setter.accept(Math.max(min, Math.min(max, value)));

                    sound(MenuSound.DONE);
                    apply();
                }));
    }

    private void footer() {
        if (section == Section.PAINEL) {
            addItem(40, Item.of(Material.BARRIER, "§cFechar")
                    .click(event -> {
                        sound(MenuSound.ERROR);
                        close();
                    }));

            return;
        }

        addItem(40, Item.of(Material.ARROW, "§aVoltar ao painel")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new BotMenu(getPlayer(), arena).handle();
                }));
    }

    private String on(boolean value) {
        return value ? "§aon" : "§coff";
    }

    /** Sem ".0" pendurado nos ajustes inteiros, sem perder as casas do strafe. */
    private String format(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;

        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
