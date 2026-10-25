const COLORS = ["#22553a", "#3b6ea8", "#7a4fb5", "#b8457a", "#c2412d", "#c97a1a", "#8a7a12", "#566069"];
const TOKEN_KEY = "kiwi-token";

const $ = (selector, root = document) => root.querySelector(selector);

let token = localStorage.getItem(TOKEN_KEY) || "";
let googleTimer = null;

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "class") node.className = value;
    else if (key === "style") node.style.cssText = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
    else if (value !== false && value != null) node.setAttribute(key, value === true ? "" : value);
  }
  node.append(...children.filter((c) => c != null && c !== false));
  return node;
}

function toast(message, isError = false) {
  const box = $("#toast");
  box.textContent = message;
  box.classList.toggle("error", isError);
  box.classList.add("show");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => box.classList.remove("show"), 3500);
}

async function api(path, { method = "GET", body } = {}) {
  const headers = { Authorization: `Bearer ${token}` };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (res.status === 401) {
    showLogin("That access token isn't valid anymore. Scan the QR code on the calendar again.");
    throw new Error("Not signed in");
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const data = await res.json();
      if (typeof data.detail === "string") message = data.detail;
      else if (Array.isArray(data.detail)) message = data.detail.map((d) => d.msg).join(", ");
    } catch {}
    throw new Error(message);
  }
  return res.status === 204 ? null : res.json();
}

// Dates

const pad = (n) => String(n).padStart(2, "0");
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

function addDays(dateString, days) {
  const [y, m, d] = dateString.split("-").map(Number);
  return ymd(new Date(y, m - 1, d + days));
}

function withOffset(dateString, timeString) {
  const local = new Date(`${dateString}T${timeString}`);
  const offset = -local.getTimezoneOffset();
  const sign = offset >= 0 ? "+" : "-";
  const abs = Math.abs(offset);
  return `${dateString}T${timeString}:00${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}

function parseStart(event) {
  if (event.all_day) {
    const [y, m, d] = event.start.split("-").map(Number);
    return new Date(y, m - 1, d);
  }
  return new Date(event.start);
}

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });
const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: "long", month: "long", day: "numeric" });

function dayLabel(date) {
  const today = ymd(new Date());
  const key = ymd(date);
  if (key === today) return "Today";
  if (key === addDays(today, 1)) return "Tomorrow";
  return dayFormat.format(date);
}

function renderMasthead() {
  const now = new Date();
  $("#today-day").textContent = now.getDate();
  $("#today-weekday").textContent = now.toLocaleDateString(undefined, { weekday: "long" });
  $("#today-month").textContent = now.toLocaleDateString(undefined, { month: "long", year: "numeric" });
}

// Shell

function showLogin(message) {
  $("#app").hidden = true;
  $("#login").hidden = false;
  if (message) $("#login-message").textContent = message;
}

function showApp(tab) {
  $("#login").hidden = true;
  $("#app").hidden = false;
  selectTab(tab || "add");
}

function selectTab(name) {
  for (const button of document.querySelectorAll("[role=tab]")) {
    const selected = button.dataset.tab === name;
    button.setAttribute("aria-selected", String(selected));
    button.tabIndex = selected ? 0 : -1;
    $(`#${button.getAttribute("aria-controls")}`).hidden = !selected;
  }
  clearInterval(googleTimer);
  if (name === "upcoming") loadUpcoming();
  if (name === "photos") loadPhotos();
  if (name === "calendars") loadFeeds();
}

function renderSwatches(container, name) {
  COLORS.forEach((color, i) => {
    container.append(
      el("label", { class: "swatch", style: `background:${color}`, title: color },
        el("input", { type: "radio", name, value: color, checked: i === 0, "aria-label": `Color ${i + 1}` }))
    );
  });
}

// Add event

async function submitEvent(e) {
  e.preventDefault();
  const form = e.target;
  const data = new FormData(form);
  const date = data.get("date");
  const allDay = data.get("all_day") === "on";
  const body = {
    title: data.get("title").trim(),
    all_day: allDay,
    location: data.get("location").trim() || null,
    notes: data.get("notes").trim() || null,
    color: data.get("color"),
  };
  if (allDay) {
    body.start = date;
    body.end = addDays(date, 1);
  } else {
    const start = data.get("start") || "09:00";
    const end = data.get("end") || start;
    body.start = withOffset(date, start);
    body.end = withOffset(end > start ? date : addDays(date, end === start ? 0 : 1), end);
  }
  const button = form.querySelector("button[type=submit]");
  button.disabled = true;
  try {
    await api("/api/events", { method: "POST", body });
    toast(`Added “${body.title}”. The calendar picks it up within a couple of minutes.`);
    form.title.value = "";
    form.location.value = "";
    form.notes.value = "";
    form.title.focus();
  } catch (err) {
    toast(err.message, true);
  } finally {
    button.disabled = false;
  }
}

// Coming up

async function loadUpcoming() {
  const list = $("#upcoming-list");
  list.replaceChildren(el("p", { class: "muted" }, "Loading…"));
  try {
    const events = await api("/api/events?days=45");
    list.replaceChildren();
    if (!events.length) {
      list.append(el("p", { class: "muted" }, "Nothing in the next six weeks. Add something from the Add tab."));
      return;
    }
    let lastLabel = null;
    for (const event of events) {
      const start = parseStart(event);
      const label = dayLabel(start < new Date(new Date().toDateString()) ? new Date() : start);
      if (label !== lastLabel) {
        list.append(el("h3", {}, label));
        lastLabel = label;
      }
      const sub = [event.location, event.calendar].filter(Boolean).join(", ");
      const remove = event.source === "local"
        ? el("button", { class: "link danger", type: "button", onclick: () => deleteEvent(event) }, "Delete")
        : null;
      list.append(
        el("div", { class: "agenda-item" },
          el("span", { class: "agenda-bar", style: `background:${event.color || COLORS[0]}` }),
          el("span", { class: "agenda-time" }, event.all_day ? "All day" : timeFormat.format(start)),
          el("div", {}, el("div", { class: "agenda-title" }, event.title), sub ? el("div", { class: "agenda-sub" }, sub) : null),
          remove)
      );
    }
  } catch (err) {
    list.replaceChildren(el("p", { class: "status error" }, err.message));
  }
}

async function deleteEvent(event) {
  if (!confirm(`Delete “${event.title}”?`)) return;
  try {
    await api(`/api/events/${encodeURIComponent(event.id)}`, { method: "DELETE" });
    toast(`Deleted “${event.title}”.`);
    loadUpcoming();
  } catch (err) {
    toast(err.message, true);
  }
}

// Photos

function describeAlbum(album) {
  if (album.refreshing) return "Checking the album for photos…";
  if (album.error) return album.error;
  if (!album.url) return "No album linked yet.";
  const when = album.last_sync ? new Date(album.last_sync * 1000).toLocaleString() : "not yet";
  return `${album.count} photos. Last checked ${when}.`;
}

async function loadPhotos() {
  try {
    const [photos, google] = await Promise.all([api("/api/photos"), api("/api/google")]);
    const { album } = photos;
    const form = $("#album-form");
    if (album.url && document.activeElement !== form.url) form.url.value = album.url;
    const status = $("#album-status");
    status.textContent = describeAlbum(album);
    status.classList.toggle("error", Boolean(album.error) && !album.refreshing);
    $("#album-remove").hidden = !album.url;
    renderGoogle(google);
    const busy = album.refreshing || google.auth || ["waiting", "downloading"].includes(google.pick.state);
    clearInterval(googleTimer);
    if (busy) googleTimer = setInterval(loadPhotos, 3000);
  } catch (err) {
    $("#album-status").textContent = err.message;
  }
}

function renderGoogle(g) {
  const box = $("#google-box");
  box.replaceChildren();
  if (!g.configured) {
    box.append(el("p", { class: "muted" },
      "Picking photos from your library needs a Google Cloud OAuth client. Add GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET to the server, then restart it. The README explains how."));
    return;
  }
  if (g.auth) {
    box.append(
      el("p", {}, "On any device, open ", el("a", { href: g.auth.verification_url, target: "_blank", rel: "noopener" }, g.auth.verification_url), " and enter this code:"),
      el("div", { class: "code" }, g.auth.user_code),
      el("p", { class: "muted" }, "This page updates by itself once you approve.")
    );
    return;
  }
  if (!g.connected) {
    if (g.auth_error) box.append(el("p", { class: "status error" }, g.auth_error));
    box.append(
      el("p", { class: "muted" }, "Choose individual photos from your library. You can search by album name while picking."),
      el("button", { type: "button", onclick: connectGoogle }, "Connect Google account")
    );
    return;
  }

  const pick = g.pick || { state: "idle" };
  if (pick.state === "waiting") {
    box.append(
      el("p", {}, "Open Google Photos, choose photos, then tap Done."),
      el("p", {}, el("a", { href: pick.picker_uri, target: "_blank", rel: "noopener" }, "Open Google Photos"))
    );
  } else if (pick.state === "downloading") {
    box.append(el("p", {}, `Saving ${pick.done} of ${pick.total} photos…`));
  } else {
    if (pick.state === "done") box.append(el("p", { class: "status" }, `Added ${pick.total} photos.`));
    if (pick.error) box.append(el("p", { class: "status error" }, pick.error));
    box.append(el("div", { class: "row" },
      el("button", { type: "button", onclick: startPick }, "Pick photos"),
      el("button", { type: "button", class: "quiet", onclick: disconnectGoogle }, "Disconnect")));
  }
  if (g.picked_count) {
    box.append(el("p", { class: "muted", style: "margin-top:1rem" },
      `${g.picked_count} picked photos on the calendar. `,
      el("button", { type: "button", class: "link danger", onclick: clearPicked }, "Remove them")));
  }
}

async function runAndReload(action, okMessage) {
  try {
    await action();
    if (okMessage) toast(okMessage);
  } catch (err) {
    toast(err.message, true);
  }
  loadPhotos();
}

const connectGoogle = () => runAndReload(() => api("/api/google/connect", { method: "POST" }));
const startPick = () => runAndReload(async () => {
  const status = await api("/api/google/pick", { method: "POST" });
  window.open(status.pick.picker_uri, "_blank", "noopener");
});
const disconnectGoogle = () => runAndReload(() => api("/api/google/disconnect", { method: "POST" }), "Disconnected.");
const clearPicked = () => {
  if (confirm("Remove every picked photo from the calendar?")) {
    runAndReload(() => api("/api/photos/picked", { method: "DELETE" }), "Picked photos removed.");
  }
};

// Calendars

async function loadFeeds() {
  const list = $("#feed-list");
  try {
    const feeds = await api("/api/feeds");
    list.replaceChildren(...feeds.map((feed) => {
      const meta = feed.last_error
        ? el("div", { class: "feed-meta danger" }, feed.last_error)
        : el("div", { class: "feed-meta" }, feed.last_sync ? `Updated ${new Date(feed.last_sync * 1000).toLocaleString()}` : "Not loaded yet");
      return el("li", {},
        el("span", { class: "feed-dot", style: `background:${feed.color}` }),
        el("div", {}, el("strong", {}, feed.name), meta),
        el("button", { type: "button", class: "link danger", onclick: () => removeFeed(feed) }, "Remove"));
    }));
  } catch (err) {
    list.replaceChildren(el("li", { class: "status error" }, err.message));
  }
}

async function removeFeed(feed) {
  if (!confirm(`Remove “${feed.name}” and its events from the calendar?`)) return;
  try {
    await api(`/api/feeds/${encodeURIComponent(feed.id)}`, { method: "DELETE" });
    toast(`Removed “${feed.name}”.`);
  } catch (err) {
    toast(err.message, true);
  }
  loadFeeds();
}

// Wiring

function init() {
  renderMasthead();
  renderSwatches($("#event-colors"), "color");
  renderSwatches($("#feed-colors"), "color");

  const params = new URLSearchParams(location.hash.slice(1));
  if (params.get("token")) {
    token = params.get("token");
    localStorage.setItem(TOKEN_KEY, token);
  }
  const startTab = params.get("tab");
  if (location.hash) history.replaceState(null, "", location.pathname);

  const eventForm = $("#event-form");
  eventForm.date.value = ymd(new Date());
  eventForm.all_day.addEventListener("change", () => { $("#time-row").hidden = eventForm.all_day.checked; });
  eventForm.addEventListener("submit", submitEvent);

  for (const button of document.querySelectorAll("[role=tab]")) {
    button.addEventListener("click", () => selectTab(button.dataset.tab));
  }

  $("#login-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    token = e.target.token.value.trim();
    try {
      await api("/api/revision");
      localStorage.setItem(TOKEN_KEY, token);
      showApp();
    } catch (err) {
      if (err.message !== "Not signed in") $("#login-message").textContent = err.message;
    }
  });

  $("#album-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const url = e.target.url.value.trim();
    runAndReload(() => api("/api/photos/album", { method: "PUT", body: { url } }), "Album link saved. Downloading photos now.");
  });
  $("#album-refresh").addEventListener("click", () => runAndReload(() => api("/api/photos/album/refresh", { method: "POST" })));
  $("#album-remove").addEventListener("click", () => {
    if (confirm("Remove the album and all of its photos from the calendar?")) {
      runAndReload(() => api("/api/photos/album", { method: "DELETE" }), "Album removed.");
    }
  });

  $("#feed-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.target;
    const data = new FormData(form);
    const button = form.querySelector("button[type=submit]");
    button.disabled = true;
    try {
      const feed = await api("/api/feeds", { method: "POST", body: { name: data.get("name").trim(), url: data.get("url").trim(), color: data.get("color") } });
      toast(feed.last_error ? `Added “${feed.name}”, but it couldn't be loaded: ${feed.last_error}` : `Added “${feed.name}”.`, Boolean(feed.last_error));
      form.reset();
      form.querySelector("input[name=color]").checked = true;
    } catch (err) {
      toast(err.message, true);
    } finally {
      button.disabled = false;
    }
    loadFeeds();
  });
  $("#feeds-refresh").addEventListener("click", async () => {
    try {
      await api("/api/feeds/refresh", { method: "POST" });
      toast("Calendars refreshed.");
    } catch (err) {
      toast(err.message, true);
    }
    loadFeeds();
  });

  if (token) showApp(startTab);
  else showLogin();
}

init();
