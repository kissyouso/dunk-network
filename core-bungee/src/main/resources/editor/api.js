const TOKEN_KEY = "dunk-editor-token";

/* Funciona tanto na raiz (http://host:1031/) quanto atrás de um reverse proxy (https://host/editor/) */
const BASE_PATH = (() => {
  const path = window.location.pathname;
  if (path.endsWith("/")) return path.slice(0, -1);
  const slash = path.lastIndexOf("/");
  return slash > 0 ? path.substring(0, slash) : "";
})();

const MESSAGES = {
  "editor.unauthenticated": "Abra o editor pelo link do /editor no servidor.",
  "editor.session-expired": "Sua sessão expirou. Use /editor no servidor para abrir outra.",
  "editor.forbidden": "Você não tem mais permissão para usar o editor.",
  "editor.bad-origin": "A requisição precisa vir da página do editor.",
  "editor.too-many-requests": "Muitas requisições. Espere um minuto.",
  "editor.too-large": "As alterações são grandes demais para salvar de uma vez.",
  "editor.invalid-request": "Há um dado inválido nas alterações.",
  "editor.user-not-found": "Nenhum jogador com esse nome.",
  "editor.unavailable": "O servidor não conseguiu concluir. Tente de novo.",
  "permission.unknown-rank": "Há referência a um rank que não existe.",
  "permission.unknown-tag": "Há referência a uma tag que não existe.",
  "permission.unknown-medal": "Há referência a uma medalha que não existe.",
  "access.forbidden": "Só Admin ou superior pode mexer no acesso da rede.",
  "access.server-not-found": "Esse servidor não está mais registrado. Atualize a lista.",
};

export class ApiError extends Error {
  constructor(status, code, message) {
    super(MESSAGES[code] ? MESSAGES[code] + " (" + message + ")" : message);
    this.status = status;
    this.code = code;
    this.friendly = MESSAGES[code] || message;
  }

  get expired() {
    return this.status === 401 || this.status === 403;
  }
}

let token = null;

export function takeToken() {
  const fragment = window.location.hash.replace(/^#/, "");
  if (fragment) {
    try {
      sessionStorage.setItem(TOKEN_KEY, fragment);
    } catch (ignored) {
    }
    history.replaceState(null, "", window.location.pathname);
    token = fragment;
    return token;
  }
  try {
    token = sessionStorage.getItem(TOKEN_KEY);
  } catch (ignored) {
    token = null;
  }
  return token;
}

export function forgetToken() {
  token = null;
  try {
    sessionStorage.removeItem(TOKEN_KEY);
  } catch (ignored) {
  }
}

async function request(method, path, body) {
  const headers = { "Authorization": "Bearer " + (token || "") };
  const options = { method, headers, cache: "no-store", credentials: "omit", referrerPolicy: "no-referrer" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(BASE_PATH + path, options);
  } catch (error) {
    throw new ApiError(0, "network", "Sem conexão com o servidor.");
  }
  let json = null;
  try {
    json = await response.json();
  } catch (ignored) {
    json = null;
  }
  if (!response.ok) {
    const error = json && json.error ? json.error : { code: "http-" + response.status, message: "Erro " + response.status };
    throw new ApiError(response.status, error.code, error.message);
  }
  return json;
}

export const api = {
  session: () => request("GET", "/api/session"),
  data: () => request("GET", "/api/data"),
  searchUser: (name) => request("GET", "/api/users/search?name=" + encodeURIComponent(name)),
  user: (id) => request("GET", "/api/users/" + encodeURIComponent(id)),
  members: (rank) => request("GET", "/api/ranks/" + encodeURIComponent(rank) + "/members"),
  actions: (limit) => request("GET", "/api/actions?limit=" + encodeURIComponent(limit)),
  save: (changes) => request("POST", "/api/changes", { changes }),
  logout: () => request("POST", "/api/logout", {}),
  access: () => request("GET", "/api/access"),
  saveAccess: (access) => request("POST", "/api/access", { access }),
  saveServerWhitelist: (payload) => request("POST", "/api/access/server", payload),
};
