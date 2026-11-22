package com.minecraft.arcade.bedwars.user.context;

import com.minecraft.core.member.list.bedwars.objects.ability.Ability;
import com.minecraft.core.member.list.bedwars.objects.ability.enums.AbilityType;
import com.minecraft.arcade.bedwars.user.context.objects.tracker.UserTracker;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.reconnect.Reconnect;
import com.minecraft.core.member.list.bedwars.objects.enums.item.BedWarsItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class UserContext {

    private UserTracker tracker = new UserTracker();

    private BedWarsItem pickaxeItem, axeItem, armorItem, swordItem;
    private Reconnect reconnect;

    private int xpGain, kills, deaths, finalKill, bedDestruction;
    
    // Sistema de moedas
    private int coins = 0;
    
    // Sistema de habilidades
    private Set<Ability> abilities = new HashSet<>();

    private boolean purchasedShears;

    /* rocketTime: até quando o dano de queda do Estilingue é ignorado */
    private long milkTime = -1L, protectionTime = -1L, rocketTime = -1L;
    
    private ItemStack permanentSword;
    
    // Rastreamento de expiração de habilidades
    private boolean noFallWasActive = false; // Para rastrear se NO_FALL estava ativa no último check

    public boolean hasReconnect() {
        return reconnect != null;
    }

    public void downGradePickaxe(Player player) {
        if (pickaxeItem == null || pickaxeItem.equals(BedWarsItem.TOOLS_WOOD_PICKAXE)) return;

        BedWarsItem previousPick = BedWarsItem.of(item -> item.getStack().getType().name().endsWith("_PICKAXE")
                && item.ordinal() == (pickaxeItem.ordinal() - 1));

        giveTool(player, previousPick);

        this.pickaxeItem = previousPick;
    }

    public void downGradeAxe(Player player) {
        if (axeItem == null || axeItem.equals(BedWarsItem.TOOLS_WOOD_AXE)) return;

        BedWarsItem previousAxe = BedWarsItem.of(item -> item.getStack().getType().name().endsWith("_AXE")
                && item.ordinal() == (axeItem.ordinal() - 1));

        giveTool(player, previousAxe);

        this.axeItem = previousAxe;
    }
    
    /**
     * Coloca a picareta/machado no slot onde já está a ferramenta do mesmo tipo (qualquer nível).
     */
    public static void giveTool(Player player, BedWarsItem tool) {
        String suffix = tool.isToolPickaxe() ? "_PICKAXE" : "_AXE";
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = Item.fromStack(tool.getStack()).unbreakable();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack content = inventory.getItem(slot);

            if (content != null && content.getType().name().endsWith(suffix)) {
                inventory.setItem(slot, stack);
                return;
            }
        }

        inventory.addItem(stack);
    }

    /**
     * Entrega a espada comprada no slot principal, removendo qualquer outra espada
     * do inventário para não acumular níveis nem deixar a madeira para trás.
     */
    public static void giveSword(Player player, ItemStack sword) {
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack content = inventory.getItem(slot);

            if (content != null && content.getType().name().endsWith("_SWORD"))
                inventory.setItem(slot, null);
        }

        inventory.setItem(0, sword);
        player.updateInventory();
    }

    public boolean hasPermanentSword() {
        return permanentSword != null;
    }
    
    // Métodos de moedas
    public void addCoins(int amount) {
        this.coins += amount;
    }
    
    public void removeCoins(int amount) {
        this.coins -= amount;
    }
    
    public boolean hasCoins(int amount) {
        return coins >= amount;
    }
    
    // Métodos de habilidades
    public boolean hasAbility(AbilityType type) {
        return abilities.stream().anyMatch(ability -> ability.getType() == type);
    }
    
    public void addAbility(Ability ability) {
        abilities.add(ability);
    }
    
    public Ability getAbility(AbilityType type) {
        return abilities.stream()
                .filter(ability -> ability.getType() == type)
                .findFirst()
                .orElse(null);
    }
}
