import { medalChatLine, medalDisplay, medalListEntry, rankDisplay, renderMc } from "./colors.js";
import { expiryBadge } from "./nodes.js";
import { medalsByWeight, notify, ranksByWeight, state, tagsByWeight } from "./state.js";
import { button, el, field } from "./ui.js";

const SAMPLE_PLAYER = "kwqz";
const SAMPLE_MESSAGE = "Olá!";
const preview = { tag: "vip" };

function grantingNode(rank, medal) {
  return rank.nodes.find((node) => node.key === medal.permission);
}

function previewCard(medal) {
  const box = el("div");
  const select = el("select", null, el("option", { value: "", text: "Sem tag" }));
  for (const tag of tagsByWeight().filter((candidate) => candidate.role !== "COLORED")) {
    select.appendChild(el("option", { value: tag.id, text: tag.name }));
  }
  select.value = state.tags.has(preview.tag) ? preview.tag : "";
  const render = () => {
    const tag = state.tags.get(select.value) || null;
    box.replaceChildren(el("div", { class: "stack" },
      field("Chat", renderMc(medalChatLine(medal, tag, SAMPLE_PLAYER, SAMPLE_MESSAGE))),
      field("/medalha", renderMc(medalListEntry(medal)))));
  };
  select.addEventListener("change", () => {
    preview.tag = select.value;
    render();
  });
  render();
  return el("div", { class: "card" },
    el("h2", { text: "Prévia" }),
    field("Tag na prévia", select),
    el("div", { class: "spacer-8" }),
    box);
}

/* Dar a medalha a um rank = adicionar a permissão medal.<nome> nas permissões extras do rank */
function ranksCard(medal, nav) {
  const list = el("div", { class: "stack" });
  const giving = ranksByWeight().filter((rank) => grantingNode(rank, medal));
  for (const rank of giving) {
    const node = grantingNode(rank, medal);
    list.appendChild(el("div", { class: "row" },
      el("button", { type: "button", class: "btn ghost small", onclick: () => nav.go("ranks", rank.id) }, renderMc(rankDisplay(rank), true)),
      expiryBadge(node),
      el("div", { class: "grow" }),
      button("Remover", () => {
        rank.nodes = rank.nodes.filter((candidate) => candidate !== node);
        notify();
      }, "small danger")));
  }
  if (!giving.length) {
    list.appendChild(el("p", { class: "muted", text: "Nenhum rank dá esta medalha." }));
  }
  const select = el("select", null, el("option", { value: "", text: "Escolha um rank" }));
  for (const rank of ranksByWeight()) {
    if (!giving.includes(rank)) {
      select.appendChild(el("option", { value: rank.id, text: rank.name }));
    }
  }
  const add = button("Dar ao rank", () => {
    const rank = state.working.ranks.get(select.value);
    if (rank) {
      rank.nodes = rank.nodes.concat([{ key: medal.permission, expiresAt: null, addedAt: null }]);
      notify();
    }
  }, "primary");
  return el("div", { class: "card" },
    el("h2", null, "Ranks", el("span", { class: "hint", text: "adiciona ou remove " + medal.permission + " no rank" })),
    list,
    el("p", { class: "section-note", text: "A equipe (Trial ou acima) tem todas as medalhas." }),
    el("div", { class: "spacer-8" }),
    el("div", { class: "row" }, el("div", { class: "grow" }, select), add));
}

export function medalsPage(view, nav) {
  const medals = medalsByWeight();
  const selected = view.id && state.medals.get(view.id) ? state.medals.get(view.id) : null;
  const list = el("div", { class: "list" });
  for (const medal of medals) {
    list.appendChild(el("button", {
      type: "button",
      class: "list-item" + (selected && selected.id === medal.id ? " active" : ""),
      onclick: () => nav.go("medals", medal.id),
    },
    renderMc(medalDisplay(medal), true),
    el("span", { text: medal.name })));
  }
  if (!medals.length) {
    list.appendChild(el("div", { class: "empty", text: "Nenhuma medalha cadastrada." }));
  }
  const left = el("div", { class: "card" }, el("h2", { text: "Medalhas" }), list);
  const right = selected
    ? el("div", null,
      el("div", { class: "card" },
        el("div", { class: "row" },
          renderMc(medalDisplay(selected), true),
          el("h2", { text: selected.name }),
          el("span", { class: "badge mono", text: selected.id })),
        el("p", { class: "section-note", text: "Liberada para quem tem a permissão " + selected.permission + " (direta ou por rank) ou a tem na coleção. Nome, símbolo e cor ficam no código (Medal)." })),
      previewCard(selected),
      ranksCard(selected, nav))
    : el("div", { class: "card empty" }, "Escolha uma medalha à esquerda.");
  return el("div", null,
    el("div", { class: "page-head" }, el("h1", { text: "Medalhas" }), el("span", { class: "muted", text: medals.length + " medalhas" })),
    el("div", { class: "split" }, left, right));
}
