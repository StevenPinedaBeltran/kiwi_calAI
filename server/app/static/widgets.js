// Live weather and followed-team cards. Browser preferences are independent
// from the Android tablet settings; responses always come from the Kiwi server.
const TOKEN_KEY = "kiwi-token";
const WEATHER_KEY = "kiwi-widget-weather-v1";
const SPORTS_KEY = "kiwi-widget-sports-v1";
const $ = id => document.getElementById(id);
const safeLoad = (key, fallback) => {
  try { return JSON.parse(localStorage.getItem(key)) || fallback; } catch { return fallback; }
};
let weatherPrefs = safeLoad(WEATHER_KEY, {place:null, units:"imperial"});
let followed = safeLoad(SPORTS_KEY, []);
let selectedTeam = null;
let lastWeather = null, lastScores = null;
let weatherBusy = false, scoresBusy = false;
let lastWeatherCheck = 0, lastScoresCheck = 0;
const text = (id, value) => { if ($(id)) $(id).textContent = value == null ? "—" : String(value); };
const timeString = iso => {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "Time unavailable" : d.toLocaleString(undefined, {weekday:"short",hour:"numeric",minute:"2-digit"});
};
const stamp = () => `Updated ${new Date().toLocaleTimeString(undefined,{hour:"numeric",minute:"2-digit"})}`;
async function request(path) {
  const token = localStorage.getItem(TOKEN_KEY);
  if (!token) throw Error("Sign in to Kiwi Calendar to load this widget.");
  let r;
  try { r = await fetch(path,{headers:{Authorization:`Bearer ${token}`},cache:"no-store"}); }
  catch { throw Error("Server offline. Check the Windows Docker connection."); }
  if (!r.ok) throw Error(r.status === 401 ? "Access token expired. Sign in again." : `Provider unavailable (${r.status}). Try again.`);
  return r.json();
}
function node(tag, className, value) {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (value != null) el.textContent = value;
  return el;
}
function weatherView(data, cached=false) {
  const c=data.current||{}, day=data.daily?.[0]||{};
  const unit = data.labels?.temp || (data.units === "metric" ? "°C" : "°F");
  const fmt = v => v == null ? "—" : `${Math.round(v)}${unit}`;
  text("weather-city", weatherPrefs.place ? [weatherPrefs.place.name, weatherPrefs.place.region].filter(Boolean).join(", ") : "");
  text("weather-temperature",fmt(c.temp));
  text("weather-condition",c.condition||"Conditions unavailable");
  text("weather-feels",`Feels like ${fmt(c.feels_like)}`);
  text("weather-hilo",`H ${fmt(day.high)} · L ${fmt(day.low)}`);
  text("weather-rain", day.precip_prob == null ? "Rain probability unavailable" : `Rain chance ${day.precip_prob}%`);
  const hours=$("weather-hours"); hours.replaceChildren();
  for (const h of (data.hourly||[]).slice(0,6)) {
    const d=new Date(h.time), label=Number.isNaN(d.getTime()) ? "—" : d.toLocaleTimeString(undefined,{hour:"numeric"});
    const div=node("div","weather-hour");
    div.append(node("span","widget-muted",label),node("strong",null,fmt(h.temp)),node("small",null,h.precip_prob == null ? "—" : `☂ ${h.precip_prob}%`));
    hours.append(div);
  }
  $("weather-results").hidden=false;
  text("weather-message",cached ? "Live weather unavailable. Showing last saved forecast." : "");
  text("weather-updated",cached ? `Saved forecast · ${data.updated ? new Date(data.updated).toLocaleString() : "time unknown"}` : stamp());
}
async function fetchWeather(force=false) {
  if (weatherBusy || !weatherPrefs.place || $("app").hidden) return;
  if (!force && Date.now()-lastWeatherCheck<10*60*1000) return;
  weatherBusy=true;
  text("weather-message","Refreshing weather…");
  try {
    const {latitude,longitude}=weatherPrefs.place;
    const d=await request(`/api/weather?lat=${encodeURIComponent(latitude)}&lon=${encodeURIComponent(longitude)}&units=${weatherPrefs.units}`);
    // Reject late responses from an earlier location or unit selection.
    if (weatherPrefs.place.latitude!==latitude || weatherPrefs.place.longitude!==longitude || weatherPrefs.units!==d.units) return;
    lastWeather=d; lastWeatherCheck=Date.now(); weatherView(d);
  } catch(e) {
    if (lastWeather) weatherView(lastWeather,true);
    else { $("weather-results").hidden=true; text("weather-message",e.message); }
    if (!lastWeather) text("weather-updated","Refresh failed");
  } finally { weatherBusy=false; }
}
function changePlace(place) {
  weatherPrefs.place=place; localStorage.setItem(WEATHER_KEY,JSON.stringify(weatherPrefs));
  $("weather-places").replaceChildren(); $("weather-location").value=place.name;
  lastWeather=null; lastWeatherCheck=0; $("weather-results").hidden=true;
  fetchWeather(true);
}
async function findPlaces(event) {
  event.preventDefault(); const query=$("weather-location").value.trim();
  const target=$("weather-places"); target.replaceChildren();
  if (query.length<2) return;
  text("weather-message","Searching locations…");
  try {
    const places=await request(`/api/weather/search?q=${encodeURIComponent(query)}`);
    text("weather-message",places.length ? "Choose the correct location:" : "No matches. Try city and state.");
    for (const place of places) {
      const button=node("button","place-choice",`${place.name}${place.region ? ` · ${place.region}` : ""}`);
      button.type="button"; button.addEventListener("click",()=>changePlace(place)); target.append(button);
    }
  } catch(e) { text("weather-message",e.message); }
}
function renderFollowed() {
  const box=$("sports-followed"); box.replaceChildren();
  followed.forEach(ref=>{
    const button=node("button","team-tag",`${ref.name} ×`);
    button.type="button"; button.setAttribute("aria-label",`Unfollow ${ref.name}`);
    button.addEventListener("click",()=>{
      followed=followed.filter(f=>`${f.league}:${f.id}`!==`${ref.league}:${ref.id}`);
      localStorage.setItem(SPORTS_KEY,JSON.stringify(followed));
      renderFollowed(); $("sports-results").replaceChildren(); lastScores=null;lastScoresCheck=0;fetchScores(true);
    }); box.append(button);
  });
  if (!followed.length) text("sports-message","Follow a team to see its live score, last result, or next game.");
}
function scoreView(games, cached=false) {
  const box=$("sports-results"); box.replaceChildren();
  if (!games.length) text("sports-message", "No scheduled games found for these teams. Check again later.");
  else text("sports-message",cached ? "Sports provider unavailable. Showing last saved scores." : "");
  games.forEach(game=>{
    const el=node("div","sports-game");
    const badge=cached ? "SAVED" : game.state==="in" ? "LIVE" : game.state==="post" ? "FINAL" : "UPCOMING";
    const head=node("div","game-meta"); head.append(node("span",null,game.league.toUpperCase()),node("strong",null,game.state==="pre" ? timeString(game.start) : `${badge} · ${game.detail||""}`));
    const sides=node("div","game-sides");
    for (const side of [game.away,game.home]) {
      if (!side) continue;
      const row=node("div","game-side");
      row.append(node("span",null,side.abbr||side.name||"Team"),node("strong",null,game.state==="pre" ? "—" : side.score ?? "—"));
      sides.append(row);
    }
    el.append(head,sides);box.append(el);
  });
  text("sports-updated",cached?"Saved scores · provider offline":stamp());
}
async function fetchScores(force=false) {
  if (scoresBusy || $("app").hidden) return;
  if (!followed.length) { $("sports-results").replaceChildren();text("sports-updated","No teams selected");return; }
  if (!force && Date.now()-lastScoresCheck<2*60*1000) return;
  scoresBusy=true; text("sports-message","Updating followed teams…");
  try {
    const refs=followed.slice(0,20).map(f=>`${f.league}:${f.id}`).join(",");
    const games=await request(`/api/scores?teams=${encodeURIComponent(refs)}`);
    lastScores=games;lastScoresCheck=Date.now();scoreView(games);
  } catch(e) {
    if (lastScores) scoreView(lastScores,true);
    else { $("sports-results").replaceChildren();text("sports-message",e.message); }
    if (!lastScores) text("sports-updated","Refresh failed");
  } finally { scoresBusy=false; }
}
async function loadLeagues() {
  const select=$("sports-league");
  try {
    const leagues=await request("/api/sports/leagues");
    select.replaceChildren();
    leagues.forEach(l=>{const opt=new Option(l.name,l.id);select.add(opt);});
    if (leagues.length) await loadTeams();
  } catch(e) { text("sports-message",e.message); }
}
async function loadTeams() {
  const league=$("sports-league").value, select=$("sports-team");
  select.replaceChildren(new Option("Loading teams…","")); selectedTeam=null;
  if (!league) return;
  try {
    const teams=await request(`/api/sports/teams?league=${encodeURIComponent(league)}`);
    if (league!==$("sports-league").value) return;
    select.replaceChildren(new Option("Choose a team…",""));
    teams.forEach(t=>select.add(new Option(t.name,t.id)));
    select.onchange=()=>{const t=teams.find(t=>t.id===select.value);selectedTeam=t?{...t,league}:null;};
  } catch(e) {select.replaceChildren(new Option("Teams unavailable","")); text("sports-message",e.message); }
}
function init() {
  $("weather-units").value=weatherPrefs.units;
  if(weatherPrefs.place) { $("weather-location").value=weatherPrefs.place.name; text("weather-message","Loading saved location…"); }
  $("weather-place-form").addEventListener("submit",findPlaces);
  $("weather-units").addEventListener("change",e=>{
    weatherPrefs.units=e.target.value;localStorage.setItem(WEATHER_KEY,JSON.stringify(weatherPrefs));
    lastWeather=null;lastWeatherCheck=0;$("weather-results").hidden=true;fetchWeather(true);
  });
  $("weather-refresh").addEventListener("click",()=>fetchWeather(true));
  $("sports-refresh").addEventListener("click",()=>fetchScores(true));
  $("sports-league").addEventListener("change",loadTeams);
  $("sports-add").addEventListener("click",()=>{
    if (!selectedTeam) {text("sports-message","Choose a league and team first.");return;}
    if (!followed.some(t=>t.league===selectedTeam.league&&t.id===selectedTeam.id)) followed.push(selectedTeam);
    followed=followed.slice(0,20);localStorage.setItem(SPORTS_KEY,JSON.stringify(followed));
    renderFollowed();lastScoresCheck=0;fetchScores(true);
  });
  renderFollowed();
  window.addEventListener("kiwi:dashboard",()=>{
    fetchWeather();fetchScores();
    if ($("sports-league").options.length===1 && $("sports-league").value==="") loadLeagues();
  });
  // Only refresh while the dashboard is visible. Failed requests can be retried immediately.
  window.setInterval(()=>{
    if (!$("app").hidden && !$("tab-dashboard").hidden) { fetchWeather();fetchScores(); }
  },30000);
  if (!$("app").hidden) {fetchWeather(true);fetchScores(true);loadLeagues();}
}
init();
