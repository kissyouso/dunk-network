import { api } from "./api.js";
import { medalDisplay, rankDisplay, renderMc, tagDisplay } from "./colors.js";
import { askExpiry, expiryBadge, expiryInputs, nodeTable, putNode, removeNode } from "./nodes.js";
import { MEDAL_PREFIX, TAG_PREFIX, activeRank, canUseMedal, canUseTag, isChanged, loadUser, medalsByWeight, notify, ranksByWeight, state, tagsByWeight } from "./state.js";
import { button, el, field, formatDate, modal, toast } from "./ui.js";

const loading = new Set();

function isTagNode(user, node) {
  return node.key.startsWith(TAG_PREFIX) && tagsByWeight().some((tag) => tag.permission === node.key);
}

function isMedalNode(node) {
  return node.key.startsWith(MEDAL_PREFIX) && medalsByWeight().some((medal) => medal.permission === node.key);
}

async function giveRank(user) {
  const rank = el("select", null);
  for (const candidate of ranksByWeight()) {
    if (candidate.id !== state.defaultRank) {
      rank.appendChild(el("option", { value: candidate.id, text: candidate.name }));
    }
  }
  const expiry = expiryInputs(null);
  const result = await modal("Dar rank a " + (user.username || user.id), el("div", { class: "stack" },
    field("Rank", rank), field("Duração", expiry.element)), [
    { label: "Cancelar", className: "ghost" },
    {
      label: "Dar rank",
      className: "primary",
      run: () => {
        try {
          return { rank: rank.value, expiresAt: expiry.read() };
        } catch (error) {
          toast(error.message, "bad");
          return false;
        }
      },
    },
  ]);
  if (result) {
    user.ranks = user.ranks.filter((entry) => entry.rank !== result.rank).concat([result]);
    notify();
  }
}

function ranksCard(user) {
  const active = activeRank(user);
  const entries = user.ranks.slice().sort((a, b) => (state.working.ranks.get(b.rank) || { weight: 0 }).weight - (state.working.ranks.get(a.rank) || { weight: 0 }).weight);
  const list = el("div", { class: "stack" });
  for (const entry of entries) {
    const rank = state.working.ranks.get(entry.rank);
    list.appendChild(el("div", { class: "row" },
      rank ? renderMc(rankDisplay(rank), true) : el("strong", { text: entry.rank }),
      rank && rank.id === active.id ? el("span", { class: "badge ok", text: "ativo" }) : null,
      expiryBadge(entry),
      entry.author ? el("span", { class: "muted", text: "por " + entry.author }) : null,
      el("div", { class: "grow" }),
      button("Remover", () => {
        user.ranks = user.ranks.filter((candidate) => candidate !== entry);
        notify();
      }, "small danger")));
  }
  if (!entries.length) {
    list.appendChild(el("p", { class: "muted", text: "Sem ranks: o jogador é Membro." }));
  }
  return el("div", { class: "card" },
    el("div", { class: "row" }, el("h2", { text: "Ranks" }), el("div", { class: "grow" }), button("+ Dar rank", () => giveRank(user), "small primary")),
    list,
    el("p", { class: "section-note", text: "O rank de maior peso que não expirou fica ativo. Os outros continuam somando permissões." }));
}

function tagsCard(user) {
  const selected = el("select", null, el("option", { value: "", text: "Automática (tag do rank ativo)" }));
  for (const tag of tagsByWeight().filter((candidate) => candidate.role !== "COLORED")) {
    selected.appendChild(el("option", { value: tag.id, text: tag.name + (canUseTag(user, tag) ? "" : " (não pode usar)") }));
  }
  selected.value = user.selectedTag || "";
  selected.addEventListener("change", () => {
    user.selectedTag = selected.value || null;
    notify();
  });
  const current = user.selectedTag ? state.tags.get(user.selectedTag) : null;
  const special = el("div", { class: "stack" });
  for (const tag of tagsByWeight().filter((candidate) => candidate.role === "SPECIAL")) {
    const node = user.nodes.find((candidate) => candidate.key === tag.permission);
    special.appendChild(el("div", { class: "row" },
      renderMc(tag.prefix.trim() || tagDisplay(tag), true),
      node ? expiryBadge(node) : el("span", { class: "muted", text: canUseTag(user, tag) ? "liberada por rank" : "não tem" }),
      el("div", { class: "grow" }),
      node
        ? button("Tirar", () => {
          user.nodes = removeNode(user.nodes, node);
          notify();
        }, "small danger")
        : button("Dar", async () => {
          const expiresAt = await askExpiry("Dar a tag " + tag.name, "Dar tag");
          if (expiresAt) {
            user.nodes = putNode(user.nodes, { key: tag.permission, expiresAt: expiresAt.value });
            notify();
          }
        }, "small")));
  }
  return el("div", { class: "card" },
    el("h2", { text: "Tags" }),
    field("Tag escolhida", selected),
    current && !canUseTag(user, current) ? el("span", { class: "badge warn", text: "o jogador não pode usar esta tag" }) : null,
    el("div", { class: "spacer-8" }),
    el("div", { class: "label", text: "Tags especiais" }),
    el("div", { class: "spacer-8" }),
    special);
}

function medalsCard(user) {
  const chosen = el("select", null, el("option", { value: "", text: "Nenhuma" }));
  for (const medal of medalsByWeight()) {
    chosen.appendChild(el("option", { value: medal.id, text: medal.symbol + " " + medal.name + (canUseMedal(user, medal) ? "" : " (não tem)") }));
  }
  chosen.value = user.selectedMedal || "";
  chosen.addEventListener("change", () => {
    user.selectedMedal = chosen.value || null;
    notify();
  });
  const current = user.selectedMedal ? state.medals.get(user.selectedMedal) : null;
  const owned = el("div", { class: "stack" });
  for (const medal of medalsByWeight()) {
    const inCollection = (user.medals || []).includes(medal.id);
    const node = user.nodes.find((candidate) => candidate.key === medal.permission);
    const usable = canUseMedal(user, medal);
    owned.appendChild(el("div", { class: "row" },
      renderMc(medalDisplay(medal), true),
      el("span", { text: medal.name }),
      inCollection ? el("span", { class: "badge", text: "coleção" }) : null,
      node ? expiryBadge(node) : null,
      !inCollection && !node && usable ? el("span", { class: "badge ok", text: "pelo rank" }) : null,
      !usable ? el("span", { class: "muted", text: "não tem" }) : null,
      el("div", { class: "grow" }),
      inCollection || node
        ? button("Tirar", () => {
          user.medals = (user.medals || []).filter((id) => id !== medal.id);
          if (node) {
            user.nodes = removeNode(user.nodes, node);
          }
          notify();
        }, "small danger")
        : button("Dar", async () => {
          const expiresAt = await askExpiry("Dar a medalha " + medal.name, "Dar medalha");
          if (!expiresAt) {
            return;
          }
          /* Permanente vai para a coleção; temporária vira a permissão medal.<nome> com validade */
          if (expiresAt.value === null) {
            user.medals = (user.medals || []).concat([medal.id]);
          } else {
            user.nodes = putNode(user.nodes, { key: medal.permission, expiresAt: expiresAt.value });
          }
          notify();
        }, "small")));
  }
  if (!medalsByWeight().length) {
    owned.appendChild(el("p", { class: "muted", text: "Nenhuma medalha cadastrada." }));
  }
  return el("div", { class: "card" },
    el("h2", { text: "Medalhas" }),
    field("Medalha escolhida", chosen, "A medalha só aparece no chat se o jogador puder usá-la."),
    current ? el("div", { class: "row" }, renderMc(medalDisplay(current), true),
      !canUseMedal(user, current) ? el("span", { class: "badge warn", text: "o jogador não tem esta medalha" }) : null) : null,
    el("div", { class: "spacer-8" }),
    el("div", { class: "label", text: "Medalhas do jogador" }),
    el("div", { class: "spacer-8" }),
    owned);
}

function profileCard(user) {
  return el("div", { class: "card" },
    el("div", { class: "user-head" },
      el("div", { class: "avatar", text: (user.username || "?").charAt(0).toUpperCase() }),
      el("div", null,
        el("div", { class: "row" },
          el("h2", { text: user.username || "Sem nome" }),
          user.premium ? el("span", { class: "badge ok", text: "original" }) : el("span", { class: "badge", text: "pirata" }),
          user.online ? el("span", { class: "badge ok", text: "online" }) : el("span", { class: "badge", text: "offline" })),
        el("div", { class: "muted mono", text: user.id }),
        el("div", { class: "muted", text: "Primeiro acesso " + formatDate(user.firstSeenAt) + " · último " + formatDate(user.lastSeenAt) }))));
}

function searchBar(nav) {
  const input = el("input", { type: "search", placeholder: "Nome do jogador ou UUID", maxlength: "36" });
  const run = async () => {
    const name = input.value.trim();
    if (!name) {
      return;
    }
    try {
      const user = await api.searchUser(name);
      loadUser(user);
      nav.go("users", user.id);
    } catch (error) {
      toast(error.friendly || error.message, "bad");
    }
  };
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      run();
    }
  });
  return el("div", { class: "card" }, el("div", { class: "row" }, el("div", { class: "grow" }, input), button("Buscar", run, "primary")));
}

function ensureLoaded(id) {
  if (state.users.has(id) || loading.has(id)) {
    return;
  }
  loading.add(id);
  api.user(id).then((user) => {
    loadUser(user);
    notify();
  }).catch((error) => toast(error.friendly || error.message, "bad")).finally(() => loading.delete(id));
}

export function usersPage(view, nav) {
  const recent = el("div", { class: "list" });
  for (const [id, entry] of state.users) {
    recent.appendChild(el("button", {
      type: "button",
      class: "list-item" + (view.id === id ? " active" : "") + (isChanged("user", id) ? " changed" : ""),
      onclick: () => nav.go("users", id),
    }, el("span", { text: entry.working.username || id })));
  }
  if (!state.users.size) {
    recent.appendChild(el("div", { class: "empty", text: "Busque um jogador para editar." }));
  }
  let right;
  if (!view.id) {
    right = el("div", { class: "card empty" }, "Busque um jogador pelo nome para ver e editar ranks, tags, medalhas e permissões.");
  } else if (!state.users.has(view.id)) {
    ensureLoaded(view.id);
    right = el("div", { class: "card empty" }, "Carregando jogador…");
  } else {
    const user = state.users.get(view.id).working;
    right = el("div", null,
      profileCard(user),
      ranksCard(user),
      tagsCard(user),
      medalsCard(user),
      nodeTable({
        key: "user:" + user.id,
        title: "Permissões do jogador",
        hint: "tags especiais e medalhas ficam nos quadros acima",
        nodes: () => user.nodes,
        update: (nodes) => {
          user.nodes = nodes;
        },
        exclude: (node) => isTagNode(user, node) || isMedalNode(node),
        emptyText: "Nenhuma permissão própria.",
      }));
  }
  return el("div", null,
    el("div", { class: "page-head" }, el("h1", { text: "Jogadores" })),
    searchBar(nav),
    el("div", { class: "spacer-8" }),
    el("div", { class: "split" }, el("div", { class: "card" }, el("h2", { text: "Abertos nesta sessão" }), recent), right));
}
