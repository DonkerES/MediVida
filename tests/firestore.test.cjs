const {before, after, beforeEach, test} = require('node:test');
const {readFileSync} = require('node:fs');
const {join} = require('node:path');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc, setDoc, getDoc, getDocs, collection, updateDoc, deleteDoc} = require('firebase/firestore');
let env;
const record = {kind:'MEDICINE', title:'Tratamiento de prueba', detail:'Dosis de prueba', times:'08:00,16:00', start:'2026-01-01', end:'', place:'', level:'Permitido', reminderMinutes:120, dayBefore:true, archived:false, stockRemaining:null, unitsPerDose:1, stockAlertAt:5, stockTotal:null, foodItems:''};
const profile = {name:'Usuario A', age:'', weight:'', blood:'', allergies:'', contact:'', phone:'', notifications:true};
const event = {recordId:'med-1', occurrence:'2026-09-20T08:00', status:'TAKEN', at:1};
const eventId = 'med-1_2026-09-20T08-00';
const db = uid => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
before(async () => { env = await initializeTestEnvironment({projectId:'demo-medivida', firestore:{rules:readFileSync(join(__dirname,'../firestore.rules'),'utf8')}}); });
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { await env.cleanup(); });
test('owner can create/read/update own profile and record', async () => {
  const a = db('alice'); await assertSucceeds(setDoc(doc(a,'users/alice'),profile));
  await assertSucceeds(setDoc(doc(a,'users/alice/records/med-1'),record));
  await assertSucceeds(getDocs(collection(a,'users/alice/records')));
  await assertSucceeds(updateDoc(doc(a,'users/alice/records/med-1'),{title:'Nuevo nombre'}));
});
test('current inventory fields and food categories are accepted for their owner', async () => {
  const a=db('alice');
  const stocked={...record,stockRemaining:18,unitsPerDose:2,stockAlertAt:5,stockTotal:30};
  await assertSucceeds(setDoc(doc(a,'users/alice/records/med-stock'),stocked));
  const permitted={...record,kind:'RESTRICTION',title:'Verduras y legumbres',detail:'Priorizar en cada comida',level:'Permitido',foodItems:'Espinaca, lentejas'};
  const restricted={...record,kind:'RESTRICTION',title:'Azúcares',level:'Restringido',foodItems:'Gaseosas, dulces'};
  await assertSucceeds(setDoc(doc(a,'users/alice/records/food-ok'),permitted));
  await assertSucceeds(setDoc(doc(a,'users/alice/records/food-no'),restricted));
});
test('legacy records without inventory fields can still be archived', async () => {
  const a=db('alice');
  const {stockRemaining,unitsPerDose,stockAlertAt,stockTotal,foodItems,...legacyRecord}=record;
  const ref=doc(a,'users/alice/records/legacy-med');
  await assertSucceeds(setDoc(ref,legacyRecord));
  await assertSucceeds(updateDoc(ref,{archived:true}));
});
test('anonymous requests cannot read or write health data', async () => {
  const a=db(); await assertFails(getDoc(doc(a,'users/alice')));
  await assertFails(setDoc(doc(a,'users/alice/records/med-1'),record));
});
test('another account cannot read, list, edit, delete or impersonate owner', async () => {
  const a=db('alice'), b=db('bob'); await setDoc(doc(a,'users/alice/records/med-1'),record);
  await assertFails(getDoc(doc(b,'users/alice/records/med-1')));
  await assertFails(getDocs(collection(b,'users/alice/records')));
  await assertFails(updateDoc(doc(b,'users/alice/records/med-1'),{title:'Intrusión'}));
  await assertFails(deleteDoc(doc(b,'users/alice/records/med-1')));
  await assertFails(setDoc(doc(b,'users/alice'),profile));
});
test('two accounts on one installation keep records in separate UID paths', async () => {
  const a=db('alice'), b=db('bob');
  await setDoc(doc(a,'users/alice/records/shared-local-id'),record);
  await setDoc(doc(b,'users/bob/records/shared-local-id'),{...record,title:'Tratamiento de Bob'});
  await assertSucceeds(getDoc(doc(a,'users/alice/records/shared-local-id')));
  await assertSucceeds(getDoc(doc(b,'users/bob/records/shared-local-id')));
  await assertFails(getDoc(doc(a,'users/bob/records/shared-local-id')));
  await assertFails(getDoc(doc(b,'users/alice/records/shared-local-id')));
});
test('unknown fields and admin privilege injection are rejected', async () => {
  const a=db('alice'); await assertFails(setDoc(doc(a,'users/alice'),{...profile,role:'admin'}));
  await assertFails(setDoc(doc(a,'users/alice/records/med-1'),{...record,owner:'bob'}));
  await assertFails(setDoc(doc(a,'admin/alice'),{role:'admin'}));
});
test('malformed or oversized record fields are rejected', async () => {
  const ref=doc(db('alice'),'users/alice/records/med-1');
  for (const patch of [{title:''},{title:'x'.repeat(101)},{times:'25:90'},{reminderMinutes:-1},{kind:'ADMIN'},{start:'bad-date'},{end:'2025-01-01'}]) await assertFails(setDoc(ref,{...record,...patch}));
});
test('invalid stock values and unknown current-schema fields are rejected', async () => {
  const a=db('alice');
  const ref=doc(a,'users/alice/records/med-stock');
  for (const patch of [
    {stockRemaining:-1},
    {stockRemaining:31,stockTotal:30},
    {unitsPerDose:0},
    {stockAlertAt:-1},
    {foodItems:'x'.repeat(2001)},
    {unexpectedField:true}
  ]) await assertFails(setDoc(ref,{...record,...patch}));
});
test('completion must reference own existing scheduled record', async () => {
  const a=db('alice'); await assertFails(setDoc(doc(a,`users/alice/completions/${eventId}`),event));
  await setDoc(doc(a,'users/alice/records/med-1'),record);
  await assertSucceeds(setDoc(doc(a,`users/alice/completions/${eventId}`),event));
  await assertFails(setDoc(doc(db('bob'),`users/bob/completions/${eventId}`),event));
  await assertFails(setDoc(doc(a,'users/alice/completions/med-1_2026-09-20T09-00'),{...event,occurrence:'2026-09-20T09:00'}));
});
test('completion is immutable and has a stable event identifier', async () => {
  const a=db('alice'); await setDoc(doc(a,'users/alice/records/med-1'),record);
  const ref=doc(a,`users/alice/completions/${eventId}`); await setDoc(ref,event);
  await assertSucceeds(setDoc(ref,event));
  await assertFails(updateDoc(ref,{status:'SKIPPED'})); await assertFails(deleteDoc(ref));
  await assertFails(setDoc(doc(a,'users/alice/completions/random'),event));
});
test('archiving preserves history and rejects new completions', async () => {
  const a=db('alice'); const ref=doc(a,'users/alice/records/med-1'); await setDoc(ref,record);
  await assertSucceeds(updateDoc(ref,{archived:true})); await assertFails(deleteDoc(ref));
  await assertFails(updateDoc(ref,{archived:false}));
  await assertFails(updateDoc(ref,{title:'Changed after archive'}));
  await assertFails(setDoc(doc(a,`users/alice/completions/${eventId}`),event));
});
