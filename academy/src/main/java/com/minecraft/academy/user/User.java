package com.minecraft.academy.user;

import com.minecraft.academy.arena.Arena;
import com.minecraft.academy.util.TimerMode;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.academy.AcademyMetadata;
import com.minecraft.core.account.context.objects.academy.DamageSettings;
import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.user.UserModel;
import com.minecraft.core.member.list.pvp.PvPMember;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
public class User extends UserModel {

    private Arena arena;
    private String pendingArena;

    private boolean playersHidden, build;

    /* Timers */
    private TimerMode timerMode;
    private long timerStart;

    /* Damage */
    private boolean training;
    private int trainingSeconds;

    /* Lava: momento em que começou o percurso atual */
    private long arenaJoinedAt;

    public User(UUID id) {
        super(id, Join.PLAYER);
    }

    @Override
    public void handle() {
    }

    public Player getPlayer() {
        return getAccount().player();
    }

    public AcademyMetadata getAcademy() {
        return getAccount().getAcademy();
    }

    public DamageSettings getDamageSettings() {
        return getAcademy().getDamageSettings();
    }

    /* Coins da Academy = coins do PvP */
    public PvPMember getMember() {
        return Core.getMemberController().of(getAccount().getId(), PvPMember.class);
    }

    public int getCoins() {
        PvPMember member = getMember();

        return member != null ? member.getCoins() : 0;
    }

    public void addCoins(int coins) {
        PvPMember member = getMember();

        if (member != null && coins > 0)
            Core.getPlatform().runAsync(() -> member.addCoins(coins));
    }

    public void ensureSidebar(String title) {
        if (getSidebar() == null)
            setSidebar(new Sidebar(getPlayer(), title));
    }

    public void sendTo(Arena target) {
        if (!target.isEnabled() && arena != null) {
            getPlayer().sendMessage("§cEsta arena está desativada no momento.");
            return;
        }

        if (arena != null)
            arena.quit(this);

        target.join(this);
    }

    public boolean isTimerRunning() {
        return timerMode != null && timerStart != 0L;
    }

    public void resetTimer() {
        this.timerMode = null;
        this.timerStart = 0L;
    }

    public static User fetch(UUID id) {
        UserModel model = UserModel.of(id);

        return model instanceof User ? (User) model : null;
    }

    public static List<User> getUsers() {
        return UserModel.list(model -> model instanceof User).stream()
                .map(model -> (User) model)
                .collect(Collectors.toList());
    }
}
