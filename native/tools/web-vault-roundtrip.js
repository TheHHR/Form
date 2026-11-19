// Runs the web app's own vault parsers + writers (www/app.js) on fixture files, so the native
// port can be tested byte-for-byte against the real thing.
// Usage: node tools/web-vault-roundtrip.js <fixtureDir> <outDir>
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const [fixtureDir, outDir] = process.argv.slice(2);
const root = path.resolve(__dirname, '../..');
const appLines = fs.readFileSync(path.join(root, 'www/app.js'), 'utf8').split('\n');
// Inclusive 1-based line ranges of www/app.js (3.6.3) holding the pure vault code.
const RANGES = [[6, 7], [29, 30], [33, 33], [35, 39], [67, 71], [78, 96], [127, 130], [335, 335], [341, 345],
  [592, 623], [837, 1396], [1418, 1478], [1482, 1496], [1688, 1702], [2387, 2387], [3299, 3338], [4449, 4463], [4673, 4678]];
const code = RANGES.map(([a, b]) => appLines.slice(a - 1, b).join('\n')).join('\n');

const exercisesJs = fs.readFileSync(path.join(root, 'www/data/exercises.js'), 'utf8');
const sandbox = { window: {}, console, Date, Math, JSON, Number, String, Object, Array, Set, Map, RegExp, Boolean };
vm.createContext(sandbox);
vm.runInContext(exercisesJs, sandbox);
vm.runInContext(`
  const EXERCISES = window.FORM_EXERCISES;
  const EXERCISE_BY_ID = new Map(EXERCISES.map(e => [String(e.id), e]));
  const VALID_EXERCISE_IDS = new Set(EXERCISE_BY_ID.keys());
  const CUSTOM_EXERCISES = [];
  function getExercise(id){ return EXERCISE_BY_ID.get(String(id)); }
  let activeAccent = 'lime';
  function applyAccent(){} function applyTabLabels(){} function persistExerciseTags(){} function persistCustomExercises(){}
  function render(){} function renderRoutineDrawer(){}
  const state = {
    fuel: { profile: { age: 22, sex: 'm', heightCm: 178, currentWeightKg: 75, startWeightKg: 75, goalWeightKg: 75, activity: 1.55, strategy: 250, proteinRate: 2, overrides: {} } },
    schedule: {0:'',1:'',2:'',3:'',4:'',5:'',6:''}, routines: [], saved: new Set(),
    progressPreferences: { firstDay: 1, defaultView: 'week' }, dashboard: {}, showWorkoutReminder: true,
    restPrefs: { enabled: true, sound: true, betweenSets: 60, betweenExercise: 90 },
    pillRowModes: { routine: 'pin', category: 'default', target: 'default', equipment: 'default', toggles: 'equipment', tagsHost: 'equipment' },
    showTabLabels: true, units: { weight: 'kg', distance: 'km', height: 'cm' }, exerciseTags: {}
  };
  ${code}
  this.run = (files) => {
    const cfg = parseConfigMd(files.config);
    registerConfigCustomExercises(cfg.customExercises);
    state.routines = parseRoutinesMd(files.routines);
    applyConfigToState(cfg);
    return {
      routines: routinesToMd(state.routines),
      meals: mealsToMd(parseMealsMd(files.meals)),
      training_logs: trainingLogsToMd(parseTrainingLogsMd(files.training_logs)),
      nutrition_diary: nutritionDiaryToMd(parseNutritionDiaryMd(files.nutrition_diary)),
      config: configToMd(),
    };
  };
`, sandbox);

const names = ['routines', 'meals', 'training_logs', 'nutrition_diary', 'config'];
const files = Object.fromEntries(names.map(n => [n, fs.readFileSync(path.join(fixtureDir, `${n}.md`), 'utf8')]));
const out = sandbox.run(files);
fs.mkdirSync(outDir, { recursive: true });
for (const n of names) fs.writeFileSync(path.join(outDir, `${n}.md`), out[n]);
console.log('wrote', names.length, 'files to', outDir);
