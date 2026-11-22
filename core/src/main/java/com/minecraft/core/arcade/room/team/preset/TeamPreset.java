package com.minecraft.core.arcade.room.team.preset;

import com.minecraft.core.arcade.room.Room;
import com.minecraft.core.arcade.room.team.Team;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;

@Getter
@Setter
public class TeamPreset extends Team {

    private Location bedLocation, portalLocation;

    private int score;

    public TeamPreset(TeamPresetType type, Room arena, Location base, int maxPlayers) {
        super(type.getName(), type.getId(), type.getColor(), type.getRgb(), arena, base, maxPlayers);

        String name = type.name().toLowerCase();

        if (arena.hasLocation(name + "_bed"))
            this.bedLocation = arena.getLocation(name + "_bed");

        if (arena.hasLocation(name + "_portal"))
            this.portalLocation = arena.getLocation(name + "_portal");
    }

    public boolean hasBed() {
        if (bedLocation == null) return false;

        World world = bedLocation.getWorld();

        if (world == null) return false;

        int x = bedLocation.getBlockX(), y = bedLocation.getBlockY(), z = bedLocation.getBlockZ();

        /* Caminho rápido: o caso normal é a cama estar exatamente no ponto salvo. */
        if (world.getBlockAt(x, y, z).getType().name().contains("BED")) return true;

        /* Algumas camas foram salvas com um bloco de deslocamento (staff em cima
         * da cama ou no canto). Procura a cama na vizinhança antes de considerar
         * o time sem cama. */
        for (int offsetX = -1; offsetX <= 1; offsetX++)
            for (int offsetY = -1; offsetY <= 1; offsetY++)
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++)
                    if (world.getBlockAt(x + offsetX, y + offsetY, z + offsetZ).getType().name().contains("BED"))
                        return true;

        return false;
    }

    public boolean hasPortal() {
        return portalLocation != null && portalLocation.getWorld() != null && getArena().getWorld() != null
                && portalLocation.getWorld().getName().equalsIgnoreCase(getArena().getWorld().getName());
    }

    public boolean isYourBed(Location location) {
        if (location == null || location.getWorld() == null || bedLocation == null) return false;

        World world = bedLocation.getWorld();

        if (world == null || !location.getWorld().getName().equalsIgnoreCase(world.getName())) return false;

        /* O bloco quebrado precisa ser uma cama. Não exigimos que o bloco salvo
         * ainda seja cama: assim uma cama recolocada/ajustada no mapa continua
         * sendo reconhecida como a cama registrada do time. */
        if (!location.getBlock().getType().name().contains("BED")) return false;

        int dx = Math.abs(location.getBlockX() - bedLocation.getBlockX());
        int dy = Math.abs(location.getBlockY() - bedLocation.getBlockY());
        int dz = Math.abs(location.getBlockZ() - bedLocation.getBlockZ());

        /* A cama ocupa dois blocos; toleramos o local salvo um bloco acima
         * (jogador em cima da cama) ou deslocado em um bloco. */
        return dx <= 1 && dy <= 1 && dz <= 1;
    }

    public String getTypeName() {
        return TeamPresetType.of(getColor()).name();
    }

    public String getBrackets() {
        return getColor() + "[" + getId() + "]";
    }

    public String getBracketsName() {
        return getColor() + "[" + getId() + "] §f" + getName();
    }

    public String getColoredId() {
        return getColor() + getId();
    }

    public String getDisplay() {
        return getColoredId() + " " + getName();
    }

    public String getBedName(int aliveUsers) {
        return hasBed() ? "§a✓" : aliveUsers > 0 ? "§a" + aliveUsers : "§c✘";
    }
}
