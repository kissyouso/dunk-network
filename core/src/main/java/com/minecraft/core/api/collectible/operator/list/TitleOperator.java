package com.minecraft.core.api.collectible.operator.list;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.collectible.Collectible;
import com.minecraft.core.api.collectible.CollectibleCategory;
import com.minecraft.core.api.collectible.operator.CollectibleOperator;
import com.minecraft.core.api.collectible.type.title.TitleCollectible;
import com.minecraft.core.api.collectible.type.title.TitleDisplay;
import com.minecraft.core.api.collectible.type.title.TitleStat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mantém o título acima da cabeça do jogador: segue o jogador a cada tick, troca os quadros
 * da animação e recarrega a estatística (se houver) a cada 30 segundos.
 */
public class TitleOperator extends CollectibleOperator {

    private static final int STATS_REFRESH_TICKS = 30 * 20;

    private final Map<TitleStat, Long> stats = new ConcurrentHashMap<>();

    private BukkitTask task;
    private TitleDisplay display;
    private int ticks;

    public TitleOperator(Player host, TitleCollectible title) {
        super(host, CollectibleCategory.TITLE);

        handle(title);
    }

    @Override
    public void handle(Collectible collectible) {
        TitleCollectible title = (TitleCollectible) collectible;

        cancel();

        Plugin plugin = JavaPlugin.getProvidingPlugin(TitleOperator.class);
        Player host = getHost();

        this.display = new TitleDisplay(host);
        this.ticks = 0;

        loadStats(plugin, title);

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!host.isOnline()) {
                cancel();
                return;
            }

            ticks++;

            if (title.getStat() != null && ticks % STATS_REFRESH_TICKS == 0)
                loadStats(plugin, title);

            display.tick(title.render(stats, ticks / Math.max(1, title.getTicksPerFrame())));
        }, 1L, 1L);

        setCollectible(collectible);
    }

    private void loadStats(Plugin plugin, TitleCollectible title) {
        TitleStat stat = title.getStat();

        if (stat == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Account account = Core.getAccountController().of(getHost().getUniqueId());

            if (account != null)
                stats.put(stat, stat.load(account));
        });
    }

    @Override
    public void cancel() {
        if (task != null)
            task.cancel();

        if (display != null)
            display.destroy();

        task = null;
        display = null;
    }
}
