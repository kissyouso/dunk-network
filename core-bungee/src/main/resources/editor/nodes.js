import { NODE_PATTERN, button, clear, el, field, formatDate, formatRemaining, modal, parseDuration, toLocalInput, toast } from "./ui.js";
import { MEDAL_PREFIX, TAG_PREFIX, notify, now, state } from "./state.js";

const memory = new Map();

/* Uma permissão é { key, expiresAt (null = permanente), addedAt } */
export function putNode(nodes, node) {
  const existing = nodes.find((candidate) => candidate.key === node.key);
  const next = nodes.filter((candidate) => candidate.key !== node.key);
  next.push({ key: node.key, expiresAt: node.expiresAt || null, addedAt: existing ? existing.addedAt : null });
  return next;
}

export function removeNode(nodes, node) {
  return nodes.filter((candidate) => candidate.key !== node.key);
}

export function validateKey(raw) {
  const key = String(raw || "").trim().toLowerCase();
  if (!NODE_PATTERN.test(key)) {
    throw new Error("Permissão inválida: use letras minúsculas, números e . _ - + *, até 128 caracteres");
  }
  return key;
}

export function expiryInputs(initial) {
  const mode = el("select", null,
    el("option", { value: "never", text: "Permanente" }),
    el("option", { value: "duration", text: "Por um tempo" }),
    el("option", { value: "date", text: "Até uma data" }));
  const duration = el("input", { type: "text", placeholder: "30d, 12h, 1w, 1mo", class: "mono" });
  const date = el("input", { type: "datetime-local" });
  const sync = () => {
    duration.hidden = mode.value !== "duration";
    date.hidden = mode.value !== "date";
  };
  if (initial) {
    mode.value = "date";
    date.value = toLocalInput(initial);
  }
  mode.addEventListener("change", sync);
  sync();
  return {
    element: el("div", { class: "stack" }, mode, duration, date),
    read() {
      if (mode.value === "never") {
        return null;
      }
      if (mode.value === "duration") {
        const millis = parseDuration(duration.value);
        if (!millis) {
          throw new Error("Duração inválida. Exemplos: 30d, 12h, 1w, 1mo, 1d12h");
        }
        return now() + millis;
      }
      const millis = new Date(date.value).getTime();
      if (!date.value || Number.isNaN(millis)) {
        throw new Error("Escolha a data de expiração");
      }
      if (millis <= now()) {
        throw new Error("A data de expiração já passou");
      }
      return millis;
    },
  };
}

export function expiryBadge(node) {
  if (!node.expiresAt) {
    return el("span", { class: "badge", text: "permanente" });
  }
  if (node.expiresAt <= now()) {
    return el("span", { class: "badge bad", text: "expirado" });
  }
  return el("span", { class: "badge warn", title: formatDate(node.expiresAt), text: formatRemaining(node.expiresAt - now()) });
}

export function askExpiry(title, confirmLabel) {
  const expiry = expiryInputs(null);
  return modal(title, field("Duração", expiry.element), [
    { label: "Cancelar", className: "ghost" },
    {
      label: confirmLabel,
      className: "primary",
      run: () => {
        try {
          return { value: expiry.read() };
        } catch (error) {
          toast(error.message, "bad");
          return false;
        }
      },
    },
  ]);
}

function suggestions() {
  const list = el("datalist", { id: "node-suggestions" });
  const keys = new Set(["*", state.editorPermission]);
  for (const rank of state.working.ranks.values()) {
    rank.base.forEach((key) => keys.add(key));
    rank.nodes.forEach((node) => keys.add(node.key));
  }
  for (const tag of state.tags.values()) {
    keys.add(tag.permission);
  }
  for (const medal of state.medals.values()) {
    keys.add(medal.permission);
  }
  for (const key of Array.from(keys).filter(Boolean).sort()) {
    list.appendChild(el("option", { value: key }));
  }
  return list;
}

export async function nodeDialog(title, node) {
  const key = el("input", { type: "text", class: "mono", value: node ? node.key : "", placeholder: "dunk.exemplo.permissao", list: "node-suggestions" });
  const expiry = expiryInputs(node ? node.expiresAt : null);
  const body = el("div", { class: "stack" },
    field("Permissão", key, "Tags especiais usam " + TAG_PREFIX + "<nome> e medalhas " + MEDAL_PREFIX + "<nome>."),
    field("Expiração", expiry.element),
    suggestions());
  return modal(title, body, [
    { label: "Cancelar", className: "ghost" },
    {
      label: node ? "Aplicar" : "Adicionar",
      className: "primary",
      run: () => {
        try {
          return { key: validateKey(key.value), expiresAt: expiry.read() };
        } catch (error) {
          toast(error.message, "bad");
          return false;
        }
      },
    },
  ]);
}

export function parseBulk(text) {
  const nodes = [];
  const lines = String(text || "").split(/\r?\n/).map((line) => line.trim()).filter((line) => line && !line.startsWith("#"));
  for (const line of lines) {
    const parts = line.split(/\s+/);
    const key = parts.shift();
    let expiresAt = null;
    if (parts.length) {
      const millis = parseDuration(parts.join(""));
      if (!millis) {
        throw new Error("Linha inválida: " + line);
      }
      expiresAt = now() + millis;
    }
    nodes.push({ key: validateKey(key), expiresAt });
  }
  return nodes;
}

export function nodeTable(options) {
  const card = el("div", { class: "card" });
  const memo = memory.get(options.key) || { search: "", filter: "all" };
  memory.set(options.key, memo);
  const search = el("input", { type: "search", placeholder: "Buscar permissão…", value: memo.search });
  const filter = el("select", null,
    el("option", { value: "all", text: "Todas" }),
    el("option", { value: "permanent", text: "Permanentes" }),
    el("option", { value: "temporary", text: "Temporárias" }));
  filter.value = memo.filter;
  const body = el("div");

  const update = (nodes) => {
    options.update(nodes);
    notify();
  };

  const add = async () => {
    const node = await nodeDialog("Adicionar permissão");
    if (node) {
      update(putNode(options.nodes(), node));
    }
  };

  const bulk = async () => {
    const text = el("textarea", { class: "mono", placeholder: "dunk.fly\ndunk.chat.color\ndunk.kit.vip 30d" });
    const result = await modal("Adicionar várias permissões", el("div", { class: "stack" },
      el("p", { class: "muted", text: "Uma por linha. Uma duração como 30d depois da permissão é opcional." }),
      text), [
      { label: "Cancelar", className: "ghost" },
      {
        label: "Adicionar todas",
        className: "primary",
        run: () => {
          try {
            const parsed = parseBulk(text.value);
            if (!parsed.length) {
              toast("Nenhuma permissão informada", "bad");
              return false;
            }
            return parsed;
          } catch (error) {
            toast(error.message, "bad");
            return false;
          }
        },
      },
    ]);
    if (result) {
      let nodes = options.nodes();
      for (const node of result) {
        nodes = putNode(nodes, node);
      }
      update(nodes);
      toast(result.length + " permissões adicionadas", "ok");
    }
  };

  const render = () => {
    clear(body);
    const term = search.value.trim().toLowerCase();
    const current = now();
    const visible = options.nodes()
      .filter((node) => !options.exclude || !options.exclude(node))
      .filter((node) => !term || node.key.includes(term))
      .filter((node) => {
        switch (filter.value) {
          case "permanent": return !node.expiresAt;
          case "temporary": return Boolean(node.expiresAt);
          default: return true;
        }
      })
      .sort((a, b) => a.key.localeCompare(b.key));
    if (!visible.length) {
      body.appendChild(el("div", { class: "empty", text: options.emptyText || "Nenhuma permissão." }));
      return;
    }
    const rows = visible.map((node) => {
      let expiry;
      if (!node.expiresAt) {
        expiry = el("span", { class: "muted", text: "permanente" });
      } else if (node.expiresAt <= current) {
        expiry = el("span", { class: "badge bad", text: "expirou " + formatDate(node.expiresAt) });
      } else {
        expiry = el("span", { class: "badge warn", title: formatDate(node.expiresAt), text: "expira em " + formatRemaining(node.expiresAt - current) });
      }
      const edit = button("Editar", async () => {
        const changed = await nodeDialog("Editar permissão", node);
        if (changed) {
          update(putNode(removeNode(options.nodes(), node), changed));
        }
      }, "small ghost");
      const remove = button("Remover", () => update(removeNode(options.nodes(), node)), "small danger");
      return el("tr", null,
        el("td", { class: "mono", text: node.key }),
        el("td", null, expiry),
        el("td", { class: "muted", text: node.addedAt ? formatDate(node.addedAt) : "nova" }),
        el("td", { class: "actions" }, edit, " ", remove));
    });
    body.appendChild(el("div", { class: "table-wrap" }, el("table", null,
      el("thead", null, el("tr", null, el("th", { text: "Permissão" }), el("th", { text: "Expiração" }), el("th", { text: "Adicionada" }), el("th"))),
      el("tbody", null, rows))));
  };

  search.addEventListener("input", () => {
    memo.search = search.value;
    render();
  });
  filter.addEventListener("change", () => {
    memo.filter = filter.value;
    render();
  });
  card.appendChild(el("h2", null, options.title || "Permissões", el("span", { class: "hint", text: options.hint || "" })));
  card.appendChild(el("div", { class: "row" },
    el("div", { class: "grow" }, search), filter, button("+ Adicionar", add, "primary"), button("Em massa", bulk)));
  card.appendChild(el("div", { class: "spacer-8" }));
  card.appendChild(body);
  render();
  return card;
}
