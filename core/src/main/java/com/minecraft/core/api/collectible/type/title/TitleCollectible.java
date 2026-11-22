package com.minecraft.core.api.collectible.type.title;

import com.minecraft.core.account.Account;
import com.minecraft.core.api.collectible.Collectible;
import com.minecraft.core.api.collectible.CollectibleCategory;
import com.minecraft.core.api.collectible.rarity.CollectibleRarity;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * Título mostrado acima da cabeça nos lobbies.
 * Pode ser animado (quadros que se alternam) ou de estatística ("Vitórias no Bed Wars: 1.234").
 */
@Getter
public abstract class TitleCollectible extends Collectible {

    private final TitleCategory group;

    public TitleCollectible(String name, TitleCategory group, CollectibleRarity rarity) {
        super(name, CollectibleCategory.TITLE, rarity, Collections.emptyList(), 0L);

        this.group = group;
    }

    /**
     * Texto do título no quadro informado. stats = valores já carregados do jogador.
     */
    public abstract String render(Map<TitleStat, Long> stats, int frame);

    /* Quantos quadros a animação tem (1 = parado) e quantos ticks cada quadro dura */
    public int getFrames() {
        return 1;
    }

    public int getTicksPerFrame() {
        return 2;
    }

    /* Estatística que este título precisa (null = nenhuma) */
    public TitleStat getStat() {
        return null;
    }

    public boolean canUse(Account account) {
        return true;
    }

    /* Texto do requisito, mostrado no menu quando o jogador ainda não pode usar */
    public String getRequirement() {
        return null;
    }

    /* Compatibilidade com quem só quer o texto */
    public String getTitle() {
        return render(Collections.emptyMap(), 0);
    }
}
