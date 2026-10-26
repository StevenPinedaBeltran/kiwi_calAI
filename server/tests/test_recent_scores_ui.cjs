// Run with: node server/tests/test_recent_scores_ui.cjs
// Exercises the exact browser cache-filter helper without needing a DOM.
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');
const source = fs.readFileSync(path.join(__dirname, '../app/static/widgets.js'), 'utf8');
const from = source.indexOf('const SCORE_WINDOW_MS');
const to = source.indexOf('function scoreView(', from);
assert(from >= 0 && to > from, 'recent-score helper must exist in widgets.js');
const scope = vm.createContext({Date});
vm.runInContext(source.slice(from, to) + '\nglobalThis.filter=recentScores;', scope);
const now = Date.parse('2026-10-25T18:00:00Z');
const event = (hours, state) => ({start: new Date(now + hours * 3600000).toISOString(), state});
const entries = [event(-169,'post'),event(-168,'post'),event(-20,'post'),event(-1,'in'),event(1,'pre'),event(-3,'pre'),{start:'invalid',state:'post'}];
const result = scope.filter(entries, now);
assert.equal(result.length, 3);
assert.deepEqual(result.map(g=>g.state).join(','), 'post,post,in');
assert.equal(scope.filter(entries, now + 8*24*3600000).length, 0);
console.log('PASS: web score window, expired caches, live, finals, future games, invalid dates');
