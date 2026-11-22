const listeners = new Set();

export const TAG_PREFIX = "tag.";
export const MEDAL_PREFIX = "medal.";

export const state = {
  session: null,
  clockOffset: 0,
  defaultRank: "member",
  editorPermission: "",
  /* Ranks têm permissões extras editáveis; tags e medalhas são fixas no código (só leitura) */
  original: { ranks: new Map() },
  working: { ranks: new Map() },
  tags: new Map(),
  medals: new Map(),
  users: new Map(),
};

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function notify() {
  for (const listener of listeners) {
    listener();
  }
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function toMap(list) {
  return new Map((list || []).map((item) => [item.id, item]));
}

export function loadData(data) {
  state.defaultRank = data.defaultRank;
  state.editorPermission = data.editorPermission;
  state.original = { ranks: toMap(data.ranks) };
  state.working = { ranks: toMap(clone(data.ranks)) };
  state.tags = toMap(data.tags);
  state.medals = toMap(data.medals);
}

export function loadUser(user) {
  const existing = state.users.get(user.id);
  if (existing && userChanged(existing)) {
    return existing;
  }
  const entry = { original: clone(user), working: clone(user) };
  state.users.set(user.id, entry);
  return entry;
}

export function now() {
  return Date.now() + state.clockOffset;
}

/* ---------------- Comparação ---------------- */

function nodesSignature(nodes) {
  return (nodes || []).map((node) => node.key + "|" + (node.expiresAt || "")).sort().join("\n");
}

function ranksSignature(ranks) {
  return (ranks || []).map((rank) => rank.rank + "|" + (rank.expiresAt || "")).sort().join("\n");
}

function rankChanged(id) {
  const before = state.original.ranks.get(id);
  const after = state.working.ranks.get(id);
  return nodesSignature(before.nodes) !== nodesSignature(after.nodes);
}

function userChanged(entry) {
  const a = entry.original;
  const b = entry.working;
  return ranksSignature(a.ranks) !== ranksSignature(b.ranks)
    || nodesSignature(a.nodes) !== nodesSignature(b.nodes)
    || (a.medals || []).slice().sort().join(",") !== (b.medals || []).slice().sort().join(",")
    || (a.selectedTag || null) !== (b.selectedTag || null)
    || (a.selectedMedal || null) !== (b.selectedMedal || null);
}

export function isChanged(type, id) {
  if (type === "user") {
    const entry = state.users.get(id);
    return Boolean(entry && userChanged(entry));
  }
  return state.working.ranks.has(id) && rankChanged(id);
}

export function pendingChanges() {
  const items = [];
  for (const id of state.working.ranks.keys()) {
    if (rankChanged(id)) {
      items.push({ kind: "edit", type: "rank", id, label: "Rank " + rankName(id), details: rankDetails(id) });
    }
  }
  for (const [id, entry] of state.users) {
    if (userChanged(entry)) {
      items.push({ kind: "edit", type: "user", id, label: "Jogador " + (entry.working.username || id), details: userDetails(entry) });
    }
  }
  return items;
}

export function changesPayload() {
  const payload = [];
  for (const item of pendingChanges()) {
    if (item.type === "rank") {
      const rank = state.working.ranks.get(item.id);
      payload.push({ type: "put-rank", rank: { id: rank.id, nodes: rank.nodes } });
    } else {
      const user = state.users.get(item.id).working;
      payload.push({
        type: "put-user",
        user: {
          id: user.id,
          ranks: user.ranks.map((rank) => ({ rank: rank.rank, expiresAt: rank.expiresAt || null })),
          nodes: user.nodes,
          medals: user.medals,
          selectedTag: user.selectedTag || null,
          selectedMedal: user.selectedMedal || null,
        },
      });
    }
  }
  return payload;
}

export function revert(type, id) {
  if (type === "user") {
    const entry = state.users.get(id);
    entry.working = clone(entry.original);
  } else {
    state.working.ranks.set(id, clone(state.original.ranks.get(id)));
  }
  notify();
}

export function revertAll() {
  state.working.ranks = new Map(Array.from(state.original.ranks.entries()).map(([id, item]) => [id, clone(item)]));
  for (const entry of state.users.values()) {
    entry.working = clone(entry.original);
  }
  notify();
}

export function markSaved(data) {
  loadData(data);
  for (const entry of state.users.values()) {
    entry.original = clone(entry.working);
  }
  notify();
}

/* ---------------- Detalhes do painel de pendentes ---------------- */

function diff(before, after, label) {
  const lines = [];
  for (const key of after) {
    if (!before.includes(key)) {
      lines.push("+ " + label + " " + key);
    }
  }
  for (const key of before) {
    if (!after.includes(key)) {
      lines.push("− " + label + " " + key);
    }
  }
  return lines;
}

function rankDetails(id) {
  const keys = (rank) => (rank.nodes || []).map((node) => node.key);
  return diff(keys(state.original.ranks.get(id)), keys(state.working.ranks.get(id)), "permissão");
}

function userDetails(entry) {
  const a = entry.original;
  const b = entry.working;
  const lines = [];
  lines.push(...diff(a.ranks.map((rank) => rankName(rank.rank)), b.ranks.map((rank) => rankName(rank.rank)), "rank"));
  lines.push(...diff((a.medals || []).map(medalName), (b.medals || []).map(medalName), "medalha"));
  lines.push(...diff(a.nodes.map((node) => node.key), b.nodes.map((node) => node.key), "permissão"));
  if ((a.selectedTag || null) !== (b.selectedTag || null)) {
    lines.push("tag escolhida: " + tagName(a.selectedTag) + " → " + tagName(b.selectedTag));
  }
  if ((a.selectedMedal || null) !== (b.selectedMedal || null)) {
    lines.push("medalha escolhida: " + (a.selectedMedal ? medalName(a.selectedMedal) : "nenhuma") + " → " + (b.selectedMedal ? medalName(b.selectedMedal) : "nenhuma"));
  }
  return lines;
}

/* ---------------- Consultas ---------------- */

export function rankName(id) {
  const rank = state.working.ranks.get(id);
  return rank ? rank.name : id;
}

export function tagName(id) {
  const tag = id ? state.tags.get(id) : null;
  return tag ? tag.name : (id || "automática");
}

export function medalName(id) {
  const medal = state.medals.get(id);
  return medal ? medal.name : id;
}

export function ranksByWeight() {
  return Array.from(state.working.ranks.values()).sort((a, b) => b.weight - a.weight);
}

export function tagsByWeight() {
  return Array.from(state.tags.values()).sort((a, b) => a.weight - b.weight);
}

export function medalsByWeight() {
  return Array.from(state.medals.values()).sort((a, b) => a.weight - b.weight);
}

function isActive(node) {
  return !node.expiresAt || node.expiresAt > now();
}

/* Rank ativo = o de maior peso entre os não expirados (igual ao servidor) */
export function activeRank(user) {
  const ranks = (user.ranks || []).filter(isActive).map((entry) => state.working.ranks.get(entry.rank)).filter(Boolean);
  ranks.sort((a, b) => b.weight - a.weight);
  return ranks[0] || state.working.ranks.get(state.defaultRank);
}

export function hasRankAtLeast(user, rankId) {
  const target = state.working.ranks.get(rankId);
  return Boolean(target) && activeRank(user).weight >= target.weight;
}

export function isStaffer(user) {
  return hasRankAtLeast(user, "trial");
}

/* Estimativa de permissão (o servidor é a referência) */
export function hasPermission(user, key) {
  const lower = key.toLowerCase();
  const ranks = new Set([activeRank(user).id, ...(user.ranks || []).filter(isActive).map((entry) => entry.rank)]);
  for (const id of ranks) {
    const rank = state.working.ranks.get(id);
    if (!rank) {
      continue;
    }
    if (rank.base.some((permission) => permission.toLowerCase() === lower)) {
      return true;
    }
    if (rank.nodes.some((node) => isActive(node) && node.key === lower)) {
      return true;
    }
  }
  return (user.nodes || []).some((node) => isActive(node) && node.key === lower);
}

/* Mesmas regras do Account.hasTag */
export function canUseTag(user, tag) {
  const admin = hasRankAtLeast(user, "admin");
  switch (tag.role) {
    case "COLORED":
      return false;
    case "SPECIAL":
      if (!(hasPermission(user, tag.permission) || admin)) return false;
      break;
    case "VIP":
      if (!(hasRankAtLeast(user, tag.source) || isStaffer(user))) return false;
      break;
    case "ONLY_THESE":
      return false;
    case "STAFF":
      if (!admin && activeRank(user).id !== tag.source) return false;
      break;
    default:
      break;
  }
  return hasRankAtLeast(user, tag.source);
}

/* Mesmas regras do Account.hasMedal */
export function canUseMedal(user, medal) {
  return isStaffer(user) || (user.medals || []).includes(medal.id) || hasPermission(user, medal.permission);
}
