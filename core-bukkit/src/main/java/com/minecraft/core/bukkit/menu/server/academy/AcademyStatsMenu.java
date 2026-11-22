package com.minecraft.core.bukkit.menu.server.academy;

import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.academy.AcademyMetadata;
import com.minecraft.core.account.context.objects.academy.DamageSettings;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * Estatísticas da Academy: recordes de Timers, Lava e Damage.
 */
public class AcademyStatsMenu extends Menu {

    private final Account target;

    public AcademyStatsMenu(Player player, Account target, Menu last) {
        super(player, "Estatísticas: Academy", last, 3);

        this.target = target;
    }

    private static String millis(long millis) {
        if (millis <= 0) return "Nenhum";

        return String.format("%02d:%02d.%03d", millis / 60000L, (millis / 1000L) % 60L, millis % 1000L);
    }

    private static String seconds(int seconds) {
        if (seconds <= 0) return "Nenhum";

        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public void handle() {
        clear();

        AcademyMetadata academy = target.getAcademy();

        addItem(11, Item.of(Material.WATCH, "§eTimers",
                "§7Refill: §f" + millis(academy.getTimerRefill()),
                "§7Cogumelo: §f" + millis(academy.getTimerMushroom()),
                "§7Cocoa: §f" + millis(academy.getTimerCocoa())));

        addItem(13, Item.of(Material.LAVA_BUCKET, "§eLava",
                "§aFácil: §f" + academy.getLavaEasy(),
                "§eMédio: §f" + academy.getLavaMedium(),
                "§cDifícil: §f" + academy.getLavaHard(),
                "§4Extremo: §f" + academy.getLavaExtreme()));

        List<String> damage = new ArrayList<>();

        for (DamageSettings.Level level : DamageSettings.Level.values())
            damage.add(level.getColor() + level.getName() + ": §f" + seconds(academy.getDamageRecord(level)));

        addItem(15, Item.of(Material.SKULL_ITEM, 1, "§eDamage", damage));

        if (isReturnable())
            addBackButton(22);

        display();
    }
}
