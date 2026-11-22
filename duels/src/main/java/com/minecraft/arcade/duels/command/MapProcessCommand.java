package com.minecraft.arcade.duels.command;

import com.minecraft.arcade.duels.Duels;
import com.minecraft.arcade.duels.arcade.Arcade;
import com.minecraft.core.Core;
import com.minecraft.core.arcade.ArcadeHolder;
import org.bukkit.command.CommandSender;

/**
 * Recarregar as arenas de Duels sem reiniciar o servidor (/duconfig salvar usa isto).
 */
public class MapProcessCommand {

    public static void reloadArcade(CommandSender sender, String mode) {
        try {
            ArcadeHolder holder = Duels.getManager().getArcade().getGame(mode);

            if (!(holder instanceof Arcade)) {
                sender.sendMessage("§cNão foi possível encontrar o modo §e" + mode + " §ccarregado para recarregar.");
                return;
            }

            Arcade arcade = (Arcade) holder;

            sender.sendMessage("§aRecarregando §e" + arcade.getName() + " §a(mapas, arenas)...");

            arcade.unload();
            arcade.getMaps().clear();

            if (arcade.load()) {
                Core.getArcadeData().save(arcade);

                sender.sendMessage("§aModo §e" + arcade.getName() + " §arecarregado! §7Mapas: §e" + arcade.getMaps().size() + " §7Arenas: §e" + arcade.getRooms().size());
            } else
                sender.sendMessage("§cFalha ao recarregar §e" + arcade.getName() + "§c. Veja o console.");
        } catch (Exception exception) {
            sender.sendMessage("§cErro ao recarregar o modo: " + exception.getMessage());
            Core.getLogger().warning("Erro ao recarregar Duels: " + exception.getMessage());
            exception.printStackTrace();
        }
    }
}
