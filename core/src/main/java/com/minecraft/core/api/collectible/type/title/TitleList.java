package com.minecraft.core.api.collectible.type.title;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.api.collectible.rarity.CollectibleRarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Todos os títulos. Registrados no CollectibleController por {@link #handle()}.
 */
public final class TitleList {

    private TitleList() {
    }

    public static void handle() {
        /* Gerais: animados e liberados por cargo */
        register(new AnimatedTitle("Beta Tester", CollectibleRarity.MYTHICAL, RankType.BETA,
                shine("BETA TESTER", "§1", "§9", "§f")));
        register(new AnimatedTitle("Style+", CollectibleRarity.MYTHICAL, RankType.DUNK_PLUS_1,
                cycle("STYLE+", "§2", "§6", "§b")));
        register(new AnimatedTitle("Style", CollectibleRarity.EPIC, RankType.DUNK,
                shine("STYLE", "§6", "§e", "§f")));
        register(new AnimatedTitle("VIP", CollectibleRarity.RARE, RankType.VIP,
                shine("VIP", "§2", "§a", "§f")));
        register(new AnimatedTitle("Partner", CollectibleRarity.MYTHICAL, RankType.PARTNER,
                shine("PARTNER", "§3", "§b", "§f")));
        register(new AnimatedTitle("Equipe", CollectibleRarity.MYTHICAL, RankType.HELPER,
                shine("EQUIPE", "§4", "§c", "§f")));
        register(new AnimatedTitle("Lendário", CollectibleRarity.EPIC, RankType.VIP,
                shine("LENDÁRIO", "§5", "§d", "§f")));
        register(new AnimatedTitle("Campeão", CollectibleRarity.EPIC, RankType.VIP,
                shine("CAMPEÃO", "§6", "§e", "§f")));
        register(new AnimatedTitle("Noob", CollectibleRarity.COMUM, null,
                shine("NOOB", "§8", "§7", "§f")));

        /* Estatísticas: um título por estatística, liberado para todos */
        for (TitleStat stat : TitleStat.values())
            register(new StatTitle(stat));
    }

    private static void register(TitleCollectible title) {
        Core.getCollectibleController().save(title);
    }

    public static List<TitleCollectible> list(TitleCategory category) {
        List<TitleCollectible> titles = new ArrayList<>();

        Core.getCollectibleController().list(com.minecraft.core.api.collectible.CollectibleCategory.TITLE).forEach(collectible -> {
            if (collectible instanceof TitleCollectible && ((TitleCollectible) collectible).getGroup() == category)
                titles.add((TitleCollectible) collectible);
        });

        return titles;
    }

    /* ---------------- Animações ---------------- */

    /**
     * Onda de brilho contínua: uma faixa em degradê (base -> meio -> brilho -> meio -> base)
     * atravessa o texto da esquerda para a direita e recomeça logo depois, sem travar.
     */
    public static List<String> shine(String text, String base, String middle, String light) {
        String[] band = {middle, light, light, middle};
        int period = text.length() + band.length + 6;

        List<String> frames = new ArrayList<>();

        for (int frame = 0; frame < period; frame++) {
            StringBuilder builder = new StringBuilder();
            String last = null;

            for (int index = 0; index < text.length(); index++) {
                int offset = frame - index;
                String color = offset >= 0 && offset < band.length ? band[offset] : base;

                if (!color.equals(last)) {
                    builder.append(color).append("§l");
                    last = color;
                }

                builder.append(text.charAt(index));
            }

            frames.add(builder.toString());
        }

        return frames;
    }

    /**
     * Mesma onda, mas a cor base muda a cada passada (ex.: STYLE+ verde -> dourado -> ciano).
     */
    public static List<String> cycle(String text, String... colors) {
        List<String> frames = new ArrayList<>();

        for (String color : colors)
            frames.addAll(shine(text, color, color, "§f"));

        return frames;
    }

    /* ---------------- Tipos ---------------- */

    public static class AnimatedTitle extends TitleCollectible {

        private final RankType rank;
        private final List<String> animation;

        public AnimatedTitle(String name, CollectibleRarity rarity, RankType rank, List<String> animation) {
            super(name, TitleCategory.GERAIS, rarity);

            this.rank = rank;
            this.animation = animation;
        }

        @Override
        public String render(Map<TitleStat, Long> stats, int frame) {
            return animation.get(Math.floorMod(frame, animation.size()));
        }

        @Override
        public int getFrames() {
            return animation.size();
        }

        @Override
        public boolean canUse(Account account) {
            return rank == null || account.hasRank(rank);
        }

        @Override
        public String getRequirement() {
            return rank == null ? null : "Exclusivo para " + rank.getColoredName() + "§7 ou superior.";
        }
    }

    public static class StatTitle extends TitleCollectible {

        private final TitleStat stat;

        public StatTitle(TitleStat stat) {
            super(stat.getLabel(), stat.getCategory(), CollectibleRarity.COMUM);

            this.stat = stat;
        }

        @Override
        public String render(Map<TitleStat, Long> stats, int frame) {
            /* Título antigo de categoria removida pode vir com stat nulo */
            if (stat == null) return "";

            return "§e" + stat.getLabel() + ": §6" + stat.format(stats.getOrDefault(stat, 0L));
        }

        @Override
        public TitleStat getStat() {
            return stat;
        }
    }
}
