package com.minecraft.event.user;

import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.bukkit.user.UserModel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.UUID;

@Getter
@Setter
public class User extends UserModel {

    /* Modo construção (/evento build) */
    private boolean build;

    public User(UUID id) {
        super(id, Join.PLAYER);
    }

    @Override
    public void handle() {
    }

    public Player getPlayer() {
        return getAccount().player();
    }

    public static User fetch(UUID id) {
        UserModel model = UserModel.of(id);

        return model instanceof User ? (User) model : null;
    }
}
