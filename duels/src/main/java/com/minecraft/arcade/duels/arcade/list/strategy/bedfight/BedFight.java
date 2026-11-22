package com.minecraft.arcade.duels.arcade.list.strategy.bedfight;

import com.minecraft.arcade.duels.arcade.Arcade;
import com.minecraft.arcade.duels.arcade.arena.Arena;
import com.minecraft.arcade.duels.arcade.objects.style.SidebarStyle;
import com.minecraft.arcade.duels.user.User;
import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.room.map.rollback.RollbackBlock;
import com.minecraft.core.arcade.room.phase.RoomPhase;
import com.minecraft.core.arcade.room.team.preset.TeamPreset;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.event.IgnoreEvent;
import com.minecraft.core.util.list.TimeUtil;
import com.minecraft.core.util.list.bukkit.BukkitUtil;
import com.minecraft.core.util.list.bukkit.ColorUtil;
import com.minecraft.core.util.list.serialization.Serialization;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

/**
 * Bed Fight: Bed Wars 1v1 sem geradores e sem loja.
 * <p>
 * - As bases ficam uma de frente para a outra; cada jogador tem a sua cama.
 * - A cama já começa protegida: uma camada de madeira em volta dela e, por fora, uma de pedra do fim.
 * - Kit fixo (no início e a cada renascimento): espada de madeira, lã do time, picareta e machado
 * (Eficiência I), tesoura e armadura de couro na cor do time.
 * - Dá para construir até 15 blocos acima do próprio spawn.
 * - Enquanto a cama existir o jogador renasce; sem cama, morreu = perdeu.
 * - Aos 8 minutos vem a Morte Súbita (as camas somem) e aos 10 a partida acaba empatada.
 */
@IgnoreEvent
public class BedFight extends Arcade {

    /* Blocos acima do spawn do jogador em que ainda dá para construir */
    public static final int HEIGHT_LIMIT = 15;

    /* Segundos de partida até a Morte Súbita e até o empate */
    public static final int SUDDEN_DEATH = 8 * 60, TIME_LIMIT = 10 * 60;

    private static final int WOOL_AMOUNT = 64;

    /* Vizinhos usados para montar a proteção (lados e em cima; nunca embaixo da cama) */
    private static final BlockFace[] COVER_FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP};

    public BedFight(String mapsDirectory, Integer minRooms, Integer maxRooms) {
        super(mapsDirectory, minRooms, maxRooms, ArcadeCategory.DUELS_BED_FIGHT);

        /* A madeira e a pedra do fim da defesa da cama vêm prontas no mapa:
         * sem elas na lista, o onBreak genérico trancava a quebra. */
        getBlocksAllowedToBreak().addAll(Arrays.asList(Material.WOOD, Material.ENDER_STONE));

        setStyle(SidebarStyle.BED);
    }

    /* ---------------- Kit ---------------- */

    @Override
    public void handleHotbar(Player player, Arena arena) {
        if (!arena.isValid(player)) {
            handleDefaultHotbar(player);
            return;
        }

        User user = (User) User.of(player.getUniqueId());
        TeamPreset team = user.getTeam();

        PlayerInventory inventory = player.getInventory();

        inventory.clear();
        inventory.setArmorContents(null);

        String kit = getKitBase64(user);

        if (kit != null)
            Serialization.sendInventoryToPlayerFromBase64(player, kit);
        else {
            inventory.setItem(0, Item.of(Material.WOOD_SWORD).unbreakable());
            inventory.setItem(1, new Item(Material.WOOL, WOOL_AMOUNT, ColorUtil.getIdByColor(team.getColor())));
            inventory.setItem(2, Item.of(Material.WOOD_PICKAXE).enchantment(Enchantment.DIG_SPEED, 1).unbreakable());
            inventory.setItem(3, Item.of(Material.WOOD_AXE).enchantment(Enchantment.DIG_SPEED, 1).unbreakable());
            inventory.setItem(4, Item.of(Material.SHEARS).unbreakable());
        }

        BukkitUtil.sendLeatherArmor(player, team.getRgb());

        player.updateInventory();
    }

    /* ---------------- Limite de altura ---------------- */

    @Override
    public double getBuildLimitY(Arena arena, User user) {
        TeamPreset team = user.getTeam();
        Location base = team != null ? team.getBase() : null;

        if (base == null)
            return super.getBuildLimitY(arena, user);

        /* Pode colocar até 15 blocos acima do bloco do spawn (inclusive) */
        return base.getBlockY() + HEIGHT_LIMIT + 1;
    }

    /* ---------------- Proteção das camas ---------------- */

    @Override
    public void handleProperties(Arena arena) {
        for (TeamPreset team : arena.getTeamList()) {
            Location bed = team.getBedLocation();

            if (bed == null || !bed.getBlock().getType().name().contains("BED")) {
                Core.getLogger().warning("[Bed Fight/" + arena.getIdentifier() + "] O time " + team.getName()
                        + " está sem cama no mapa " + arena.getMap().getName() + " (ponto " + team.getTypeName().toLowerCase() + "_bed).");
                continue;
            }

            buildDefense(arena, bedBlocks(bed.getBlock()));
        }
    }

    /**
     * Camada 1 (madeira) em volta da cama e camada 2 (pedra do fim) em volta da madeira.
     * Só preenche ar: se o mapa já tiver a proteção montada, nada muda.
     */
    private void buildDefense(Arena arena, Set<Block> bed) {
        Set<Block> wood = cover(bed, bed);

        Set<Block> occupied = new HashSet<>(bed);
        occupied.addAll(wood);

        Set<Block> endStone = cover(wood, occupied);

        place(arena, wood, Material.WOOD);
        place(arena, endStone, Material.ENDER_STONE);
    }

    private static Set<Block> cover(Set<Block> from, Set<Block> ignore) {
        Set<Block> result = new LinkedHashSet<>();

        for (Block block : from) {
            for (BlockFace face : COVER_FACES) {
                Block relative = block.getRelative(face);

                if (!ignore.contains(relative) && relative.getType() == Material.AIR)
                    result.add(relative);
            }
        }

        return result;
    }

    private static void place(Arena arena, Set<Block> blocks, Material material) {
        for (Block block : blocks) {
            block.setType(material);

            /* Registrado como colocado: os jogadores podem quebrar */
            arena.addRollBack(block, RollbackBlock.RollbackType.PLACE_BLOCK);
        }
    }

    /**
     * As duas metades da cama (o ponto do mapa pode estar em qualquer uma delas).
     */
    private static Set<Block> bedBlocks(Block block) {
        Set<Block> blocks = new LinkedHashSet<>();

        blocks.add(block);

        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block relative = block.getRelative(face);

            if (relative.getType() == Material.BED_BLOCK) {
                blocks.add(relative);
                break;
            }
        }

        return blocks;
    }

    /* ---------------- Quebrar cama ---------------- */

    @EventHandler(priority = EventPriority.HIGH)
    public void onBedBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (block.getType() != Material.BED_BLOCK || !isValid(player)) return;

        User user = (User) User.of(player.getUniqueId());
        Arena arena = user.getArena();
        TeamPreset team = user.getTeam();

        event.setCancelled(true);

        if (!arena.isPhase(RoomPhase.PLAYING)) return;

        if (team.isYourBed(block.getLocation())) {
            player.sendMessage("§cVocê não pode quebrar a sua própria cama.");
            return;
        }

        TeamPreset owner = arena.getTeamByBed(block.getLocation());

        if (owner == null) return;

        destroyBed(block);

        arena.send("",
                "§f§lCAMA DESTRUÍDA §f" + Constant.ARROW_SYMBOL + "§7 A " + owner.getColor() + "Cama do " + owner.getName()
                        + "§7 foi destruída por " + team.getColor() + player.getName() + "§7.",
                "");

        arena.sound(Sound.ENDERDRAGON_GROWL, 13, 1);

        owner.getPlayers().forEach(victim -> {
            Account account = Core.getAccountController().of(victim.getUniqueId());

            if (account != null)
                account.title("§c§lCAMA DESTRUÍDA!", "§fVocê não vai mais renascer!");
        });
    }

    /* Remove as duas metades sem física, para não soltar o item da cama */
    private static void destroyBed(Block block) {
        for (Block half : bedBlocks(block))
            half.setType(Material.AIR, false);
    }

    /* ---------------- Tempo: Morte Súbita e empate ---------------- */

    @Override
    public void timer(Arena arena) {
        if (!arena.isPhase(RoomPhase.PLAYING)) return;

        int time = arena.getFullTime();

        if (time == SUDDEN_DEATH - 60)
            arena.send("§c§lMORTE SÚBITA §7em §c1 minuto§7: as camas serão destruídas!");

        if (time == SUDDEN_DEATH) {
            for (TeamPreset team : arena.getTeamList()) {
                Location bed = team.getBedLocation();

                if (bed != null && bed.getBlock().getType() == Material.BED_BLOCK)
                    destroyBed(bed.getBlock());
            }

            arena.send("", "§c§lMORTE SÚBITA §f" + Constant.ARROW_SYMBOL + " §7Todas as camas foram destruídas. Quem morrer agora perde!", "");
            arena.sound(Sound.ENDERDRAGON_GROWL, 13, 1);
            arena.title("§c§lMORTE SÚBITA", "§fAs camas foram destruídas!");
        }

        if (time == TIME_LIMIT - 60)
            arena.send("§eA partida termina em §c1 minuto§e. Sem vencedor até lá, dá empate!");

        if (time >= TIME_LIMIT) {
            arena.send("", "§e§lTEMPO ESGOTADO §f" + Constant.ARROW_SYMBOL + " §7A partida terminou §fempatada§7.", "");
            arena.setPhase(RoomPhase.ENDING);
        }
    }

    /* ---------------- Scoreboard ---------------- */

    @Override
    public void handleTeamStyle(Arena arena, Sidebar sidebar) {
        super.handleTeamStyle(arena, sidebar);

        sidebar.addRow("bedfight_event", eventRow(arena));
        sidebar.blankRow();
    }

    @Override
    public void updateTeamStyle(Arena arena, Sidebar sidebar) {
        super.updateTeamStyle(arena, sidebar);

        if (sidebar != null)
            sidebar.updateRow("bedfight_event", eventRow(arena));
    }

    private static String eventRow(Arena arena) {
        int time = arena.getFullTime();

        if (time < SUDDEN_DEATH)
            return "Morte Súbita: §c" + TimeUtil.time(SUDDEN_DEATH - time);

        return "Fim: §c" + TimeUtil.time(Math.max(0, TIME_LIMIT - time));
    }
}
