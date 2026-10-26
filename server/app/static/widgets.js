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
let weatherSequence=0;
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
// Fixed vector illustrations keep the forecast readable in light and dark mode.
function weatherSvg(code, isDay=true) {
  const n=Number(code);
  const cloud='<path d="M17 48h55a16 16 0 0 0 1-32 24 24 0 0 0-45-2 18 18 0 0 0-11 34Z" fill="#e3ebf5" stroke="#b7cbe0" stroke-width="2"/>';
  const sun='<circle cx="26" cy="22" r="13" fill="#ffc66b"/><g stroke="#ffc66b" stroke-width="3" stroke-linecap="round"><path d="M26 2v6M26 36v6M6 22h6M40 22h6M12 8l4 4M40 8l-4 4"/></g>';
  const moon='<path d="M17 8a19 19 0 1 0 23 27A18 18 0 0 1 17 8Z" fill="#cfdaf4"/>';
  let picture;
  if (n===0 || n===1) picture=isDay?sun:moon;
  else if ([2,3,45,48].includes(n)) picture=(isDay?sun:moon)+cloud;
  else if ((n>=51 && n<=67) || (n>=80 && n<=82)) picture=cloud+'<g stroke="#7dcaff" stroke-width="4" stroke-linecap="round"><path d="m25 56-5 10m25-10-5 10m25-10-5 10"/></g>';
  else if ((n>=71 && n<=77) || n===85 || n===86) picture=cloud+'<g fill="#dbeaff"><circle cx="25" cy="60" r="3"/><circle cx="45" cy="67" r="3"/><circle cx="65" cy="60" r="3"/></g>';
  else if (n>=95) picture=cloud+'<path d="m47 51-12 16h12l-4 13 19-23H49Z" fill="#ffd06d"/>';
  else picture=cloud;
  return `<svg viewBox="0 0 100 86" role="presentation" focusable="false" aria-hidden="true">${picture}</svg>`;
}
function weatherView(data, cached=false) {
  const c=data.current||{}, day=data.daily?.[0]||{}, tomorrow=data.daily?.[1];
  const unit=data.labels?.temp || (data.units === "metric" ? "°C" : "°F");
  const speedUnit=data.labels?.speed || (data.units === "metric" ? "km/h" : "mph");
  const fmt=v=>v==null?"—":`${Math.round(v)}${unit}`;
  text("weather-city", weatherPrefs.place ? [weatherPrefs.place.name,weatherPrefs.place.region].filter(Boolean).join(", ") : "Selected location");
  text("weather-temperature",fmt(c.temp));
  text("weather-condition",c.condition||"Conditions unavailable");
  text("weather-feels",`Feels like ${fmt(c.feels_like)}`);
  text("weather-hilo",`H ${fmt(day.high)} · L ${fmt(day.low)}`);
  text("weather-rain",day.precip_prob==null?"—":`${Math.round(day.precip_prob)}%`);
  text("weather-humidity",c.humidity==null?"—":`${Math.round(c.humidity)}%`);
  text("weather-wind",c.wind_speed==null?"—":`${Math.round(c.wind_speed)} ${speedUnit}`);
  $("weather-art").innerHTML=weatherSvg(c.code,c.is_day!==false);
  $("weather-results").classList.toggle("weather-night",c.is_day===false);
  const hours=$("weather-hours");hours.replaceChildren();
  for(const h of (data.hourly||[]).slice(0,6)) {
    const d=new Date(h.time);
    const label=Number.isNaN(d.getTime())?"—":d.toLocaleTimeString(undefined,{hour:"numeric"});
    const div=node("div","weather-hour");
    const art=node("div","weather-small-art");art.innerHTML=weatherSvg(h.code,h.is_day!==false);
    div.append(node("span","widget-muted",label),art,node("strong",null,fmt(h.temp)));
    hours.append(div);
  }
  const next=$("weather-tomorrow");next.replaceChildren();next.hidden=!tomorrow;
  if(tomorrow) next.append(node("strong",null,"Tomorrow"),node("span",null,tomorrow.condition||"Forecast"),node("strong",null,`H ${fmt(tomorrow.high)} · L ${fmt(tomorrow.low)}`));
  $("weather-results").hidden=false;
  text("weather-message",cached?"Forecast unavailable. Showing previously saved weather.":"");
  const updated=data.updated?new Date(data.updated):null;
  text("weather-age",cached?"SAVED":updated&&!Number.isNaN(updated.getTime())?`Updated ${updated.toLocaleTimeString(undefined,{hour:"numeric",minute:"2-digit"})}`:"Updated now");
  text("weather-updated",cached?"Saved forecast · provider offline":stamp());
}
async function fetchWeather(force=false) {
  if (weatherBusy || !weatherPrefs.place || $("app").hidden) return;
  if (!force && Date.now()-lastWeatherCheck<10*60*1000) return;
  weatherBusy=true;
  const sequence=weatherSequence;
  text("weather-message","Refreshing weather…");
  try {
    const {latitude,longitude}=weatherPrefs.place;
    const d=await request(`/api/weather?lat=${encodeURIComponent(latitude)}&lon=${encodeURIComponent(longitude)}&units=${weatherPrefs.units}`);
    // Reject late responses from an earlier location or unit selection.
    if (sequence!==weatherSequence || weatherPrefs.place.latitude!==latitude || weatherPrefs.place.longitude!==longitude || weatherPrefs.units!==d.units) return;
    lastWeather=d; lastWeatherCheck=Date.now(); weatherView(d);
  } catch(e) {
    if (sequence!==weatherSequence) return;
    if (lastWeather) weatherView(lastWeather,true);
    else { $("weather-results").hidden=true; text("weather-message",e.message); }
    if (!lastWeather) text("weather-updated","Refresh failed");
  } finally { weatherBusy=false; if (sequence!==weatherSequence && weatherPrefs.place) fetchWeather(true); }
}
function changePlace(place) {
  weatherPrefs.place=place; localStorage.setItem(WEATHER_KEY,JSON.stringify(weatherPrefs));
  $("weather-places").replaceChildren(); $("weather-location").value=place.name;
  weatherSequence++;lastWeather=null;lastWeatherCheck=0;$("weather-results").hidden=true;
  // A previous request can still finish; retry immediately after it exits.
  if (!weatherBusy) fetchWeather(true);
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
  if (!followed.length) text("sports-message","Follow a team to see its most recent score from the past 7 days.");
}
// Filter stored responses too: results expire even when the server is offline.
const SCORE_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
function recentScores(games, now=Date.now()) {
  return (games||[]).filter(game=>{
    if (!game || !["in","post"].includes(game.state)) return false;
    const started = new Date(game.start).getTime();
    return Number.isFinite(started) && started <= now && now-started <= SCORE_WINDOW_MS;
  });
}
function safeLogo(url) {
  try {
    const parsed=new URL(url);
    return parsed.protocol==="https:" && (parsed.hostname==="espncdn.com"||parsed.hostname.endsWith(".espncdn.com")) ? parsed.href : null;
  } catch { return null; }
}
function scoreTeam(side, home, game) {
  const team=node("div",`score-team ${home?"is-home":"is-away"}`);
  const emblem=node("div","score-emblem");
  const fallback=node("span","score-initials",side?.abbr||side?.name?.slice(0,3)||"—");
  const url=safeLogo(side?.logo);
  if(url) {
    const img=node("img","score-logo");img.alt="";img.loading="lazy";img.referrerPolicy="no-referrer";
    img.addEventListener("error",()=>{img.remove();emblem.append(fallback);},{once:true});
    img.src=url;emblem.append(img);
  } else emblem.append(fallback);
  const label=node("div","score-team-label");
  label.append(node("strong",null,side?.abbr||"TEAM"),node("span",null,side?.name||"Unknown"));
  team.append(emblem,label);return team;
}
function scoreView(games, cached=false) {
  const box=$("sports-results");box.replaceChildren();
  const recent=recentScores(games);
  text("sports-message",recent.length?(cached?"Provider unavailable — showing saved results from the past week.":""):(followed.length?"No games played in the past 7 days for your followed teams.":"Follow a team to see recent scores."));
  recent.forEach(game=>{
    const card=node("article","scoreboard-card");
    const meta=node("div","scoreboard-meta");
    const isLive=game.state==="in"&&!cached;
    const badge=node("span",`score-badge ${isLive?"score-live":""}`,cached?"SAVED":isLive?"● LIVE":"FINAL");
    const d=new Date(game.start);
    const date=Number.isNaN(d.getTime())?"":d.toLocaleDateString(undefined,{month:"short",day:"numeric"});
    meta.append(node("span", "score-league", String(game.league||"").toUpperCase()),badge,node("span","score-date",date));
    const sides=node("div","scoreboard-versus");
    const center=node("div","score-main");
    center.append(node("strong",null,`${game.away?.score??"—"} – ${game.home?.score??"—"}`),node("small",null,cached?"Last saved result":(game.detail||"Recent game")));
    sides.append(scoreTeam(game.away,false,game),center,scoreTeam(game.home,true,game));
    card.append(meta,sides);box.append(card);
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
    weatherSequence++;lastWeather=null;lastWeatherCheck=0;$("weather-results").hidden=true;
    if (!weatherBusy) fetchWeather(true);
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
