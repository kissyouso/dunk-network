package com.minecraft.core.api.collectible.operator;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.collectible.Collectible;
import com.minecraft.core.api.collectible.CollectibleCategory;
import com.minecraft.core.api.collectible.nms.module.companion.CompanionEntities;
import com.minecraft.core.api.collectible.operator.list.*;
import com.minecraft.core.api.collectible.type.artifact.ArtifactCollectible;
import com.minecraft.core.api.collectible.type.balloon.BalloonCollectible;
import com.minecraft.core.api.collectible.type.banner.BannerCollectible;
import com.minecraft.core.api.collectible.type.companion.CompanionCollectible;
import com.minecraft.core.api.collectible.type.emotion.EmotionCollectible;
import com.minecraft.core.api.collectible.type.hat.HatCollectible;
import com.minecraft.core.api.collectible.type.particle.ParticleCollectible;
import com.minecraft.core.api.collectible.type.title.TitleCollectible;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

@Getter
@Setter
public abstract class CollectibleOperator {

    @Getter
    private static final Set<CollectibleOperator> list = new CopyOnWriteArraySet<>();

    private final Player host;
    private final CollectibleCategory category;

    private Collectible collectible;
    private List<CollectibleCategory> categoriesWithConflict;

    public CollectibleOperator(Player host, CollectibleCategory category) {
        this.host = host;
        this.category = category;

        list.add(this);
    }

    public abstract void handle(Collectible collectible);

    public abstract void cancel();

    public void setCollectible(Collectible collectible) {
        this.collectible = collectible;

        Account account = Core.getAccountController().of(host.getUniqueId());

        if (account != null)
            account.activateCollectible(collectible);
    }

    public static void load(Player host) {
        Account account = Core.getAccountController().of(host.getUniqueId());

        if (account != null) {
            List<String> list = account.getActiveCollectibles();

            for (String id : new java.util.ArrayList<>(list)) {
                Collectible collectible = Core.getCollectibleController().of(id);

                if (collectible == null) continue;

                /* Títulos de categorias removidas (PvP/Eventos) não existem mais: limpa do jogador */
                if (collectible instanceof TitleCollectible && ((TitleCollectible) collectible).getGroup() == null) {
                    account.removeActiveCollectible(collectible);
                    continue;
                }

                start(host, collectible);
            }
        }
    }

    /**
     * Liga de novo só os colecionáveis ativos de uma categoria (ex.: o título ao entrar no lobby).
     */
    public static void load(Player host, CollectibleCategory category) {
        Account account = Core.getAccountController().of(host.getUniqueId());

        if (account == null || has(host, category)) return;

        for (String id : new java.util.ArrayList<>(account.getActiveCollectibles())) {
            Collectible collectible = Core.getCollectibleController().of(id);

            if (collectible != null && collectible.getCategory() == category) {
                if (collectible instanceof TitleCollectible) {
                    TitleCollectible title = (TitleCollectible) collectible;

                    /* Categoria removida (PvP/Eventos) ou requisito perdido: tira o título */
                    if (title.getGroup() == null || !title.canUse(account)) {
                        account.removeActiveCollectible(collectible);
                        continue;
                    }
                }

                start(host, collectible);
                return;
            }
        }
    }

    public static void start(Player host, Collectible collectible) {
        switch (collectible.getCategory()) {
            case HAT: {
                new HatOperator(host, (HatCollectible) collectible);
                break;
            }
            case EMOTION: {
                new EmotionOperator(host, (EmotionCollectible) collectible);
                break;
            }
            case BALLOON: {
                new BalloonOperator(host, (BalloonCollectible) collectible);
                break;
            }
            case COMPANION: {
                new CompanionOperator(host, (CompanionCollectible) collectible);
                break;
            }
            case ARTIFACT: {
                new ArtifactOperator(host, (ArtifactCollectible) collectible);
                break;
            }
            case BANNER: {
                new BannerOperator(host, (BannerCollectible) collectible);
                break;
            }
            case PARTICLE: {
                new ParticleOperator(host, (ParticleCollectible) collectible);
                break;
            }
            case TITLE: {
                new TitleOperator(host, (TitleCollectible) collectible);
                break;
            }

            case CAPES: {
                new com.minecraft.core.api.collectible.operator.list.CapeOperator(host, (com.minecraft.core.api.collectible.type.cape.CapeCollectible) collectible);
                break;
            }
        }
    }

    public static List<CollectibleOperator> list(Player host) {
        return list.stream()
                .filter(operator -> operator.getHost().equals(host))
                .collect(Collectors.toList());
    }

    public static CollectibleOperator of(Player host, CollectibleCategory category) {
        return list(host).stream()
                .filter(operator -> operator.getCategory().equals(category))
                .findFirst()
                .orElse(null);
    }

    public static boolean has(Player host, CollectibleCategory category) {
        return of(host, category) != null;
    }

    public static boolean has(Player host, Collectible collectible) {
        return list(host).stream().anyMatch(operator -> operator.getCategory().equals(collectible.getCategory())
                && operator.getCollectible() != null && operator.getCollectible().equals(collectible));
    }

    public static void disable(Player host) {
        Account account = Core.getAccountController().of(host.getUniqueId());

        list(host).forEach(operator -> {
            operator.cancel();

            account.removeActiveCollectible(operator.getCollectible());

            list.remove(operator);
        });

        CompanionEntities.removeByOwner(host);
    }

    public static void enable(Player host) {
        Account account = Core.getAccountController().of(host.getUniqueId());
        if (account != null) {
            unload(host);
            
            load(host);
        }
    }

    public static void unload(Player host) {
        list(host).forEach(operator -> {
            operator.cancel();

            list.remove(operator);
        });

        CompanionEntities.removeByOwner(host);
    }

    public static void unload(Player host, CollectibleCategory category) {
        CollectibleOperator operator = of(host, category);

        if (operator != null) {
            operator.cancel();

            Account account = Core.getAccountController().of(host.getUniqueId());

            if (account != null)
                account.removeActiveCollectible(operator.getCollectible());

            list.remove(operator);
        }
    }

    public static void remove(Player host, CollectibleCategory category) {
        list.removeIf(operator -> operator.getHost().equals(host) && operator.getCategory().equals(category));
    }

    public static void unloadConflicts(Player host, CollectibleCategory category) {
        list(host).stream().filter(operator -> operator.hasConflict(category)).forEach(operator -> unload(host, operator.getCategory()));
    }

    public boolean hasConflict(CollectibleCategory category) {
        return categoriesWithConflict != null && categoriesWithConflict.contains(category);
    }
}
