// Run: node server/tests/test_custom_widget_web_v42.cjs
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.join(__dirname,'../app/static/widgets.js'),'utf8');
function extract(startText,endText) {
  const start=source.indexOf(startText),end=source.indexOf(endText,start);
  assert(start>=0 && end>start);
  return source.slice(start,end);
}
const weatherScope=vm.createContext({});
vm.runInContext(extract('function weatherSvg(', 'function weatherView(')+'\nglobalThis.draw=weatherSvg;',weatherScope);
for(const code of [0,2,3,61,71,95]) {
  const svg=weatherScope.draw(code,true);
  assert.match(svg,/^<svg/);
  assert.match(svg,/<\/svg>$/);
}
const logoScope=vm.createContext({URL});
vm.runInContext(extract('function safeLogo(', 'function scoreTeam(')+'\nglobalThis.validate=safeLogo;',logoScope);
assert.equal(logoScope.validate('https://a.espncdn.com/i/teamlogos/nba/500/chi.png'),'https://a.espncdn.com/i/teamlogos/nba/500/chi.png');
for(const url of ['javascript:alert(1)','https://a.espncdn.com.bad-site.test/bad.png','http://a.espncdn.com/logo.png']) assert.equal(logoScope.validate(url),null);
console.log('PASS: weather condition illustrations and safe ESPN logo URLs');
