import { api } from "./api.js";
import { chatLine, rankDisplay, renderMc } from "./colors.js";
import { nodeTable } from "./nodes.js";
import { isChanged, ranksByWeight, state } from "./state.js";
import { button, clear, el, field, toast } from "./ui.js";

const ROLES = { NONE: "Membro", VIP: "VIP", SPECIAL: "Especial", STAFF: "Equipe" };

function detailsCard(rank) {
  const tag = rank.tag ? state.tags.get(rank.tag) : null;
  return el("div", { class: "card" },
    el("div", { class: "row" },
      renderMc(rankDisplay(rank), true),
      el("span", { class: "badge mono", text: rank.id }),
      el("span", { class: "badge accent", text: ROLES[rank.role] || rank.role }),
      rank.id === state.defaultRank ? el("span", { class: "badge ok", text: "padrão" }) : null,
      el("div", { class: "grow" }),
      el("span", { class: "muted", text: "peso " + rank.weight })),
    el("div", { class: "spacer-8" }),
    tag ? field("Tag do rank", renderMc(chatLine(tag, "kwqz", "Olá!"))) : el("p", { class: "muted", text: "Este rank não tem tag própria." }),
    el("p", { class: "section-note", text: "Nome, cor, peso e tag ficam no código (RankType). Aqui você edita as permissões extras do rank." }));
}

function baseCard(rank) {
  const list = el("div", { class: "readonly-list" });
  for (const key of rank.base) {
    list.appendChild(el("span", { class: "badge mono", text: key }));
  }
  if (!rank.base.length) {
    list.appendChild(el("span", { class: "muted", text: "Nenhuma." }));
  }
  return el("div", { class: "card" },
    el("h2", null, "Permissões fixas", el("span", { class: "hint", text: "definidas no código, não podem ser removidas aqui" })),
    list);
}

function membersCard(rank, nav) {
  const body = el("div", null, el("p", { class: "section-note", text: "Jogadores com este rank ativo (até 200)." }));
  const load = button("Carregar membros", async () => {
    load.disabled = true;
    try {
      const result = await api.members(rank.id);
      clear(body);
      body.appendChild(el("p", { class: "section-note", text: result.total + " jogador(es)" }));
      const list = el("div", { class: "chips" });
      for (const member of result.members) {
        list.appendChild(el("button", { type: "button", class: "chip", onclick: () => nav.go("users", member.id) }, member.username || member.id));
      }
      body.appendChild(list);
    } catch (error) {
      toast(error.friendly || error.message, "bad");
      load.disabled = false;
    }
  }, "small");
  return el("div", { class: "card" }, el("div", { class: "row" }, el("h2", { text: "Membros" }), el("div", { class: "grow" }), load), body);
}

export function ranksPage(view, nav) {
  const ranks = ranksByWeight();
  const selected = view.id && state.working.ranks.get(view.id) ? state.working.ranks.get(view.id) : null;
  const list = el("div", { class: "list" });
  for (const rank of ranks) {
    list.appendChild(el("button", {
      type: "button",
      class: "list-item" + (selected && selected.id === rank.id ? " active" : "") + (isChanged("rank", rank.id) ? " changed" : ""),
      onclick: () => nav.go("ranks", rank.id),
    },
    renderMc(rankDisplay(rank), true),
    el("span", { class: "meta", text: (ROLES[rank.role] || rank.role) + " · " + rank.nodes.length + " extra" })));
  }
  const left = el("div", { class: "card" }, el("h2", { text: "Ranks" }), list);
  let right;
  if (!selected) {
    right = el("div", { class: "card empty" }, "Escolha um rank à esquerda.");
  } else {
    right = el("div", null,
      detailsCard(selected),
      baseCard(selected),
      nodeTable({
        key: "rank:" + selected.id,
        title: "Permissões extras",
        hint: "valem para todos com este rank, em todos os servidores",
        nodes: () => selected.nodes,
        update: (nodes) => {
          selected.nodes = nodes;
        },
        emptyText: "Nenhuma permissão extra.",
      }),
      membersCard(selected, nav));
  }
  return el("div", null,
    el("div", { class: "page-head" }, el("h1", { text: "Ranks" }), el("span", { class: "muted", text: ranks.length + " ranks" })),
    el("div", { class: "split" }, left, right));
}
