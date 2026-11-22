package com.minecraft.arcade.thebridge.user.factory;

import com.minecraft.arcade.thebridge.arcade.arena.TheBridgeArena;
import com.minecraft.arcade.thebridge.user.TheBridgeUser;
import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.member.list.thebridge.TheBridgeMember;

public class UserFactory {

    public static TheBridgeUser create(TheBridgeMember member, TheBridgeArena arena, Join join) {
        return new TheBridgeUser(member, arena, join);
    }
}
