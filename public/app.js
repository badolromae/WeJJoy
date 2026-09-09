// WeJJoy web — main app. Talks to the same Firebase (RTDB/Storage/Auth) as the Android app.
import { firebaseConfig } from './firebase-config.js';
import { D } from './date.js';
import { Holidays } from './holidays.js';

// ---------------------------------------------------------------- constants
const STICKER_GROUPS = [
  ["둘이A", [
    ["ac_01","사랑해"],["ac_02","안녕"],["ac_03","최고"],["ac_04","삐짐"],["ac_05","미안"],["ac_06","화이팅"],["ac_07","감동"],["ac_08","기다려"],["ac_09","심쿵"],["ac_10","잘자"],["ac_11","맘마"],["ac_12","놀람"],["ac_13","분노"],["ac_14","부끄"],["ac_15","흥"],["ac_16","헉"],["ac_17","신나"],["ac_18","메롱"]
  ]],
  ["아내A", [
    ["aw_01","사랑해"],["aw_02","기다려"],["aw_03","화이팅"],["aw_04","안녕"],["aw_05","미안"],["aw_06","심쿵"],["aw_07","잘자"],["aw_08","맘마"],["aw_09","최고"],["aw_10","감동"],["aw_11","헉"],["aw_12","놀람"],["aw_13","부끄"],["aw_14","새침"],["aw_15","신나"],["aw_16","졸림"],["aw_17","흥"],["aw_18","삐짐"],["aw_19","분노"],["aw_20","축하"],["aw_21","메롱"]
  ]],
  ["남편A", [
    ["ah_01","사랑해"],["ah_02","기다려"],["ah_03","화이팅"],["ah_04","안녕"],["ah_05","미안"],["ah_06","심쿵"],["ah_07","무표정"],["ah_08","잘자"],["ah_09","굿"],["ah_10","맘마"],["ah_11","최고"],["ah_12","감동"],["ah_13","헉"],["ah_14","부끄"],["ah_15","신나"],["ah_16","졸림"],["ah_17","흥"],["ah_18","삐짐"],["ah_19","분노"],["ah_20","축하"],["ah_21","메롱"]
  ]],
  ["둘이B", [
    ["bc_01","안녕"],["bc_02","사랑해"],["bc_03","최고야"],["bc_04","보고싶어"],["bc_05","배고파"],["bc_06","헉"],["bc_07","놀람"],["bc_08","고마워"],["bc_09","미안해"],["bc_10","잘자"],["bc_11","좋은아침"],["bc_12","뭐먹지"],["bc_13","같이가자"],["bc_14","기다려줘"],["bc_15","힘내"],["bc_16","축하해"],["bc_17","우리부부"],["bc_18","꼭안아줘"],["bc_19","삐졌어"],["bc_20","행복해"],["bc_21","뽀뽀"]
  ]],
  ["아내B", [
    ["bw_01","안녕"],["bw_02","사랑해"],["bw_03","신나요"],["bw_04","감동이야"],["bw_05","헤헤"],["bw_06","메롱"],["bw_07","삐짐"],["bw_08","풀죽"],["bw_09","울지마"],["bw_10","졸려"],["bw_11","배고파"],["bw_12","최고"],["bw_13","부끄러워"],["bw_14","기다려"],["bw_15","고마워"],["bw_16","미안해"],["bw_17","보고싶어"],["bw_18","화났어"],["bw_19","행복해"],["bw_20","잘자"],["bw_21","뿅"]
  ]],
  ["남편B", [
    ["bh_01","안녕"],["bh_02","사랑해"],["bh_03","든든하지"],["bh_04","헉"],["bh_05","최고야"],["bh_06","배고파"],["bh_07","밥줘"],["bh_08","졸려"],["bh_09","힘내"],["bh_10","걱정마"],["bh_11","미안해"],["bh_12","고마워"],["bh_13","보고싶어"],["bh_14","삐졌어"],["bh_15","화해하자"],["bh_16","기다릴게"],["bh_17","출발"],["bh_18","잘자"],["bh_19","좋은아침"],["bh_20","아내최고"],["bh_21","뽀뽀"]
  ]]
];
const STICKER_SET = new Set(STICKER_GROUPS.flatMap(([g,items])=>items.map(([n])=>n)));
const WEB_VERSION = '3.1';
function firstInline(t){ const m=/\[\[s:([a-z0-9_]+)\]\]/.exec(t||''); return (m && STICKER_SET.has(m[1]))?m[1]:''; }
function renderRich(t, big){ let h=escapeHtml(t||''); const cls=big?'inline-emo-big':'inline-emo'; return h.replace(/\[\[s:([a-z0-9_]+)\]\]/g,(m,n)=>STICKER_SET.has(n)?`<img class="${cls}" src="${stickerSrc(n)}" alt="">`:''); }
const MOODS = ["😊","😄","😍","🥰","😌","😐","😢","😭","😠","😴","🤒","🎉","❤️","👍","🙏","💐","☕","🍚"];
const THEMES = [
  ["green","딥그린"],["blue","스카이블루"],["pink","연핑크"],["mono","블랙+그레이"],
  ["red","레드+주황"],["navy","군청"],["light_green","연그린"],["yellow","노랑"],
];
const stickerSrc = (name) => `stickers/${name}.png`;

// ---------------------------------------------------------------- state
const S = {
  uid: null,
  group: JSON.parse(localStorage.getItem('wj_group') || 'null'), // {code,nick,isOwner}
  entries: new Map(),   // uid -> entry (deletedAt==0 only)
  monthFirst: D.firstOfMonthOf(D.today()),
  selected: D.today(),
  entriesRef: null, entriesCb: null,
  edit: null,           // current editor entry
};

// ---------------------------------------------------------------- firebase
firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const db = firebase.database();
let storage = null; try { storage = firebase.storage(); } catch(_) {}

const $ = (id) => document.getElementById(id);
function toast(msg, ms=2200){ const b=$('statusBar'); b.textContent=msg; b.hidden=false; clearTimeout(b._t); b._t=setTimeout(()=>b.hidden=true, ms); }

// ---------------------------------------------------------------- theme
function applyTheme(){
  const t = localStorage.getItem('wj_theme') || 'green';
  const mode = localStorage.getItem('wj_mode') || 'auto';
  document.documentElement.dataset.theme = t;
  const dark = mode==='dark' || (mode==='auto' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.classList.toggle('dark', dark);
}
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);
applyTheme();

// ================================================================ calendar
function renderCalendar(){
  const first = S.monthFirst;
  $('monthTitle').textContent = D.formatMonthTitle(first);
  const gridStart = D.monthGridStart(first);
  const monthVal = D.ymd(first).m;
  const today = D.today();
  const counts = countByDay();
  const repMap = daySticker();

  let wd = '<div class="cal-wd">';
  ['일','월','화','수','목','금','토'].forEach((w,i)=>{ wd += `<div class="${i===0?'sun':i===6?'sat':''}">${w}</div>`; });
  wd += '</div><div class="cal-grid">';
  let cells = '';
  for (let i=0;i<42;i++){
    const ed = gridStart + i;
    const {m,d} = D.ymd(ed);
    const dow = D.dow(ed);
    const info = Holidays.info(ed);
    const inMonth = m === monthVal;
    const red = dow===0 || info.isHoliday;
    const cls = ['cell'];
    if (!inMonth) cls.push('out');
    if (red) cls.push('sun'); else if (dow===6) cls.push('sat');
    if (ed===today) cls.push('today');
    if (ed===S.selected) cls.push('sel');
    if ((counts.get(ed)||0)>0) cls.push('has');
    if (info.isHoliday) cls.push('holiday');
    const lunar = (d===1) ? D.lunarShort(ed) : '';
    const note = info.compact || '';
    cells += `<div class="${cls.join(' ')}" data-ed="${ed}">
      <div class="dnum">${d}</div>${repMap.get(ed)?`<img class="cell-emo" src="${stickerSrc(repMap.get(ed))}" alt="">`:'<div class="dot"></div>'}
      <div class="lunar">${lunar}</div><div class="note">${note}</div></div>`;
  }
  $('calendar').innerHTML = wd + cells + '</div>';
  $('calendar').querySelectorAll('.cell').forEach(c=>{
    c.onclick = ()=>{ selectDay(parseInt(c.dataset.ed,10)); };
  });
}

function countByDay(){
  const map = new Map();
  for (const e of S.entries.values()){
    const start = e.dateEpochDay;
    const end = (e.endDateEpochDay && e.endDateEpochDay > start) ? e.endDateEpochDay : start;
    for (let d=start; d<=end; d++) map.set(d, (map.get(d)||0)+1);
  }
  return map;
}

function daySticker(){
  const map=new Map();
  for (const e of S.entries.values()){
    const st=e.sticker||firstInline(e.title)||firstInline(e.content);
    if(!st) continue;
    const start=e.dateEpochDay; const end=(e.endDateEpochDay&&e.endDateEpochDay>start)?e.endDateEpochDay:start;
    for(let d=start; d<=end; d++){ if(!map.has(d)) map.set(d, st); }
  }
  return map;
}

function selectDay(ed){
  S.selected = ed;
  const mf = D.firstOfMonthOf(ed);
  if (mf !== S.monthFirst) S.monthFirst = mf;
  renderCalendar(); renderList();
}

// ================================================================ entry list
function entriesForDay(day){
  const out = [];
  for (const e of S.entries.values()){
    const start = e.dateEpochDay;
    const end = (e.endDateEpochDay && e.endDateEpochDay > start) ? e.endDateEpochDay : start;
    if (start<=day && end>=day) out.push(e);
  }
  out.sort((a,b)=>{
    const ka = a.dateEpochDay<day?0:(a.timeMinutes<0?2:1);
    const kb = b.dateEpochDay<day?0:(b.timeMinutes<0?2:1);
    if (ka!==kb) return ka-kb;
    const ta = a.timeMinutes<0?1e9:a.timeMinutes, tb = b.timeMinutes<0?1e9:b.timeMinutes;
    if (ta!==tb) return ta-tb;
    return (a.createdAt||0)-(b.createdAt||0);
  });
  return out;
}

function renderList(){
  const day = S.selected;
  const info = Holidays.info(day);
  const dow = D.dow(day);
  const sd = $('selDate');
  sd.textContent = D.formatFullDate(day);
  sd.style.color = (dow===0||info.isHoliday) ? 'var(--sun)' : (dow===6 ? 'var(--sat)' : 'var(--text)');
  const lunar = D.lunarLong(day);
  $('selLunar').textContent = lunar;
  const si = $('selInfo'); si.textContent = info.full; si.hidden = !info.full;
  si.style.color = info.isHoliday ? 'var(--sun)' : 'var(--muted)';

  const list = entriesForDay(day);
  const box = $('entryList');
  box.innerHTML = '';
  $('emptyMsg').hidden = list.length>0;
  for (const e of list) box.appendChild(entryCard(e, day));
  renderWeek();
}

// ================================================================ 주간 일정(주일정) 패널
const WK_DOW = ['일','월','화','수','목','금','토'];
function stripInline(t){ return (t||'').replace(/\[\[s:[a-z0-9_]+\]\]/g,'').replace(/\s+/g,' ').trim(); }
function weekStickerName(e){ return e.sticker || firstInline(e.title) || firstInline(e.content) || ''; }

function wkRow(e, d){
  const st = weekStickerName(e);
  const icon = st ? `<img class="wk-emo" src="${stickerSrc(st)}" alt="">`
             : (e.mood ? `<span class="wk-mood">${escapeHtml(e.mood)}</span>` : '');
  const title = stripInline(e.title) || (st ? '' : '(제목 없음)');
  return `<div class="wk-row">${icon}<span class="wk-title">${escapeHtml(title)}${multiDayLabel(e,d)}</span></div>`;
}

function renderWeek(){
  const box = $('weekArea'); if(!box) return;
  const sel = S.selected;
  const start = sel - D.dow(sel);   // 그 주의 일요일
  const today = D.today();
  let html = `<div class="week-head">주간 일정</div>`;
  for (let i=0;i<7;i++){
    const d = start + i;
    const info = Holidays.info(d);
    const dow = D.dow(d);
    const {m, d:dd} = D.ymd(d);
    const col = (dow===0||info.isHoliday) ? 'var(--sun)' : (dow===6 ? 'var(--sat)' : 'var(--text)');
    const dateLabel = `${m}/${dd} (${WK_DOW[dow]})` + (info.full ? `  ·  ${escapeHtml(info.full)}` : '');
    const list = entriesForDay(d);
    const rows = list.length ? list.map(e=>wkRow(e,d)).join('') : `<div class="wk-empty">기록 없음</div>`;
    html += `<div class="wk-day${d===sel?' sel':''}${d===today?' today':''}" data-ed="${d}">
      <div class="wk-date" style="color:${col}">${dateLabel}</div>
      <div class="wk-entries">${rows}</div>
    </div>`;
  }
  box.innerHTML = html;
  box.querySelectorAll('.wk-day').forEach(el=>{
    el.onclick = ()=> selectDay(Number(el.dataset.ed));
  });
}

function multiDayLabel(e, day){
  const start = e.dateEpochDay;
  const end = (e.endDateEpochDay && e.endDateEpochDay>start)?e.endDateEpochDay:start;
  if (end<=start) return '';
  const idx = day - start + 1; const total = end - start + 1;
  return `  (${idx}/${total}일차)`;
}

function entryCard(e, day){
  const el = document.createElement('div'); el.className='card';
  const end = (e.endDateEpochDay && e.endDateEpochDay>e.dateEpochDay)?e.endDateEpochDay:e.dateEpochDay;
  const timeTxt = D.formatTimeRangeShort(e.dateEpochDay, e.timeMinutes ?? -1, end, e.endTimeMinutes ?? -1);
  const moodHtml = e.sticker ? `<span class="mood"><img src="${stickerSrc(e.sticker)}" alt=""></span>`
                  : (e.mood ? `<span class="mood">${escapeHtml(e.mood)}</span>` : '');
  const tags = (e.tags && e.tags.length) ? `<div class="tags">${e.tags.map(t=>'#'+escapeHtml(t)).join(' ')}</div>` : '';
  const imp = Math.max(1, Math.min(100, e.importance||50));
  const content = (e.content||'').trim();
  el.innerHTML = `
    <div class="time"><div class="t">${escapeHtml(timeTxt)}</div></div>
    <div class="body">
      <div class="titleline">${moodHtml}<span class="title">${renderRich(e.title)||'(제목 없음)'}${multiDayLabel(e,day)}</span></div>
      ${content?`<div class="content">${renderRich(content)}</div>`:''}
      ${tags}
      <div class="impwrap"><div class="imp"><i style="width:${imp}%"></i></div><span class="imppct">${imp}%</span></div>
    </div>`;
  if (e._photoUrl){
    const img = document.createElement('img'); img.className='thumb'; img.src=e._photoUrl; el.appendChild(img);
  }
  el.onclick = ()=> openDetail(e);
  return el;
}

// ================================================================ 보기(읽기) 전체화면
function openDetail(e){
  S.detail = e;
  const end = (e.endDateEpochDay && e.endDateEpochDay>e.dateEpochDay)?e.endDateEpochDay:e.dateEpochDay;
  const timeTxt = D.formatTimeRangeShort(e.dateEpochDay, e.timeMinutes ?? -1, end, e.endTimeMinutes ?? -1).replace(/\n/g,' ');
  const dateTxt = D.formatFullDate(e.dateEpochDay) + (e.timeMinutes>=0 ? '  '+D.formatTime(e.timeMinutes) : '  종일');
  $('dtMeta').textContent = dateTxt + (e.authorNick?('  ·  '+e.authorNick):'');
  $('dtMood').innerHTML = e.sticker ? `<img src="${stickerSrc(e.sticker)}" alt="">`
                        : (e.mood ? escapeHtml(e.mood) : '');
  $('dtMood').hidden = !(e.sticker || e.mood);
  $('dtTitle').innerHTML = renderRich(e.title, false) || '(제목 없음)';
  const content = (e.content||'').trim();
  $('dtContent').innerHTML = content ? renderRich(content, true) : '<span class="dt-empty">(내용 없음)</span>';
  const tags = (e.tags && e.tags.length) ? e.tags.map(t=>'#'+escapeHtml(t)).join(' ') : '';
  $('dtTags').innerHTML = tags; $('dtTags').hidden = !tags;
  const imp = Math.max(1, Math.min(100, e.importance||50));
  $('dtImpBar').style.width = imp+'%'; $('dtImpPct').textContent = imp+'%';
  // 사진
  const pr = $('dtPhotos'); pr.innerHTML='';
  if (e._photoUrl){ const img=document.createElement('img'); img.src=e._photoUrl; pr.appendChild(img); }
  else if (storage && S.group && e.photos && e.photos.length){
    for (const nm of e.photos){ const img=document.createElement('img');
      storage.ref(`groups/${S.group.code}/photos/${nm}`).getDownloadURL().then(u=>img.src=u).catch(()=>{}); pr.appendChild(img); }
  }
  show('detailModal');
}

function escapeHtml(s){ return String(s).replace(/[&<>"]/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])); }

// ================================================================ RTDB sync
function startEntries(){
  stopEntries();
  if (!S.group) { S.entries.clear(); renderCalendar(); renderList(); return; }
  const ref = db.ref(`groups/${S.group.code}/entries`);
  const cb = ref.on('value', snap => {
    S.entries.clear();
    snap.forEach(ch => {
      const e = ch.val() || {};
      e.uid = e.uid || ch.key;
      if ((e.deletedAt||0) === 0) S.entries.set(e.uid, e);
    });
    renderCalendar(); renderList();
    loadPhotos();
  }, err => { toast('서버에서 내용을 받지 못했습니다.'); });
  S.entriesRef = ref; S.entriesCb = cb;
}
function stopEntries(){ if (S.entriesRef && S.entriesCb) S.entriesRef.off('value', S.entriesCb); S.entriesRef=null; S.entriesCb=null; }

async function loadPhotos(){
  if (!storage || !S.group) return;
  let changed = false;
  for (const e of S.entries.values()){
    if (e._photoUrl || !e.photos || !e.photos.length) continue;
    try {
      e._photoUrl = await storage.ref(`groups/${S.group.code}/photos/${e.photos[0]}`).getDownloadURL();
      changed = true;
    } catch(_){}
  }
  if (changed) renderList();
}

// ================================================================ editor
function buildTimeOptions(sel, withAllDay=true){
  let html = withAllDay ? '<option value="-1">종일</option>' : '';
  for (let t=0;t<24*60;t+=10){ html += `<option value="${t}">${D.formatTime(t)}</option>`; }
  sel.innerHTML = html;
}
function isoDate(ed){ const {y,m,d}=D.ymd(ed); return `${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`; }
function edFromIso(v){ const [y,m,d]=v.split('-').map(Number); return D.toEpoch(y,m,d); }

function openEditor(entry){
  if (!S.group){ toast('먼저 공유를 시작하거나 참여하세요.'); openShare(); return; }
  const isNew = !entry;
  S.edit = isNew ? {
    uid: (crypto.randomUUID ? crypto.randomUUID() : 'w'+Date.now()+Math.random().toString(16).slice(2)),
    dateEpochDay: S.selected, timeMinutes: -1, endDateEpochDay: -1, endTimeMinutes: -1,
    title:'', content:'', mood:'', sticker:'', importance:50, tags:[], photos:[],
    reminderAtMillis:0, createdAt:0, updatedAt:0, deletedAt:0, authorNick:S.group.nick||'',
  } : JSON.parse(JSON.stringify(entry));

  const e = S.edit;
  $('edTitle').textContent = isNew ? '새 일기' : '일기';
  $('edTitleInput').value = e.title||'';
  $('edContent').value = e.content||'';
  $('edDate').value = isoDate(e.dateEpochDay);
  buildTimeOptions($('edStart')); buildTimeOptions($('edEnd'));
  $('edStart').value = String(e.timeMinutes ?? -1);
  const hasEnd = (e.endDateEpochDay && e.endDateEpochDay>0) || (e.endTimeMinutes!=null && e.endTimeMinutes>=0);
  $('edUseEnd').checked = !!hasEnd; $('edEndRow').hidden = !hasEnd;
  $('edEndDate').value = isoDate((e.endDateEpochDay && e.endDateEpochDay>0)?e.endDateEpochDay:e.dateEpochDay);
  $('edEnd').value = String(e.endTimeMinutes ?? -1);
  $('edImp').value = e.importance||50; $('impVal').textContent = (e.importance||50)+'%';
  $('edTags').value = (e.tags||[]).join(', ');
  S.edit._repMode = false; S.edit._lastField = 'edContent';
  renderMoodPicker(); renderStickerModeRow(); renderStickerPicker(); renderPhotoRow();
  $('edDelete').hidden = isNew;
  show('editorModal');
}

function renderMoodPicker(){
  const row = $('moodRow'); row.innerHTML='';
  const none = document.createElement('button'); none.className='m'+(!S.edit.mood&&!S.edit.sticker?' sel':''); none.textContent='∅'; none.title='없음';
  none.onclick=()=>{ S.edit.mood=''; S.edit.sticker=''; renderMoodPicker(); renderStickerPicker(); };
  row.appendChild(none);
  MOODS.forEach(m=>{
    const b=document.createElement('button'); b.className='m'+(S.edit.mood===m?' sel':''); b.textContent=m;
    b.onclick=()=>{ S.edit.mood=m; S.edit.sticker=''; renderMoodPicker(); renderStickerPicker(); };
    row.appendChild(b);
  });
}
function renderStickerPicker(){
  const row=$('stickerRow'); row.innerHTML='';
  STICKER_GROUPS.forEach(([groupName, items])=>{
    const h=document.createElement('div'); h.className='sticker-group-label'; h.textContent=groupName;
    row.appendChild(h);
    const grid=document.createElement('div'); grid.className='sticker-grid';
    items.forEach(([name,label])=>{
      const b=document.createElement('button'); b.className='s'+(S.edit.sticker===name?' sel':''); b.title=label;
      b.innerHTML=`<img src="${stickerSrc(name)}" alt="${label}" loading="lazy">`;
      b.onclick=()=>{
        if(S.edit._repMode){
          // '대표로 지정'이 켜져 있으면 → 대표(달력·목록) 이모티콘
          S.edit.sticker=(S.edit.sticker===name?'':name); S.edit.mood=''; renderMoodPicker();
        } else {
          // 아니면 → 마지막으로 누른 글칸(제목/내용, 기본은 내용)에 넣는다
          insertToken(S.edit._lastField||'edContent', name);
        }
        renderStickerPicker();
      };
      grid.appendChild(b);
    });
    row.appendChild(grid);
  });
}
function renderStickerModeRow(){
  const row=$('stickerModeRow'); if(!row) return; row.innerHTML='';
  const b=document.createElement('button');
  b.textContent='⭐ 대표로 지정'; if(S.edit && S.edit._repMode) b.className='sel';
  b.onclick=()=>{ S.edit._repMode=!S.edit._repMode; renderStickerModeRow(); };
  row.appendChild(b);
}
function insertToken(id, name){
  const el=$(id); const tok=`[[s:${name}]]`;
  const st=(el.selectionStart!=null)?el.selectionStart:el.value.length;
  const en=(el.selectionEnd!=null)?el.selectionEnd:el.value.length;
  el.value = el.value.slice(0,st)+tok+el.value.slice(en);
  const pos=st+tok.length; el.focus(); try{ el.setSelectionRange(pos,pos); }catch(_){}
  toast(id.includes('Title') ? '제목에 이모티콘을 넣었어요' : '내용에 이모티콘을 넣었어요');
}
function renderPhotoRow(){
  const row=$('photoRow'); row.innerHTML='';
  (S.edit.photos||[]).forEach((name,idx)=>{
    const wrap=document.createElement('div'); wrap.className='ph';
    const img=document.createElement('img'); img.alt='';
    if (S.edit._urls && S.edit._urls[name]) img.src=S.edit._urls[name];
    else if (storage && S.group) storage.ref(`groups/${S.group.code}/photos/${name}`).getDownloadURL().then(u=>img.src=u).catch(()=>{});
    const rm=document.createElement('button'); rm.className='rm'; rm.textContent='×';
    rm.onclick=()=>{ S.edit.photos.splice(idx,1); renderPhotoRow(); };
    wrap.appendChild(img); wrap.appendChild(rm); row.appendChild(wrap);
  });
}

async function addPhoto(file){
  if (!storage){ toast('사진 공유가 아직 설정되지 않았습니다.'); return; }
  if (!S.group){ toast('공유 그룹이 필요합니다.'); return; }
  try {
    const name = `w_${Date.now()}_${Math.random().toString(16).slice(2)}.jpg`;
    toast('사진 올리는 중…', 4000);
    await storage.ref(`groups/${S.group.code}/photos/${name}`).put(file);
    S.edit.photos = S.edit.photos || []; S.edit.photos.push(name);
    S.edit._urls = S.edit._urls || {}; S.edit._urls[name] = URL.createObjectURL(file);
    renderPhotoRow(); toast('사진 추가됨');
  } catch(err){ toast('사진 업로드 실패 (Storage 설정 필요)'); }
}

async function saveEntry(){
  const e = S.edit; const now = Date.now();
  e.title = $('edTitleInput').value.trim();
  e.content = $('edContent').value;
  e.dateEpochDay = edFromIso($('edDate').value);
  e.timeMinutes = parseInt($('edStart').value,10);
  if ($('edUseEnd').checked){
    e.endDateEpochDay = edFromIso($('edEndDate').value);
    e.endTimeMinutes = parseInt($('edEnd').value,10);
  } else { e.endDateEpochDay = -1; e.endTimeMinutes = -1; }
  e.importance = parseInt($('edImp').value,10);
  e.tags = $('edTags').value.split(',').map(s=>s.trim()).filter(Boolean);
  e.authorNick = S.group.nick || '';
  if (!e.createdAt) e.createdAt = now;
  e.updatedAt = now; e.deletedAt = 0;
  const rec = {
    uid:e.uid, dateEpochDay:e.dateEpochDay, timeMinutes:e.timeMinutes,
    endDateEpochDay:e.endDateEpochDay, endTimeMinutes:e.endTimeMinutes,
    title:e.title, content:e.content, mood:e.mood||'', sticker:e.sticker||'',
    importance:e.importance, tags:e.tags, photos:e.photos||[],
    reminderAtMillis:e.reminderAtMillis||0, createdAt:e.createdAt, updatedAt:e.updatedAt,
    deletedAt:0, authorNick:e.authorNick,
  };
  try {
    await db.ref(`groups/${S.group.code}/entries/${e.uid}`).set(rec);
    hide('editorModal'); toast('저장했습니다.');
  } catch(err){ toast('저장 실패: '+(err.message||err)); }
}

async function deleteEntry(){
  const e = S.edit; if (!e) return;
  if (!confirm('이 일기를 삭제할까요?')) return;
  try {
    await db.ref(`groups/${S.group.code}/entries/${e.uid}`).update({ deletedAt: Date.now(), updatedAt: Date.now() });
    hide('editorModal'); toast('삭제했습니다.');
  } catch(err){ toast('삭제 실패: '+(err.message||err)); }
}

// ================================================================ settings
function openSettings(){
  const cur = localStorage.getItem('wj_theme')||'green';
  const mode = localStorage.getItem('wj_mode')||'auto';
  const mr=$('modeRow'); mr.innerHTML='';
  [['auto','자동'],['light','밝게'],['dark','어둡게']].forEach(([k,l])=>{
    const b=document.createElement('button'); b.textContent=l; if(mode===k)b.className='sel';
    b.onclick=()=>{ localStorage.setItem('wj_mode',k); applyTheme(); openSettings(); };
    mr.appendChild(b);
  });
  const tr=$('themeRow'); tr.innerHTML='';
  THEMES.forEach(([k,l])=>{
    const chip=document.createElement('div'); chip.className='theme-chip'+(cur===k?' sel':'');
    chip.innerHTML=`<div class="sw" data-theme="${k}" style="background:linear-gradient(135deg,var(--accent),var(--toolbar))"></div><div class="nm">${l}</div>`;
    // set the swatch's theme vars by temporarily applying
    const sw=chip.querySelector('.sw'); sw.style.background = `linear-gradient(135deg, ${themeAccent(k)}, ${themeToolbar(k)})`;
    chip.onclick=()=>{ localStorage.setItem('wj_theme',k); applyTheme(); openSettings(); };
    tr.appendChild(chip);
  });
  const sb=document.querySelector('#settingsModal .sheet-body');
  let vl=document.getElementById('webVerLabel'); if(!vl){ vl=document.createElement('div'); vl.id='webVerLabel'; vl.className='hint'; vl.style.marginTop='18px'; sb.appendChild(vl);} vl.textContent='WeJJoy 웹 버전 '+WEB_VERSION;
  show('settingsModal');
}
// read a theme's accent/toolbar from CSS by probing a hidden element
function themeVar(themeKey, varName){
  const probe=document.createElement('div'); probe.dataset.theme=themeKey; probe.style.display='none';
  document.body.appendChild(probe); const v=getComputedStyle(probe).getPropertyValue(varName).trim();
  probe.remove(); return v||'#888';
}
const themeAccent=(k)=>themeVar(k,'--accent');
const themeToolbar=(k)=>themeVar(k,'--toolbar');

// ================================================================ sharing (direct-join; owner-approval lands in the security step)
function newCode(){
  const chars='ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; let s='WJ-';
  const a=new Uint32Array(6); crypto.getRandomValues(a);
  for (let i=0;i<6;i++) s+=chars[a[i]%chars.length];
  return s;
}
function openShare(){
  const body=$('shareBody'); body.innerHTML='';
  if (!S.group){
    body.innerHTML = `
      <div class="share-sec">
        <div class="field-label">내 별명</div>
        <input id="shNick" class="field" placeholder="예: 조이" value="${escapeHtml(localStorage.getItem('wj_nick')||'')}">
      </div>
      <div class="share-sec">
        <button id="shCreate" class="primary-btn">새 공유 다이어리 만들기</button>
        <div class="hint">나 혼자 또는 배우자와 함께 쓸 공유 다이어리를 새로 만듭니다. 만든 사람이 관리자(소유자)가 됩니다.</div>
      </div>
      <div class="share-sec">
        <div class="field-label">초대 코드로 참여</div>
        <div class="row"><input id="shCode" class="field small" placeholder="WJ-XXXXXX"></div>
        <button id="shJoin" class="primary-btn" style="margin-top:8px">참여하기</button>
        <div class="hint">배우자가 앱에서 만든 그룹의 초대 코드를 넣으면, 같은 일기를 웹에서도 함께 볼 수 있습니다.</div>
      </div>`;
    $('shCreate').onclick = doCreate;
    $('shJoin').onclick = doJoin;
  } else {
    const owner = S.group.isOwner;
    body.innerHTML = `
      <div class="share-sec">
        <div class="field-label">초대 코드 (배우자에게 알려주세요)</div>
        <div class="code-box" id="shCodeBox">${escapeHtml(S.group.code)}</div>
        <button id="shCopy" class="mini-btn" style="margin-top:8px">코드 복사</button>
        <div class="hint">${owner?'나는 관리자입니다. 구성원을 내보낼 수 있어요.':'나는 공유자입니다.'}</div>
      </div>
      <div class="share-sec">
        <div class="field-label">구성원</div>
        <div id="shMembers"></div>
      </div>
      <div class="share-sec">
        <button id="shLeave" class="mini-btn danger">공유 그만두기 (나가기)</button>
      </div>`;
    $('shCopy').onclick = ()=>{ navigator.clipboard?.writeText(S.group.code); toast('코드를 복사했습니다.'); };
    $('shLeave').onclick = doLeave;
    watchMembers();
  }
  show('shareModal');
}

async function doCreate(){
  const nick = $('shNick').value.trim(); if(!nick){ toast('별명을 입력하세요.'); return; }
  localStorage.setItem('wj_nick', nick);
  const code = newCode(); const now = Date.now();
  try {
    await db.ref(`groups/${code}`).update({
      meta: { ownerUid: S.uid, createdAt: now },
      members: { [S.uid]: { nick, role:'owner', joinedAt: now } },
    });
    setGroup({ code, nick, isOwner:true });
    toast('공유 다이어리를 만들었습니다: '+code);
    openShare();
  } catch(err){ toast('만들기 실패: '+(err.message||err)); }
}
async function doJoin(){
  const nick = $('shNick').value.trim(); const code = $('shCode').value.trim().toUpperCase();
  if(!nick){ toast('별명을 입력하세요.'); return; }
  if(!code){ toast('초대 코드를 입력하세요.'); return; }
  localStorage.setItem('wj_nick', nick);
  try {
    const meta = await db.ref(`groups/${code}/meta`).get();
    if (!meta.exists()){ toast('그런 코드의 그룹이 없습니다.'); return; }
    const banned = await db.ref(`groups/${code}/banned/${S.uid}`).get();
    if (banned.exists()){ toast('관리자가 공유를 해제한 그룹입니다.'); return; }
    const isOwner = meta.val().ownerUid === S.uid;
    await db.ref(`groups/${code}/members/${S.uid}`).set({ nick, role: isOwner?'owner':'member', joinedAt: Date.now() });
    setGroup({ code, nick, isOwner });
    toast('그룹에 참여했습니다.'); openShare();
  } catch(err){ toast('참여 실패: '+(err.message||err)); }
}
async function doLeave(){
  if (!confirm('공유를 그만둘까요?')) return;
  try { await db.ref(`groups/${S.group.code}/members/${S.uid}`).remove(); } catch(_){}
  setGroup(null); toast('그룹에서 나왔습니다.'); openShare();
}
function watchMembers(){
  const box=$('shMembers'); if(!box) return;
  db.ref(`groups/${S.group.code}/members`).on('value', snap=>{
    if (!$('shMembers')) return;
    let html=''; const list=[];
    snap.forEach(ch=>{ list.push([ch.key, ch.val()]); });
    list.sort((a,b)=> (b[1].role==='owner')-(a[1].role==='owner') || (a[1].joinedAt||0)-(b[1].joinedAt||0));
    for (const [uid,m] of list){
      const me = uid===S.uid;
      const canKick = S.group.isOwner && !me && m.role!=='owner';
      html += `<div class="member"><div><span class="who">${escapeHtml(m.nick||'(이름 없음)')}</span>
        <span class="role">${m.role==='owner'?'관리자':'공유자'}${me?' · 나':''}</span></div>
        ${canKick?`<button class="mini-btn danger" data-kick="${uid}" data-nick="${escapeHtml(m.nick||'')}">내보내기</button>`:''}</div>`;
    }
    box.innerHTML = html || '<div class="hint">아직 참여한 사람이 없습니다.</div>';
    box.querySelectorAll('[data-kick]').forEach(b=>{
      b.onclick=async()=>{
        if(!confirm(`'${b.dataset.nick}' 님을 내보낼까요?`)) return;
        try{
          await db.ref(`groups/${S.group.code}`).update({
            [`members/${b.dataset.kick}`]: null,
            [`banned/${b.dataset.kick}`]: { nick:b.dataset.nick, at:Date.now() },
          });
          toast('내보냈습니다.');
        }catch(err){ toast('실패: '+(err.message||err)); }
      };
    });
  });
}
function setGroup(g){
  if (S.group){ try{ db.ref(`groups/${S.group.code}/members`).off(); }catch(_){} }
  S.group = g;
  if (g) localStorage.setItem('wj_group', JSON.stringify(g)); else localStorage.removeItem('wj_group');
  startEntries();
}

// ================================================================ search
function openSearch(){
  $('scResults').innerHTML=''; $('scInput').value=''; show('searchModal');
  setTimeout(()=>$('scInput').focus(),50);
  $('scInput').oninput = ()=>{
    const q=$('scInput').value.trim().toLowerCase(); const box=$('scResults'); box.innerHTML='';
    if(!q) return;
    const hits=[...S.entries.values()].filter(e=>
      (e.title||'').toLowerCase().includes(q) || (e.content||'').toLowerCase().includes(q) ||
      (e.tags||[]).some(t=>t.toLowerCase().includes(q))
    ).sort((a,b)=>(b.dateEpochDay-a.dateEpochDay)).slice(0,50);
    for (const e of hits){ const c=entryCard(e, e.dateEpochDay);
      c.onclick=()=>{ hide('searchModal'); selectDay(e.dateEpochDay); openDetail(e); }; box.appendChild(c); }
    if(!hits.length) box.innerHTML='<div class="hint">검색 결과가 없습니다.</div>';
  };
}

// ================================================================ modal helpers
function show(id){ $(id).hidden=false; }
function hide(id){ $(id).hidden=true; }
document.querySelectorAll('.modal').forEach(m=>{ m.addEventListener('click', ev=>{ if(ev.target===m) m.hidden=true; }); });

// ================================================================ wire up
$('btnPrev').onclick=()=>{ S.monthFirst=D.addMonths(S.monthFirst,-1); renderCalendar(); };
$('btnNext').onclick=()=>{ S.monthFirst=D.addMonths(S.monthFirst,+1); renderCalendar(); };
$('btnToday').onclick=()=>{ S.monthFirst=D.firstOfMonthOf(D.today()); selectDay(D.today()); };
$('monthTitle').onclick=()=>{ const v=prompt('이동할 년/월 (예: 2026-09)', isoDate(S.monthFirst).slice(0,7)); if(v){ const [y,m]=v.split('-').map(Number); if(y&&m){ S.monthFirst=D.firstOfMonth(y,m); renderCalendar(); } } };
$('fabAdd').onclick=()=> openEditor(null);
$('btnSettings').onclick=openSettings; $('setClose').onclick=()=>hide('settingsModal');
$('btnShare').onclick=openShare; $('shClose').onclick=()=>hide('shareModal');
$('btnSearch').onclick=openSearch; $('scClose').onclick=()=>hide('searchModal');
$('dtClose').onclick=()=>hide('detailModal');
$('dtEdit').onclick=()=>{ hide('detailModal'); if(S.detail) openEditor(S.detail); };
$('edCancel').onclick=()=>hide('editorModal'); $('edSave').onclick=saveEntry; $('edDelete').onclick=deleteEntry;
// 제목/내용 칸을 누르면 그 칸을 기억하고 '대표로 지정'은 끈다 → 이후 이모티콘 탭은 그 칸에 들어간다
['edTitleInput','edContent'].forEach(id=>{ const el=$(id); if(el) el.addEventListener('focus',()=>{
  if(S.edit){ S.edit._lastField=id; S.edit._repMode=false; renderStickerModeRow(); }
}); });
$('edImp').oninput=()=>$('impVal').textContent=$('edImp').value+'%';
$('edUseEnd').onchange=()=>$('edEndRow').hidden=!$('edUseEnd').checked;
$('edAddPhoto').onclick=()=>$('edPhoto').click();
$('edPhoto').onchange=(e)=>{ const f=e.target.files[0]; if(f) addPhoto(f); e.target.value=''; };

// swipe months on calendar (touch)
(function(){
  const el=$('calendar'); let x0=0,y0=0,sw=false;
  el.addEventListener('touchstart',e=>{ const t=e.touches[0]; x0=t.clientX; y0=t.clientY; sw=false; },{passive:true});
  el.addEventListener('touchmove',e=>{ const t=e.touches[0]; if(Math.abs(t.clientX-x0)>50 && Math.abs(t.clientX-x0)>Math.abs(t.clientY-y0)) sw=true; },{passive:true});
  el.addEventListener('touchend',e=>{ if(!sw) return; const dx=e.changedTouches[0].clientX-x0; S.monthFirst=D.addMonths(S.monthFirst, dx<0?1:-1); renderCalendar(); });
})();

// ================================================================ boot
renderCalendar(); renderList();
auth.signInAnonymously().catch(err=>toast('로그인 실패: '+(err.message||err)));
auth.onAuthStateChanged(u=>{
  if (!u) return;
  S.uid = u.uid;
  if (S.group) startEntries();
});
