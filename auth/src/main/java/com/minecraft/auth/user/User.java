package com.minecraft.auth.user;

import com.minecraft.auth.menu.CaptchaMenu;
import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.auth.state.AuthState;
import com.minecraft.core.account.context.objects.tag.Tag;
import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.manager.list.TagManager;
import com.minecraft.core.bukkit.user.UserModel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class User extends UserModel {

    /* Tempo total para concluir a verificação e o registro/login.
     * Clientes lentos costumam demorar para carregar o mundo do Auth. */
    public static final int TIMEOUT_SECONDS = 120;

    private boolean captcha = true, locked = true;

    private int totalAttempts = 3;
    private long startTime;

    public User(UUID id) {
        super(id, Join.PLAYER);

        this.startTime = System.currentTimeMillis();

        setTag(Tag.SPECTATOR);
    }

    @Override
    public void handle() {
        Account account = getAccount();

        Player player = account.player();

        System.out.println("[DEBUG] Player " + player.getName() + " entrou. AuthState: " + account.getAuthState() + " | Password: " + account.getPassword());

        // Configurar sidebar
        Sidebar sidebar = new Sidebar(player, Constant.SERVER_TITLE);

        sidebar.setAnimated(false);

        sidebar.clear();
        sidebar.blankRow();

        boolean pendent = account.isAuthState(AuthState.PENDENT);

        sidebar.addRow("info", "§7" + (pendent ? "Este é o início de sua" : "Bem-vindo de volta,"));
        sidebar.addRow("info2", pendent ? "§7jornada no " + Constant.SERVER_NAME + "." : "§f" + account.getName());

        sidebar.blankRow();
        sidebar.addRow("info3", "§a" + (pendent ? "Crie uma conta para" : "Faça login para ter"));
        sidebar.addRow("info4", "§a" + (pendent ? "jogar no servidor" : "acesso ao servidor") + ".");

        sidebar.blankRow();
        sidebar.addWebsiteRow();

        sidebar.display();
        TagManager.updateTag(account);

        setSidebar(sidebar);

        // Abrir menu de verificação
        Core.getPlatform().runSync(() -> new CaptchaMenu(account.player()).handle(), 10);

        account.send("§eResolva a verificação de segurança para acessar o servidor.",
                "§7Depois use §e/register (senha) (confirmar-senha)§7 ou §e/login (senha)§7.",
                "§7Você tem §e" + TIMEOUT_SECONDS + " segundos§7 para concluir.");
    }

    public long getEndTime() {
        return startTime + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
    }

    public void resetTime() {
        this.startTime = System.currentTimeMillis();
    }
}
