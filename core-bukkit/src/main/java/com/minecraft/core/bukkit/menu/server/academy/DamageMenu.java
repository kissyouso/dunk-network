package com.minecraft.core.bukkit.menu.server.academy;

import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.academy.AcademyMetadata;
import com.minecraft.core.account.context.objects.academy.DamageSettings;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Menu "Configurar Damage": usado no lobby da Academy e no NPC CONFIGURAR do servidor de treinos.
 */
public class DamageMenu extends Menu {

    private final Account account;

    /* start: ação do botão "Iniciar treino" (null = sem botão); locked: impede mudanças durante o treino */
    private final Runnable start, changed;
    private final BooleanSupplier locked;

    public DamageMenu(Player player, Account account, Runnable start, BooleanSupplier locked, Runnable changed) {
        super(player, "Configurar Damage", 4);

        this.account = account;
        this.start = start;
        this.locked = locked;
        this.changed = changed;
    }

    private static String seconds(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public void handle() {
        clear();

        DamageSettings settings = account.getAcademy().getDamageSettings();

        if (start != null) {
            addItem(4, Item.of(Material.IRON_SWORD, "§aIniciar treino",
                            "§7Vá para o Damage com as",
                            "§7configurações abaixo.",
                            "",
                            "§eClique para jogar!")
                    .flags(ItemFlag.values())
                    .click(event -> {
                        close();
                        start.run();
                    }));
        }

        addItem(10, Item.of(Material.WATCH, "§aFrequência",
                        "§7Intervalo entre cada dano.",
                        "",
                        "§fAtual: §a" + settings.getFrequency().getDisplay(),
                        "",
                        "§eClique para alterar.")
                .click(event -> change(() -> settings.setFrequency(settings.getFrequency().next()))));

        DamageSettings.Level level = settings.getLevel();

        addItem(11, Item.of(Material.INK_SACK, level.getDyeColor(), "§aNível de dano",
                        "§7Quantidade de dano recebida.",
                        "",
                        "§fAtual: " + level.getColor() + level.getName(),
                        "§fDano: §c" + level.getHearts() + " ❤",
                        "",
                        "§eClique para alterar.")
                .click(event -> change(() -> settings.setLevel(settings.getLevel().next()))));

        addItem(12, Item.of(Material.IRON_CHESTPLATE, "§aArmadura",
                        "§7Armadura de ferro usada no treino.",
                        "",
                        "§fAtual: §a" + (settings.isArmor() ? "Com Armadura" : "Sem Armadura"),
                        "",
                        "§eClique para alternar.")
                .flags(ItemFlag.values())
                .click(event -> change(() -> settings.setArmor(!settings.isArmor()))));

        addItem(21, toggle(settings.isArmor() ? "Com Armadura" : "Sem Armadura", settings.isArmor())
                .click(event -> change(() -> settings.setArmor(!settings.isArmor()))));

        addItem(13, option(Material.WOOD_SPADE, 0, "Drops", "Pás caem ao seu redor durante o treino.", settings.isDrops())
                .click(event -> change(() -> settings.setDrops(!settings.isDrops()))));
        addItem(22, toggle("Drops", settings.isDrops())
                .click(event -> change(() -> settings.setDrops(!settings.isDrops()))));

        addItem(14, option(Material.SKULL_ITEM, 1, "Wither", "Aplica o efeito Wither durante o treino.", settings.isWither())
                .click(event -> change(() -> settings.setWither(!settings.isWither()))));
        addItem(23, toggle("Wither", settings.isWither())
                .click(event -> change(() -> settings.setWither(!settings.isWither()))));

        addItem(15, option(Material.CHEST, 0, "Inventário cheio", "Inventário completo de sopas no treino.", settings.isFullInventory())
                .click(event -> change(() -> settings.setFullInventory(!settings.isFullInventory()))));
        addItem(24, toggle("Inventário cheio", settings.isFullInventory())
                .click(event -> change(() -> settings.setFullInventory(!settings.isFullInventory()))));

        addItem(16, Item.of(Material.ITEM_FRAME, "§aRecraft",
                        "§7Tipo de recraft entregue no treino.",
                        "",
                        "§fAtual: §a" + settings.getRecraft().getName(),
                        "",
                        "§eClique para alterar.")
                .click(event -> change(() -> settings.setRecraft(settings.getRecraft().next()))));

        AcademyMetadata academy = account.getAcademy();
        List<String> records = new ArrayList<>();

        records.add("§7Maior tempo em cada nível.");
        records.add("");

        for (DamageSettings.Level value : DamageSettings.Level.values()) {
            int seconds = academy.getDamageRecord(value);
            records.add(value.getColor() + value.getName() + ": §f" + (seconds == 0 ? "Nenhum" : seconds(seconds)));
        }

        records.add("");
        records.add("§7Recordes valem apenas sem");
        records.add("§7armadura, drops e wither.");

        addItem(31, Item.of(Material.BOOK, "§aRecordes", records));

        display();
    }

    private Item option(Material material, int data, String name, String description, boolean enabled) {
        Item item = Item.of(material, data, (enabled ? "§a" : "§c") + name,
                        "§7" + description,
                        "",
                        "§fEstado: " + (enabled ? "§aAtivado" : "§cDesativado"),
                        "",
                        "§eClique para " + (enabled ? "desativar." : "ativar."))
                .flags(ItemFlag.values());

        if (enabled)
            item.enchantment(Enchantment.DURABILITY, 1);

        return item;
    }

    private Item toggle(String name, boolean enabled) {
        return Item.of(Material.INK_SACK, enabled ? 10 : 8, (enabled ? "§a" : "§c") + name,
                enabled ? "§eClique para desativar." : "§eClique para ativar.");
    }

    private void change(Runnable runnable) {
        if (locked != null && locked.getAsBoolean()) {
            sound(MenuSound.ERROR);
            getPlayer().sendMessage("§cVocê não pode alterar as configurações durante o treino.");
            return;
        }

        runnable.run();
        account.saveAcademy();

        sound(MenuSound.PAGINATED);

        if (changed != null)
            changed.run();

        handle();
    }
}
