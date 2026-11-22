import { chatLine, renderMc, tagDisplay } from "./colors.js";
import { rankName, state, tagsByWeight } from "./state.js";
import { el, field } from "./ui.js";

const SAMPLE_PLAYER = "kwqz";
const SAMPLE_MESSAGE = "Olá!";

const ROLES = {
  NONE: "Membro",
  COLORED: "Colorida",
  VIP: "VIP",
  SPECIAL: "Especial",
  ONLY_THESE: "Exclusiva",
  STAFF: "Equipe",
};

/* Explica quem pode usar a tag (mesmas regras do Account.hasTag) */
function rule(tag) {
  switch (tag.role) {
    case "COLORED":
      return "Uso interno (cores de times e espectadores). Não pode ser escolhida.";
    case "SPECIAL":
      return "Liberada para quem tem a permissão " + tag.permission + " (dê pelo jogador ou por um rank). Admins têm todas.";
    case "VIP":
      return "Liberada para quem tem o rank " + rankName(tag.source) + " ou acima, e para a equipe.";
    case "STAFF":
      return "Liberada para o rank " + rankName(tag.source) + " (exatamente) e para admins.";
    case "ONLY_THESE":
      return "Liberada só para contas específicas definidas no código.";
    default:
      return "Liberada para todos.";
  }
}

function previews(tag) {
  return el("div", { class: "stack" },
    field("Chat", renderMc(chatLine(tag, SAMPLE_PLAYER, SAMPLE_MESSAGE))),
    field("/tag", renderMc(tagDisplay(tag))),
    field("Nome acima da cabeça / tab", renderMc(tag.prefix + SAMPLE_PLAYER)));
}

export function tagsPage(view, nav) {
  const tags = tagsByWeight();
  const selected = view.id && state.tags.get(view.id) ? state.tags.get(view.id) : null;
  const list = el("div", { class: "list" });
  for (const tag of tags) {
    list.appendChild(el("button", {
      type: "button",
      class: "list-item" + (selected && selected.id === tag.id ? " active" : ""),
      onclick: () => nav.go("tags", tag.id),
    },
    renderMc(tag.prefix ? tag.prefix.trim() : tagDisplay(tag), true),
    el("span", { class: "meta", text: ROLES[tag.role] || tag.role })));
  }
  const left = el("div", { class: "card" }, el("h2", { text: "Tags" }), list);
  const right = selected
    ? el("div", null,
      el("div", { class: "card" },
        el("div", { class: "row" },
          renderMc(tagDisplay(selected), true),
          el("span", { class: "badge mono", text: selected.id }),
          el("span", { class: "badge accent", text: ROLES[selected.role] || selected.role })),
        el("div", { class: "spacer-8" }),
        field("Prefixo", el("span", { class: "mono", text: selected.prefix || "(sem prefixo)" })),
        field("Permissão", el("span", { class: "mono", text: selected.permission })),
        el("p", { class: "section-note", text: rule(selected) }),
        el("p", { class: "section-note", text: "Nome, cor e prefixo ficam no código (Tag). Para dar uma tag especial a alguém, use a página do jogador." })),
      el("div", { class: "card" }, el("h2", { text: "Prévia" }), previews(selected)))
    : el("div", { class: "card empty" }, "Escolha uma tag à esquerda.");
  return el("div", null,
    el("div", { class: "page-head" }, el("h1", { text: "Tags" }), el("span", { class: "muted", text: tags.length + " tags" })),
    el("div", { class: "split" }, left, right));
}
