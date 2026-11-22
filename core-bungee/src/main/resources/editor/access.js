import { api } from "./api.js";
import { rankDisplay, renderMc } from "./colors.js";
import { notify, ranksByWeight } from "./state.js";
import { button, el, field, formatDate, modal, toast } from "./ui.js";

const NICK = /^[A-Za-z0-9_]{1,16}$/;
const MOTD_FIRST_LINE = "&e&l✦ &6&lREDE STYLE &e&l✦";

/* Estado da página: dados do servidor, rascunho editável e flags de carregamento */
const page = { data: null, draft: null, loading: false, saving: false, failed: null };

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function dirty() {
  return page.data && page.draft && JSON.stringify(page.data.access) !== JSON.stringify(page.draft);
}

function rankOptions(select, selected, includeAll) {
  if (includeAll) {
    select.appendChild(el("option", { value: "", text: "Todos" }));
  }
  for (const rank of ranksByWeight()) {
    if (includeAll && rank.id === "member") {
      continue;
    }
    select.appendChild(el("option", { value: rank.id, text: rank.name + " ou superior" }));
  }
  select.value = selected || "";
  return select;
}

function rankName(id) {
  const rank = ranksByWeight().find((candidate) => candidate.id === id);
  return rank ? rank.name : id;
}

function load() {
  if (page.loading) {
    return;
  }
  page.loading = true;
  page.failed = null;
  api.access().then((data) => {
    page.data = data;
    page.draft = clone(data.access);
  }).catch((error) => {
    page.failed = error.friendly || error.message;
  }).finally(() => {
    page.loading = false;
    notify();
  });
}

async function saveAll() {
  if (page.saving || !dirty()) {
    return;
  }
  page.saving = true;
  notify();
  try {
    page.data = await api.saveAccess(page.draft);
    page.draft = clone(page.data.access);
    toast("Configurações de acesso salvas. Valem na rede em até 5 segundos.", "ok");
  } catch (error) {
    toast("Não foi possível salvar: " + (error.friendly || error.message), "bad");
  } finally {
    page.saving = false;
    notify();
  }
}

/* ---------------- Peças reutilizáveis ---------------- */

function switchButton(on, labels, onToggle) {
  return el("button", {
    type: "button",
    class: "toggle " + (on ? "on" : "off"),
    text: on ? labels[0] : labels[1],
    onclick: onToggle,
  });
}

/* Lista de nicks com chips e campo para adicionar */
function nickList(list, onChange, emptyText) {
  const chips = el("div", { class: "chips" });
  for (const nick of list) {
    chips.appendChild(el("span", { class: "chip" }, el("span", { class: "mono", text: nick }),
      el("button", { type: "button", title: "Remover", text: "×", onclick: () => onChange(list.filter((other) => other !== nick)) })));
  }
  if (!list.length) {
    chips.appendChild(el("span", { class: "muted", text: emptyText }));
  }
  const input = el("input", { type: "text", placeholder: "Nick do jogador", maxlength: "16" });
  const add = () => {
    const nick = input.value.trim();
    if (!NICK.test(nick)) {
      toast("Nick inválido (letras, números e _; até 16).", "bad");
      return;
    }
    if (list.some((other) => other.toLowerCase() === nick.toLowerCase())) {
      toast(nick + " já está na lista.", "bad");
      return;
    }
    onChange(list.concat([nick]));
  };
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      add();
    }
  });
  return el("div", { class: "stack" }, chips, el("div", { class: "row" }, el("div", { class: "grow" }, input), button("Adicionar", add, "small")));
}

/* Campo de texto com prévia em cores do Minecraft, atualizada enquanto digita */
function textWithPreview(label, value, options, onInput, previewOf) {
  const input = options.multiline
    ? el("textarea", { maxlength: String(options.max), rows: "6" })
    : el("input", { type: "text", maxlength: String(options.max) });
  input.value = value;
  /* .stack é flex: a prévia vira bloco e as linhas não quebram em caixas separadas */
  const preview = el("div", { class: "stack" });
  const draw = () => preview.replaceChildren(renderMc(previewOf(input.value)));
  input.addEventListener("input", () => {
    onInput(input.value);
    draw();
    refreshHead();
  });
  draw();
  return el("div", { class: "stack" }, field(label, input, options.hint), el("div", { class: "label", text: "Prévia" }), preview);
}

/* Atualiza só os botões do topo (sem redesenhar a página e perder o foco do campo) */
function refreshHead() {
  const head = document.getElementById("access-head-actions");
  if (head) {
    head.replaceChildren(...headActions());
  }
}

function headActions() {
  const actions = [];
  if (dirty()) {
    actions.push(el("span", { class: "badge warn", text: "alterações não salvas" }));
    actions.push(button("Desfazer", () => {
      page.draft = clone(page.data.access);
      notify();
    }, "ghost"));
  }
  const save = button(page.saving ? "Salvando…" : "Salvar acesso", saveAll, "save");
  save.disabled = page.saving || !dirty();
  actions.push(save);
  return actions;
}

/* ---------------- Cartões ---------------- */

function maintenanceCard() {
  const draft = page.draft;
  const rank = rankOptions(el("select"), draft.maintenanceRank, false);
  rank.addEventListener("change", () => {
    draft.maintenanceRank = rank.value;
    refreshHead();
  });
  return el("div", { class: "card" },
    el("div", { class: "row" },
      el("h2", { text: "Manutenção da rede" }),
      el("div", { class: "grow" }),
      switchButton(draft.maintenance, ["ATIVA", "DESLIGADA"], () => {
        draft.maintenance = !draft.maintenance;
        notify();
      })),
    el("p", { class: "section-note", text: draft.maintenance
      ? "Ninguém entra na rede, exceto o cargo abaixo e os jogadores liberados. O MOTD mostra a linha de manutenção."
      : "Com a manutenção ativa, só entra o cargo abaixo e os jogadores liberados. Quem já está online não é expulso." }),
    el("div", { class: "stack" },
      field("Quem entra mesmo em manutenção", rank, "Desenvolvedores sempre entram."),
      el("div", { class: "label", text: "Jogadores liberados" }),
      nickList(draft.maintenancePlayers, (list) => {
        draft.maintenancePlayers = list;
        notify();
      }, "Nenhum jogador liberado."),
      textWithPreview("Linha do MOTD", draft.maintenanceMotd, { max: 80, hint: "Segunda linha da lista de servidores. Use & para cores." },
        (value) => {
          draft.maintenanceMotd = value;
        }, (value) => MOTD_FIRST_LINE + "\n" + value),
      textWithPreview("Mensagem para quem tenta entrar", draft.maintenanceMessage, { max: 600, multiline: true, hint: "Aparece na tela de desconexão. Use & para cores." },
        (value) => {
          draft.maintenanceMessage = value;
        }, (value) => value)));
}

function slotsCard() {
  const draft = page.draft;
  const slots = el("input", { type: "number", min: "0", max: "100000", step: "1" });
  slots.value = String(draft.slots);
  slots.addEventListener("input", () => {
    const value = Number(slots.value);
    draft.slots = Number.isInteger(value) && value >= 0 ? value : 0;
    refreshHead();
  });
  const rank = rankOptions(el("select"), draft.slotsRank, false);
  rank.addEventListener("change", () => {
    draft.slotsRank = rank.value;
    refreshHead();
  });
  const online = page.data.online;
  return el("div", { class: "card" },
    el("div", { class: "row" },
      el("h2", { text: "Slots da rede" }),
      el("div", { class: "grow" }),
      el("span", { class: "badge", text: online + " online agora" })),
    el("p", { class: "section-note", text: "Quando a rede chega no limite, só entra quem tem o cargo escolhido (a equipe sempre entra). A lista de servidores mostra esse limite." }),
    el("div", { class: "stack" },
      el("div", { class: "grid-2" },
        field("Limite de jogadores", slots, "0 = sem limite."),
        field("Quem entra com a rede cheia", rank)),
      textWithPreview("Mensagem de rede cheia", draft.fullMessage, { max: 600, multiline: true, hint: "Aparece na tela de desconexão. Use & para cores." },
        (value) => {
          draft.fullMessage = value;
        }, (value) => value)));
}

function serversCard() {
  const draft = page.draft;
  const rows = page.data.serverTypes.map((type) => {
    const select = rankOptions(el("select"), draft.serverRanks[type.id] || "", true);
    select.addEventListener("change", () => {
      if (select.value) {
        draft.serverRanks[type.id] = select.value;
      } else {
        delete draft.serverRanks[type.id];
      }
      refreshHead();
    });
    return el("tr", null,
      el("td", null, el("strong", { text: type.name }), type.lobby ? el("span", { class: "muted", text: " · lobby" }) : null),
      el("td", { class: "mono muted", text: type.id.toLowerCase() }),
      el("td", null, select));
  });
  return el("div", { class: "card" },
    el("h2", null, "Servidores exclusivos", el("span", { class: "hint", text: "cargo mínimo para entrar em cada tipo de servidor" })),
    el("p", { class: "section-note", text: "Quem não tem o cargo recebe um aviso e não troca de servidor. Desenvolvedores sempre entram." }),
    el("div", { class: "table-wrap" }, el("table", null,
      el("thead", null, el("tr", null, el("th", { text: "Servidor" }), el("th", { text: "Tipo" }), el("th", { text: "Quem pode entrar" }))),
      el("tbody", null, rows))));
}

/* ---------------- Whitelist por servidor (salva na hora) ---------------- */

async function saveServer(server, changes) {
  const payload = {
    identifier: server.identifier,
    enabled: server.whitelist.enabled,
    ranks: server.whitelist.ranks,
    players: server.whitelist.players,
    ...changes,
  };
  try {
    page.data = await api.saveServerWhitelist(payload);
    toast("Whitelist de " + server.name + " salva.", "ok");
    return true;
  } catch (error) {
    toast("Não foi possível salvar: " + (error.friendly || error.message), "bad");
    return false;
  } finally {
    notify();
  }
}

async function editServer(server) {
  let ranks = server.whitelist.ranks.slice();
  let players = server.whitelist.players.slice();
  const rankBox = el("div", { class: "chips" });
  for (const rank of ranksByWeight()) {
    const box = el("input", { type: "checkbox", checked: ranks.includes(rank.id) });
    box.addEventListener("change", () => {
      ranks = box.checked ? ranks.concat([rank.id]) : ranks.filter((id) => id !== rank.id);
    });
    rankBox.appendChild(el("label", { class: "chip" }, box, renderMc(rankDisplay(rank), true)));
  }
  const nicks = el("textarea", { rows: "6", placeholder: "Um nick por linha" });
  nicks.value = players.join("\n");
  const result = await modal("Whitelist de " + server.name, el("div", { class: "stack" },
    el("p", { class: "section-note", text: "Com a whitelist ativa, só entram os cargos marcados (exatamente esses cargos), os nicks da lista e a equipe." }),
    el("div", { class: "label", text: "Cargos liberados" }), rankBox,
    field("Jogadores liberados", nicks)), [
    { label: "Cancelar", className: "ghost" },
    {
      label: "Salvar",
      className: "primary",
      run: () => {
        players = nicks.value.split(/\s+/).map((nick) => nick.trim()).filter(Boolean);
        const invalid = players.find((nick) => !NICK.test(nick));
        if (invalid) {
          toast("Nick inválido: " + invalid, "bad");
          return false;
        }
        return { ranks, players };
      },
    },
  ]);
  if (result) {
    await saveServer(server, result);
  }
}

function whitelistCard() {
  const servers = page.data.servers.slice().sort((a, b) => a.name.localeCompare(b.name));
  const rows = servers.map((server) => {
    const list = server.whitelist;
    const allowed = list.ranks.map(rankName).concat(list.players.length ? [list.players.length + " jogador(es)"] : []);
    return el("tr", null,
      el("td", null,
        el("strong", { text: server.name }),
        el("div", { class: "muted", text: server.typeName })),
      el("td", null, server.online
        ? el("span", { class: "badge ok", text: server.players + "/" + server.maxPlayers })
        : el("span", { class: "badge bad", text: "offline" })),
      el("td", null, switchButton(list.enabled, ["ATIVA", "DESLIGADA"], () => saveServer(server, { enabled: !list.enabled }))),
      el("td", { class: "muted", text: allowed.length ? allowed.join(", ") : "só a equipe" }),
      el("td", null, button("Editar", () => editServer(server), "small ghost")));
  });
  return el("div", { class: "card" },
    el("div", { class: "row" },
      el("h2", null, "Whitelist por servidor", el("span", { class: "hint", text: "salva na hora, igual ao /whitelist" })),
      el("div", { class: "grow" }),
      button("Atualizar lista", () => {
        load();
      }, "small ghost")),
    servers.length
      ? el("div", { class: "table-wrap" }, el("table", null,
        el("thead", null, el("tr", null, el("th", { text: "Servidor" }), el("th", { text: "Jogadores" }), el("th", { text: "Whitelist" }), el("th", { text: "Quem entra" }), el("th"))),
        el("tbody", null, rows)))
      : el("div", { class: "empty", text: "Nenhum servidor registrado agora." }));
}

/* ---------------- Página ---------------- */

export function accessPage() {
  if (!page.data && !page.loading && !page.failed) {
    load();
  }
  if (!page.data) {
    return el("div", null,
      el("div", { class: "page-head" }, el("h1", { text: "Acesso" })),
      el("div", { class: "card empty", text: page.failed ? "Não foi possível carregar: " + page.failed : "Carregando configurações de acesso…" }),
      page.failed ? el("div", { class: "row end" }, button("Tentar de novo", () => {
        page.failed = null;
        load();
      })) : null);
  }
  const access = page.data.access;
  return el("div", null,
    el("div", { class: "page-head" },
      el("h1", { text: "Acesso" }),
      access.maintenance ? el("span", { class: "badge warn", text: "rede em manutenção" }) : el("span", { class: "badge ok", text: "rede aberta" }),
      el("div", { class: "grow" }),
      el("div", { class: "row", id: "access-head-actions" }, headActions())),
    access.updatedBy ? el("p", { class: "section-note", text: "Última alteração por " + access.updatedBy + " em " + formatDate(access.updatedAt) + "." }) : null,
    el("div", { class: "grid-2" }, maintenanceCard(), slotsCard()),
    el("div", { class: "spacer-8" }),
    serversCard(),
    whitelistCard());
}

/* Para o aviso ao sair da página com alterações */
export function accessDirty() {
  return Boolean(dirty());
}
