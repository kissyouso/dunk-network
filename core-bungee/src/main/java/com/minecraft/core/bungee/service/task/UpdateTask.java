package com.minecraft.core.bungee.service.task;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.bungee.event.list.update.UpdateEvent;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.Util;

import java.util.Arrays;
import java.util.List;

public class UpdateTask implements Runnable {

    private final List<String> customMessages = Arrays.asList(
            "§fQuer fazer parte da equipe §6Rede Style§f? Envie sua aplicação em §astaff." + Constant.SERVER_DOMAIN,
            "§fDeixe o servidor do seu jeito! Use §a/prefs §fpara ajustar chat, convites e muito mais.",
            "§fFique por dentro de tudo: novidades, eventos e sorteios em §amidia." + Constant.SERVER_DOMAIN,
            "§fApoie o servidor e libere VIPs, tags e medalhas em §a" + Constant.SERVER_STORE,
            "§fEnjoou da sua skin? Use §a/skin <nick> §fe troque na hora, §a§lDE GRAÇA§f!",
            "§fTreine seu PvP na §2Academy§f: §eTimers§f, §cLava§f, §6Damage §fe §bTexture §fte esperam!",
            "§fJogar sozinho é chato! Chame seus amigos com §a/party §fe joguem juntos.",
            "§fVocê está bem? O §aCVV §foferece apoio emocional gratuito, ligue §a188§f. Você não está sozinho!"
    );

    private final int messageSize = customMessages.size();
    private final int delay = 120;
    private int cleanupCounter = 0;
    int time = delay, index = 0;

    @Override
    public void run() {
        new UpdateEvent().call();
        cleanupCounter++;
        int cleanupInterval = 10;
        if (cleanupCounter >= cleanupInterval) {
            cleanupCounter = 0;
            Core.getServerData().cleanupDeadServers();
        }

        time--;

        if (time == 0) {
            Core.getAccountController()
                    .filter(account -> !account.inServer(ServerType.AUTH))
                    .forEach(account -> account.send(Constant.SERVER_PREFIX + Util.color(customMessages.get(index))));

            time = delay;
            index++;

            if (index >= messageSize)
                index = 0;
        }
    }
}
