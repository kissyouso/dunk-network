package com.minecraft.core.member.list.thebridge;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.member.Member;
import com.minecraft.core.member.context.menu.MenuMetadata;
import com.minecraft.core.member.context.stats.ArcadeStats;
import com.minecraft.core.server.type.ServerType;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TheBridgeMember extends Member {

    private long bestTime;
    private int attempts, completions;
    private String selectedBlock = "WOOL:14";
    private String islandSkin = "PADRAO";
    private String animation = "FOGUETE";

    public TheBridgeMember(UUID id, String name) {
        super(id, name);

        this.addMenu(
                new MenuMetadata(ArcadeCategory.THE_BRIDGE_SOLO, Constant.DUELS_THE_BRIDGE_BASE64)
        );

        ArcadeCategory.of(ServerType.THE_BRIDGE).forEach(arcade -> this.addMultipleStats(new ArcadeStats(arcade)));
    }

    public String getSelectedBlock() {
        return selectedBlock == null || !selectedBlock.contains(":") ? "WOOL:14" : selectedBlock;
    }

    public String getIslandSkin() {
        return islandSkin == null || islandSkin.trim().isEmpty() ? "PADRAO" : islandSkin;
    }

    public String getAnimation() {
        return animation == null || animation.trim().isEmpty() ? "FOGUETE" : animation;
    }

    public void addAttempt() {
        attempts++;
        savePractice();
    }

    public void addCompletion(long time) {
        completions++;
        if (time > 0 && (bestTime <= 0 || time < bestTime)) bestTime = time;
        savePractice();
    }

    public void savePractice() {
        save("practice");
    }

    public String getBestTimeFormat() {
        return formatTime(bestTime);
    }

    public static String formatTime(long millis) {
        millis = Math.max(0, millis);
        return String.format("%d:%02d.%02d", millis / 60000L, (millis / 1000L) % 60L, (millis % 1000L) / 10L);
    }

    @Override
    public void save(String... fields) {
        for (String field : fields)
            Core.getTheBridgeData().update(this, field);
    }
}
