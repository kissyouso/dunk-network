package com.minecraft.lobby.halloween;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.assignment.Assignment;
import com.minecraft.core.account.context.objects.permission.Permission;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.account.context.objects.tag.Tag;
import com.minecraft.lobby.Lobby;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evento de Halloween: doces (cabeças) escondidos no lobby.
 * Ao encontrar todos os doces necessários, o jogador conclui o evento.
 * Os doces ficam em plugins/Lobby/halloween.yml e são configurados com /halloween.
 */
public final class Halloween {

    private static final List<Candy> candies = new ArrayList<>();

    private static File file;
    private static YamlConfiguration config;

    private static int required = 10;
    private static boolean enabled = true;

    private Halloween() {
    }

    public static void load() {
        file = new File(Lobby.getInstance().getDataFolder(), "halloween.yml");

        if (!file.exists()) {
            file.getParentFile().mkdirs();

            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        config = YamlConfiguration.loadConfiguration(file);

        enabled = config.getBoolean("enabled", true);
        required = config.getInt("required", 10);

        candies.clear();

        ConfigurationSection section = config.getConfigurationSection("candies");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection candy = section.getConfigurationSection(key);

                candies.add(new Candy(Integer.parseInt(key), candy.getString("world"), candy.getInt("x"), candy.getInt("y"), candy.getInt("z")));
            }
        }
    }

    public static void save() {
        config.set("enabled", enabled);
        config.set("required", required);
        config.set("candies", null);

        for (Candy candy : candies) {
            String path = "candies." + candy.getId();

            config.set(path + ".world", candy.getWorld());
            config.set(path + ".x", candy.getX());
            config.set(path + ".y", candy.getY());
            config.set(path + ".z", candy.getZ());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Candy> getCandies() {
        return Collections.unmodifiableList(candies);
    }

    public static int getRequired() {
        return Math.max(1, required);
    }

    public static void setRequired(int amount) {
        required = Math.max(1, amount);
        save();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static Candy of(Block block) {
        return candies.stream().filter(candy -> candy.is(block)).findFirst().orElse(null);
    }

    public static Candy of(int id) {
        return candies.stream().filter(candy -> candy.getId() == id).findFirst().orElse(null);
    }

    public static Candy add(Block block) {
        int id = candies.stream().mapToInt(Candy::getId).max().orElse(0) + 1;

        Candy candy = new Candy(id, block.getWorld().getName(), block.getX(), block.getY(), block.getZ());

        candies.add(candy);
        save();

        return candy;
    }

    public static void remove(Candy candy) {
        candies.remove(candy);
        save();
    }

    /* Identificador salvo na conta (os doces de cada tipo de lobby são independentes) */
    public static String key(Candy candy) {
        return Core.getServerType().name() + ":" + candy.getId();
    }

    /* Quantos doces deste lobby o jogador já encontrou */
    public static int found(Account account) {
        String prefix = Core.getServerType().name() + ":";

        return (int) account.getHalloweenCandies().stream().filter(key -> key.startsWith(prefix)).count();
    }

    public static boolean canManage(Account account) {
        return account.hasRank(RankType.ADMIN) || Constant.DEVELOPERS_IDS.contains(account.getId());
    }

    /**
     * Marca de conclusao do Halloween na conta. A tag SPOOKY saiu do catalogo de tags,
     * entao a chave continua aqui so para nao perder quem ja completou o evento.
     */
    public static final String SPOOKY_PERMISSION = "tag.spooky";

    public static void collect(Player player, Account account, Candy candy) {
        int total = getRequired();

        if (account.getHalloweenCandies().contains(key(candy))) {
            player.sendMessage("§cVocê já encontrou este doce! §7(" + Math.min(found(account), total) + "/" + total + ")");
            player.playSound(player.getLocation(), Sound.NOTE_BASS, 1F, 1F);
            return;
        }

        account.getHalloweenCandies().add(key(candy));

        int found = found(account);

        Location location = candy.toLocation().add(0.5, 0.5, 0.5);

        player.getWorld().playEffect(location, Effect.WITCH_MAGIC, 0);
        player.getWorld().playEffect(location, Effect.MOBSPAWNER_FLAMES, 0);

        if (found < total) {
            player.sendMessage("§6§lDOCE! §fVocê encontrou um doce! §7(" + found + "/" + total + ")");
            player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1F, 1F);

            Core.getPlatform().runAsync(() -> account.saveContext(account.getContext()));
            return;
        }

        /* Encontrou todos: marca a conclusao (a recompensa de tag saiu do catalogo) */
        if (!account.hasPermission(SPOOKY_PERMISSION))
            account.getPermissions().add(new Permission(SPOOKY_PERMISSION, Assignment.AUTO, Constant.DEFAULT_ID, -1L));

        Core.getPlatform().runAsync(() -> account.saveContext(account.getContext()));

        player.sendMessage("");
        player.sendMessage("§6§lDOCE! §fVocê encontrou todos os §6" + total + " doces§f!");
        player.sendMessage("");

        account.title("§6§lHALLOWEEN", "§fTodos os doces encontrados!");
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1F, 1F);

        Bukkit.getOnlinePlayers().forEach(online -> {
            if (!online.equals(player))
                online.sendMessage("§6§lHALLOWEEN §8» §f" + account.getNickname() + " encontrou todos os §6" + total + " doces§f!");
        });
    }

    @Getter
    @AllArgsConstructor
    public static class Candy {

        private final int id;
        private final String world;
        private final int x, y, z;

        public boolean is(Block block) {
            return block.getX() == x && block.getY() == y && block.getZ() == z && block.getWorld().getName().equals(world);
        }

        public Location toLocation() {
            return new Location(Bukkit.getWorld(world), x, y, z);
        }
    }
}
