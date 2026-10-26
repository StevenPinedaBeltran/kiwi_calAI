const COLORS = ["#FFFFFF", "#C8C8C8", "#9A9A9A", "#6E6E6E"];
const TOKEN_KEY = "kiwi-token";

const $ = (selector, root = document) => root.querySelector(selector);

let token = localStorage.getItem(TOKEN_KEY) || "";
let googleTimer = null;

// Theme selection stays in this browser, independently of the Android device.
const THEME_KEY = "kiwi-appearance";
function setWebTheme(mode) {
  const choice = mode === "light" ? "light" : "dark";
  document.documentElement.dataset.theme = choice;
  document.documentElement.style.colorScheme = choice;
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", choice === "light" ? "#f4f6fa" : "#090b0f");
  const button = document.getElementById("theme-switch");
  if (button) button.setAttribute("aria-label", `Switch to ${choice === "dark" ? "light" : "dark"} mode`);
  const label = document.getElementById("theme-label");
  if (label) label.textContent = choice === "dark" ? "Light mode" : "Dark mode";
}
const storedTheme = localStorage.getItem(THEME_KEY);
setWebTheme(storedTheme || (window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark"));

// Authenticated image endpoints need the bearer token: direct <img src> would 401.
const photoUrls = new Map();
async function photoSrc(id) {
  if (photoUrls.has(id)) return photoUrls.get(id);
  const resp = await fetch(`/api/photos/${encodeURIComponent(id)}`, {headers: {Authorization:`Bearer ${token}`}});
  if (!resp.ok) throw new Error(`Photo ${id} could not load (${resp.status})`);
  const blob = await resp.blob();
  if (!blob.type.startsWith("image/")) throw new Error("Invalid photo format");
  const url = URL.createObjectURL(blob);
  photoUrls.set(id, url);
  return url;
}
async function showPhotoStrip(target, photos, limit = 6) {
  if (!target) return;
  const ids = photos.slice(0,limit).map(p => p.id);
  for (const [key,url] of photoUrls) {
    if (!ids.includes(key)) { URL.revokeObjectURL(url); photoUrls.delete(key); }
  }
  target.replaceChildren();
  if (!ids.length) {
    target.append(el("p", {class:"muted"}, "No photos available yet. Link an album or pick photos in the Photos tab."));
    return;
  }
  const items = ids.map(id => el("div",{class:"photo-tile",role:"img","aria-label":"Family photo"},
    el("span",{class:"photo-skeleton"},"Loading…")));
  target.append(...items);
  await Promise.allSettled(ids.map(async (id,index) => {
    try {
      const src = await photoSrc(id);
      if (!items[index].isConnected) return;
      const img = el("img",{src,alt:"Family memory",loading:"lazy"});
      items[index].replaceChildren(img);
    } catch {
      if (items[index].isConnected) items[index].replaceChildren(el("span",{class:"photo-skeleton"},"Photo unavailable"));
    }
  }));
}


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
  for (const u of photoUrls.values()) URL.revokeObjectURL(u);
  photoUrls.clear();
  $("#app").hidden = true;
  $("#login").hidden = false;
  if (message) $("#login-message").textContent = message;
}

function showApp(tab) {
  $("#login").hidden = true;
  $("#app").hidden = false;
  selectTab(["dashboard", "add", "upcoming", "photos", "calendars"].includes(tab) ? tab : "dashboard");
}

function selectTab(name) {
  for (const button of document.querySelectorAll("[role=tab]")) {
    const selected = button.dataset.tab === name;
    button.setAttribute("aria-selected", String(selected));
    button.tabIndex = selected ? 0 : -1;
    $(`#${button.getAttribute("aria-controls")}`).hidden = !selected;
  }
  clearInterval(googleTimer);
  if (name === "dashboard") loadDashboard();
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
    const endDate = data.get("end_date") || date;
    if (endDate < date) { toast("End date cannot be before the start date", true); return; }
    body.end = addDays(endDate, 1);
  } else {
    const start = data.get("start") || "09:00";
    const end = data.get("end") || start;
    const endDate = data.get("end_date") || date;
    if (endDate < date) { toast("End date cannot be before the start date", true); return; }
    body.start = withOffset(date, start);
    body.end = withOffset(endDate === date && end < start ? addDays(date, 1) : endDate, end);
  }
  const button = form.querySelector("button[type=submit]");
  button.disabled = true;
  try {
    const wasEditing = Boolean(editingEvent);
    if (wasEditing) await api(`/api/events/${encodeURIComponent(editingEvent.id)}`, { method: "PUT", body });
    else await api("/api/events", { method: "POST", body });
    toast(`${wasEditing ? "Updated" : "Added"} “${body.title}”.`);
    resetEventForm();
    selectTab(wasEditing ? "upcoming" : "dashboard");
    if (wasEditing) loadUpcoming();
  } catch (err) {
    toast(err.message, true);
  } finally {
    button.disabled = false;
  }
}

// Agenda: source filtering and search over real server events.
let agendaEvents = [];
let editingEvent = null;
let detailedEvent = null;

function agendaMatches(event) {
  const filter = $("#agenda-source-filter").value;
  if (filter === "local" && event.source !== "local") return false;
  if (filter === "subscribed" && event.source === "local") return false;
  const query = $("#agenda-search-input").value.trim().toLocaleLowerCase();
  return !query || [event.title, event.location, event.notes, event.calendar].some((value) => String(value || "").toLocaleLowerCase().includes(query));
}

function renderUpcoming() {
  const list = $("#upcoming-list");
  const results = agendaEvents.filter(agendaMatches);
  $("#agenda-results").textContent = `${results.length} event${results.length === 1 ? "" : "s"} found`;
  list.replaceChildren();
  if (!results.length) {
    list.append(el("p", { class: "muted empty-text" }, "No matching events. Try another search or add something to your calendar."));
    return;
  }
  let lastLabel = null;
  for (const event of results) {
    const start = parseStart(event);
    const label = dayLabel(start);
    if (label !== lastLabel) {
      list.append(el("h3", {}, label));
      lastLabel = label;
    }
    const sub = [event.location, event.calendar || (event.source === "local" ? "Kiwi Calendar" : "")].filter(Boolean).join(" · ");
    list.append(el("button", { class: "agenda-item agenda-event-button", type: "button", onclick: () => showEventDetails(event) },
      el("span", { class: "agenda-bar", style: `background:${event.color || COLORS[0]}` }),
      el("span", { class: "agenda-time" }, event.all_day ? "All day" : timeFormat.format(start)),
      el("div", {}, el("div", { class: "agenda-title" }, event.title), sub ? el("div", { class: "agenda-sub" }, sub) : null),
      el("span", { class: "agenda-open" }, "↗")));
  }
}

async function loadUpcoming() {
  const list = $("#upcoming-list");
  list.replaceChildren(el("p", { class: "muted" }, "Loading events…"));
  try {
    agendaEvents = await api("/api/events?days=90");
    renderUpcoming();
  } catch (err) {
    list.replaceChildren(el("p", { class: "status error" }, err.message));
  }
}

async function deleteEvent(event) {
  if (event.source !== "local" || !confirm(`Delete “${event.title}”?`)) return;
  try {
    await api(`/api/events/${encodeURIComponent(event.id)}`, { method: "DELETE" });
    toast(`Deleted “${event.title}”.`);
    loadUpcoming();
    if (!$("#tab-dashboard").hidden) loadDashboard();
  } catch (err) { toast(err.message, true); }
}

function resetEventForm() {
  editingEvent = null;
  const form = $("#event-form");
  form.reset();
  form.date.value = ymd(new Date());
  form.end_date.value = form.date.value;
  form.start.value = "09:00";
  form.end.value = "10:00";
  $("#time-row").hidden = false;
  $("#save-event-button").firstChild.textContent = "Add event ";
  $("#cancel-edit").hidden = true;
}

function startNewEvent(dateStr = ymd(new Date())) {
  resetEventForm();
  $("#event-form").date.value = dateStr;
  $("#event-form").end_date.value = dateStr;
  selectTab("add");
  $("#event-form").title.focus();
}

function editEvent(event) {
  if (event.source !== "local") return;
  $("#event-dialog").close();
  editingEvent = event;
  const form = $("#event-form");
  form.title.value = event.title;
  form.location.value = event.location || "";
  form.notes.value = event.notes || "";
  form.all_day.checked = Boolean(event.all_day);
  const start = parseStart(event);
  const end = event.all_day ? new Date(`${addDays(event.end.slice(0,10), -1)}T00:00:00`) : new Date(event.end);
  form.date.value = ymd(start);
  form.end_date.value = ymd(end);
  form.start.value = `${pad(start.getHours())}:${pad(start.getMinutes())}`;
  form.end.value = `${pad(end.getHours())}:${pad(end.getMinutes())}`;
  $("#time-row").hidden = Boolean(event.all_day);
  let color = event.color || COLORS[0];
  if (!COLORS.includes(color.toUpperCase())) color = COLORS[0];
  const swatch = form.querySelector(`input[name="color"][value="${color.toUpperCase()}"]`);
  if (swatch) swatch.checked = true;
  $("#save-event-button").firstChild.textContent = "Save changes ";
  $("#cancel-edit").hidden = false;
  selectTab("add");
  form.title.focus();
}

function showEventDetails(event) {
  detailedEvent = event;
  $("#detail-title").textContent = event.title;
  const start = parseStart(event);
  const meta = $("#detail-meta");
  const end = event.end && !event.all_day ? new Date(event.end) : null;
  meta.replaceChildren(
    el("span", {}, "◷  " + dayFormat.format(start) + (event.all_day ? " · All day" : " · " + timeFormat.format(start) + (end ? " – " + timeFormat.format(end) : ""))),
    el("span", {}, "▦  " + (event.calendar || "Kiwi Calendar"))
  );
  const extra = $("#detail-extra");
  extra.replaceChildren();
  if (event.location) extra.append(el("p", {}, "⌖  " + event.location));
  if (event.notes) extra.append(el("p", {}, event.notes));
  const mine = event.source === "local";
  $("#detail-edit").hidden = !mine;
  $("#detail-delete").hidden = !mine;
  $("#event-dialog").showModal();
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
    $("#library-preview-count").textContent = `${photos.photos.length} photos`;
    showPhotoStrip($("#library-photo-strip"), photos.photos, 12);
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

// A family-oriented calendar, backed by the same server data as Android.
let calendarMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
let selectedDate = ymd(new Date());
let monthEvents = [];
let dashboardRequestId = 0;
let monthRequestId = 0;

function localMidnight(day) { return new Date(`${day}T00:00:00`); }
function dayEvents(events, day) {
  const from = localMidnight(day);
  const to = localMidnight(addDays(day, 1));
  return events.filter(event => {
    const start = parseStart(event);
    const end = event.end ? (event.all_day ? localMidnight(event.end.slice(0, 10)) : new Date(event.end)) : start;
    return start < to && (end > from || (start >= from && start < to));
  }).sort((a,b) => Number(!a.all_day) - Number(!b.all_day) || parseStart(a) - parseStart(b));
}

function calendarGridDates() {
  const first = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth(), 1);
  const begin = new Date(first.getFullYear(), first.getMonth(), 1-first.getDay());
  return Array.from({length:42}, (_, i) => ymd(new Date(begin.getFullYear(), begin.getMonth(), begin.getDate()+i)));
}

function paintMonth() {
  $("#calendar-month-label").textContent = calendarMonth.toLocaleDateString(undefined, {month: "long", year: "numeric"});
  const grid = $("#calendar-grid");
  const today = ymd(new Date());
  const buttons = calendarGridDates().map(key => {
    const date = localMidnight(key);
    const events = dayEvents(monthEvents, key);
    const outside = date.getMonth() !== calendarMonth.getMonth();
    const btn = el("button", {
      type:"button", class:`cal-day${outside ? " outside" : ""}${key === today ? " today" : ""}${key === selectedDate ? " selected" : ""}`,
      "aria-label": `${dayFormat.format(date)}, ${events.length} events`, "aria-pressed": String(key === selectedDate),
      onclick: () => selectDay(key),
    }, el("span", {class:"cal-number"}, String(date.getDate())));
    const holder = el("span", {class:"cal-event-holder"});
    events.slice(0,2).forEach(e => holder.append(el("span", {class:"cal-event", title:e.title},
      el("span", {class:"cal-event-dot", style:`background:${e.color || COLORS[0]}`}),
      el("span", {class:"cal-event-title"}, e.title))));
    if (events.length > 2) holder.append(el("span", {class:"cal-more"}, `+${events.length - 2} more`));
    btn.append(holder);
    return btn;
  });
  grid.replaceChildren(...buttons);
  paintDay();
}

function selectDay(day) {
  selectedDate = day;
  const d = localMidnight(day);
  if (d.getMonth() !== calendarMonth.getMonth() || d.getFullYear() !== calendarMonth.getFullYear()) {
    calendarMonth = new Date(d.getFullYear(),d.getMonth(),1);
    loadMonth();
  } else paintMonth();
}

function paintDay() {
  const d = localMidnight(selectedDate);
  const events = dayEvents(monthEvents, selectedDate);
  $("#selected-weekday").textContent = d.toLocaleDateString(undefined, {weekday: "long"});
  $("#selected-full-date").textContent = d.toLocaleDateString(undefined, {month:"long",day:"numeric",year:"numeric"});
  $("#selected-count").textContent = `${events.length} event${events.length===1?"":"s"}`;
  const agenda = $("#selected-day-agenda");
  if (!events.length) {
    agenda.replaceChildren(el("div",{class:"day-empty"},
      el("span",{class:"empty-moon", "aria-hidden":"true"},"◯"),
      el("strong",{},"Your day is open."),
      el("p",{},"Nothing planned yet. Add something the whole family can see.")));
    return;
  }
  agenda.replaceChildren(...events.map(event => {
    const date = parseStart(event);
    const meta = [event.all_day ? "All day" : timeFormat.format(date),event.location].filter(Boolean).join(" · ");
    return el("button", {class:"day-event",type:"button",onclick:()=>showEventDetails(event)},
      el("span", {class:"day-event-marker",style:`background:${event.color || COLORS[0]}`}),
      el("span",{class:"day-event-content"},el("strong",{},event.title),el("small",{},meta)),
      el("span",{class:"day-event-arrow"},"↗"));
  }));
}

async function loadMonth() {
  const id = ++monthRequestId;
  const dates = calendarGridDates();
  try {
    const end = addDays(dates[41], 1);
    const events = await api(`/api/events/range?start=${dates[0]}&end=${end}`);
    if (id !== monthRequestId) return;
    monthEvents = events;
    paintMonth();
  } catch(err) {
    if(id!==monthRequestId)return;
    $("#calendar-grid").replaceChildren(el("p", {class:"status error"}, `Couldn't load the calendar: ${err.message}`));
    $("#selected-day-agenda").replaceChildren();
  }
}

async function refreshHomeStatus() {
  const id = ++dashboardRequestId;
  $("#server-status-text").textContent = "checking server";
  try {
    const [events, feeds, photos, revision] = await Promise.all([
      api("/api/events?days=90"), api("/api/feeds"), api("/api/photos"), api("/api/revision")
    ]);
    if (id !== dashboardRequestId) return;
    const current = new Date();
    const upcoming = events.filter(e => {
      const start = parseStart(e);
      const end = e.end ? (e.all_day ? localMidnight(e.end.slice(0,10)) : new Date(e.end)) : start;
      return end > current || start >= current;
    }).sort((a,b)=>parseStart(a)-parseStart(b));
    const todays = dayEvents(events,ymd(current));
    $("#overview-day-label").textContent = current.toLocaleDateString(undefined,{weekday:"long",month:"long",day:"numeric"}).toUpperCase();
    $("#overview-summary").textContent = `${todays.length} today · ${upcoming.length} upcoming`;
    const next = $("#next-events");
    if (!upcoming.length) next.replaceChildren(el("div",{class:"no-upcoming"},"Nothing coming up. Add your first event to get started."));
    else next.replaceChildren(...upcoming.slice(0,4).map(event=>{
      const start = parseStart(event);
      return el("button", {class:"next-event",type:"button",onclick:()=>showEventDetails(event)},
        el("span", {class:"next-event-date"},
          el("strong",{},String(start.getDate()).padStart(2,"0")),
          el("small",{},start.toLocaleDateString(undefined,{month:"short"}).toUpperCase())),
        el("span", {class:"next-event-body"},el("strong",{},event.title),
          el("small",{},`${dayLabel(start)} · ${event.all_day ? "All day" : timeFormat.format(start)}${event.calendar ? " · "+event.calendar : ""}`)),
        el("span",{class:"next-event-arrow"},"↗"));
    }));
    const feedErrors = feeds.filter(f=>f.last_error);
    $("#stat-feeds").textContent=feeds.length;
    $("#home-feed-count").textContent = feeds.length;
    $("#home-photo-count").textContent = photos.photos?.length || 0;
    $("#month-event-count").textContent = events.filter(e => {
      const d = parseStart(e);
      return d.getMonth() === current.getMonth() && d.getFullYear() === current.getFullYear();
    }).length;
    showPhotoStrip($("#home-photo-strip"), photos.photos || [], 6);
    $("#feed-health").textContent = !feeds.length ? "No calendars linked" : feedErrors.length ? `${feedErrors.length} connection${feedErrors.length===1?"":"s"} need attention` : `${feeds.length} subscribed calendar${feeds.length===1?"":"s"} · no reported errors`;
    $("#stat-photos").textContent = Array.isArray(photos.photos)?photos.photos.length:0;
    const album=photos.album||{};
    $("#photo-health").textContent = album.error ? "Album needs attention" : album.refreshing ? "Photos syncing…" : album.url ? "Shared album connected" : "No album added yet";
    $("#server-health").textContent = `Server responding · data revision ${revision.revision}`;
    $("#server-badge").textContent = "SERVER OK";
    $("#server-badge").classList.remove("offline");
    $("#server-status-text").textContent = "server connected";
    $("#dashboard-updated").textContent = `LAST CHECKED / ${current.toLocaleTimeString(undefined,{hour:"numeric",minute:"2-digit"})}`;
  } catch(err) {
    if(id!==dashboardRequestId)return;
    $("#server-status-text").textContent="connection issue";
    $("#server-health").textContent=err.message;
    $("#server-badge").textContent="ERROR";
    $("#server-badge").classList.add("offline");
    if (err.message!=="Not signed in") toast(`Couldn't refresh the dashboard: ${err.message}`,true);
  }
}

function loadDashboard() {
  window.dispatchEvent(new Event("kiwi:dashboard"));
  return Promise.all([loadMonth(), refreshHomeStatus()]);
}

// Wiring

function init() {
  $("#theme-switch").addEventListener("click", () => {
    const mode = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    localStorage.setItem(THEME_KEY, mode);
    setWebTheme(mode);
  });
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
  eventForm.end_date.value = eventForm.date.value;
  eventForm.date.addEventListener("change", () => { if (eventForm.end_date.value < eventForm.date.value) eventForm.end_date.value = eventForm.date.value; });
  eventForm.all_day.addEventListener("change", () => { $("#time-row").hidden = eventForm.all_day.checked; });
  eventForm.addEventListener("submit", submitEvent);
  $("#cancel-edit").addEventListener("click", resetEventForm);
  $("#agenda-search-input").addEventListener("input", renderUpcoming);
  $("#agenda-source-filter").addEventListener("change", renderUpcoming);
  $("#home-refresh").addEventListener("click", loadDashboard);
  $("#home-sync-feeds").addEventListener("click", async e => {
    const button=e.currentTarget;
    button.disabled=true;
    try { await api("/api/feeds/refresh", {method:"POST"}); toast("Calendar feeds refreshed."); loadDashboard(); }
    catch(err) { toast(`Couldn't refresh feeds: ${err.message}`,true); }
    finally { button.disabled=false; }
  });
  $("#home-sync-photos").addEventListener("click", async e => {
    const button = e.currentTarget;
    button.disabled = true;
    try {
      await api("/api/photos/album/refresh", {method:"POST"});
      toast("Photo refresh started. Open Photos to view the result.");
      loadDashboard();
    } catch(err) { toast(`Couldn't refresh photos: ${err.message}`, true); }
    finally { button.disabled = false; }
  });
  $("#home-add").addEventListener("click", () => startNewEvent());
  $("#day-add").addEventListener("click", () => startNewEvent(selectedDate));
  function moveMonth(delta) {
    calendarMonth = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + delta, 1);
    const today = new Date();
    selectedDate = calendarMonth.getFullYear() === today.getFullYear() && calendarMonth.getMonth() === today.getMonth() ? ymd(today) : ymd(calendarMonth);
    loadMonth();
  }
  $("#calendar-prev").addEventListener("click", () => moveMonth(-1));
  $("#calendar-next").addEventListener("click", () => moveMonth(1));
  $("#calendar-today").addEventListener("click", () => { const now=new Date(); calendarMonth = new Date(now.getFullYear(),now.getMonth(),1); selectedDate = ymd(now); loadMonth(); });
  $("#detail-close").addEventListener("click", () => $("#event-dialog").close());
  $("#detail-done").addEventListener("click", () => $("#event-dialog").close());
  $("#detail-edit").addEventListener("click", () => { if(detailedEvent) editEvent(detailedEvent); });
  $("#detail-delete").addEventListener("click", async () => { const event=detailedEvent; $("#event-dialog").close(); if(event) { await deleteEvent(event); if(!$("#tab-dashboard").hidden) loadDashboard(); } });
  $("#copy-server-url").addEventListener("click", async () => {
    const host = location.hostname;
    if (["localhost","127.0.0.1","::1"].includes(host)) { toast("Open this dashboard using your Windows LAN IP to get a usable Android address."); return; }
    const url = `${location.protocol}//${location.host}`;
    try { await navigator.clipboard.writeText(url); toast("Server address copied. Enter it in the Android Kiwi app."); }
    catch { toast(`Server address: ${url}`); }
  });
  if (!["localhost","127.0.0.1","::1"].includes(location.hostname)) $("#copy-hint").textContent = `${location.protocol}//${location.host}`;

  for (const button of document.querySelectorAll("[role=tab]")) {
    button.addEventListener("click", () => selectTab(button.dataset.tab));
  }
  for (const button of document.querySelectorAll("[data-go-tab]")) {
    button.addEventListener("click", () => selectTab(button.dataset.goTab));
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
