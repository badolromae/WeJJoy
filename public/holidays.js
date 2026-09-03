// Korean holidays + memorial days — ported from the WeJJoy app's KoreanHolidays.kt.
import { D } from './date.js';
import { LUNAR } from './lunar.js';

const EXTRA = { "2028-04-12": "국회의원선거일" };
const SUB_SATSUN = new Set(["삼일절","어린이날","부처님오신날","제헌절","광복절","개천절","한글날","성탄절"]);
const SUB_SUNONLY = new Set(["설날","설날 연휴","추석","추석 연휴"]);

const cache = new Map();

function lunar(y,m,d){ return LUNAR.toEpochDay(y,m,d,false); }
function solar(y,m,d){ return D.toEpoch(y,m,d); }

// n>0: nth weekday; n<0: last weekday. dow: 0=Sun..6=Sat
function nthDow(y, month, dow, n) {
  if (n > 0) {
    const first = D.toEpoch(y, month, 1);
    const shift = (dow - D.dow(first) + 7) % 7;
    const ed = first + shift + (n - 1) * 7;
    return D.ymd(ed).m === month ? ed : null;
  } else {
    const last = D.toEpoch(y, month + 1, 1) - 1;
    const shift = (D.dow(last) - dow + 7) % 7;
    return last - shift;
  }
}

function build(targetYear) {
  const H = new Map(), O = new Map();
  const addH = (ed, name) => { if (ed==null) return; const l = H.get(ed) || []; if (!l.includes(name)) l.push(name); H.set(ed, l); };
  const addE = (ed, name) => { if (ed==null) return; const l = O.get(ed) || []; if (!l.includes(name)) l.push(name); O.set(ed, l); };

  for (let y = targetYear - 1; y <= targetYear + 1; y++) {
    // public holidays
    addH(solar(y,1,1),"신정"); addH(solar(y,3,1),"삼일절"); addH(solar(y,5,5),"어린이날");
    addH(solar(y,6,6),"현충일"); if (y>=2026) addH(solar(y,7,17),"제헌절");
    addH(solar(y,8,15),"광복절"); addH(solar(y,10,3),"개천절"); addH(solar(y,10,9),"한글날");
    addH(solar(y,12,25),"성탄절");
    const seol = lunar(y,1,1); if (seol!=null){ addH(seol-1,"설날 연휴"); addH(seol,"설날"); addH(seol+1,"설날 연휴"); }
    addH(lunar(y,4,8),"부처님오신날");
    const chu = lunar(y,8,15); if (chu!=null){ addH(chu-1,"추석 연휴"); addH(chu,"추석"); addH(chu+1,"추석 연휴"); }
    for (const [iso,name] of Object.entries(EXTRA)) { const [yy,mm,dd]=iso.split('-').map(Number); if (yy===y) addH(solar(yy,mm,dd),name); }
    // folk (lunar)
    addE(lunar(y,1,15),"정월대보름"); addE(lunar(y,3,3),"삼짇날"); addE(lunar(y,5,5),"단오");
    addE(lunar(y,6,15),"유두"); addE(lunar(y,7,7),"칠석"); addE(lunar(y,7,15),"백중"); addE(lunar(y,9,9),"중양절");
    const last = LUNAR.lastDayOfMonth(y,12,false); if (last!=null) addE(LUNAR.toEpochDay(y,12,last,false),"섣달그믐");
    // memorial / life days
    const M = [
      [2,14,"밸런타인데이"],[2,28,"2·28 민주운동 기념일"],[3,3,"납세자의 날"],[3,8,"3·8 민주의거 기념일"],
      [3,14,"화이트데이"],[3,15,"3·15 의거 기념일"],[4,1,"수산인의 날"],[4,3,"4·3 희생자 추념일"],
      [4,5,"식목일"],[4,7,"보건의 날"],[4,11,"대한민국 임시정부 수립 기념일"],[4,16,"국민안전의 날"],
      [4,19,"4·19 혁명 기념일"],[4,20,"장애인의 날"],[4,21,"과학의 날"],[4,22,"정보통신의 날"],
      [4,25,"법의 날"],[4,28,"충무공 이순신 탄신일"],[5,1,"근로자의 날"],[5,8,"어버이날"],
      [5,10,"유권자의 날"],[5,11,"동학농민혁명 기념일"],[5,15,"스승의 날"],[5,18,"5·18 민주화운동 기념일"],
      [5,19,"발명의 날"],[5,21,"부부의 날"],[5,25,"방재의 날"],[5,27,"우주항공의 날"],[5,31,"바다의 날"],
      [6,1,"의병의 날"],[6,5,"환경의 날"],[6,9,"구강보건의 날"],[6,10,"6·10 민주항쟁 기념일"],
      [6,25,"6·25 전쟁일"],[6,28,"철도의 날"],[7,11,"인구의 날"],[7,14,"북한이탈주민의 날"],
      [8,8,"섬의 날"],[9,7,"사회복지의 날"],[9,7,"푸른 하늘의 날"],[9,10,"자살예방의 날"],[9,21,"치매극복의 날"],
      [10,1,"국군의 날"],[10,2,"노인의 날"],[10,5,"세계 한인의 날"],[10,8,"재향군인의 날"],[10,15,"스포츠의 날"],
      [10,16,"부마민주항쟁 기념일"],[10,21,"경찰의 날"],[10,24,"국제연합일"],[10,28,"교정의 날"],
      [10,29,"지방자치 및 균형발전의 날"],[11,3,"학생독립운동기념일"],[11,9,"소방의 날"],[11,11,"농업인의 날"],
      [11,11,"빼빼로데이"],[11,17,"순국선열의 날"],[12,3,"소비자의 날"],[12,5,"무역의 날"],
      [12,24,"크리스마스이브"],[12,27,"원자력 안전 및 진흥의 날"],
    ];
    for (const [mm,dd,name] of M) addE(solar(y,mm,dd),name);
    addE(nthDow(y,3,3,3),"상공의 날"); addE(nthDow(y,3,5,4),"서해수호의 날"); addE(nthDow(y,4,5,1),"예비군의 날");
    addE(nthDow(y,4,5,4),"순직의무군경의 날"); addE(nthDow(y,5,1,3),"성년의 날"); addE(nthDow(y,7,3,2),"정보보호의 날");
    if (y<2026) addE(solar(y,7,17),"제헌절");
    addE(nthDow(y,9,6,3),"청년의 날"); addE(nthDow(y,10,6,3),"문화의 날"); addE(nthDow(y,10,2,-1),"금융의 날");
  }

  // substitute holidays
  const base = new Set(H.keys());
  const days = [...H.keys()].sort((a,b)=>a-b);
  const added = new Map();
  for (const ed of days) {
    const names = H.get(ed) || [];
    const dw = D.dow(ed);
    for (const name of [...names]) {
      if (name.startsWith("대체공휴일")) continue;
      let need = false;
      if (SUB_SUNONLY.has(name)) need = dw === 0;
      else if (SUB_SATSUN.has(name)) need = dw === 6 || dw === 0 || (name === "어린이날" && names.length > 1);
      if (!need) continue;
      let n = ed + 1, found = false;
      for (let step = 0; step < 30; step++) {
        const isSun = D.dow(n) === 0;
        const taken = base.has(n) || added.has(n);
        if (!isSun && !taken) { found = true; break; }
        n++;
      }
      if (found) added.set(n, `대체공휴일(${name})`);
    }
  }
  for (const [ed,name] of added) { const yy = D.ymd(ed).y; if (yy>=targetYear-1 && yy<=targetYear+1) addH(ed,name); }

  // collect into target year
  const out = new Map();
  const keys = new Set([...H.keys(), ...O.keys()]);
  for (const ed of keys) {
    if (D.ymd(ed).y !== targetYear) continue;
    out.set(ed, { holidays: H.get(ed) || [], others: O.get(ed) || [] });
  }
  return out;
}

function yearMap(y) { if (!cache.has(y)) cache.set(y, build(y)); return cache.get(y); }

export const Holidays = {
  info(ed) {
    const y = D.ymd(ed).y;
    const di = yearMap(y).get(ed);
    if (!di) return { holidays:[], others:[], isHoliday:false, short:'', compact:'', full:'' };
    const short = di.holidays[0] || di.others[0] || '';
    let compact = short;
    if (!short) compact = '';
    else if (short.startsWith('대체공휴일')) compact = '대체휴일';
    else if (short === '설날 연휴') compact = '설연휴';
    else if (short === '추석 연휴') compact = '추석연휴';
    else if (short.startsWith('부처님')) compact = '석가탄신';
    return {
      holidays: di.holidays, others: di.others,
      isHoliday: di.holidays.length > 0,
      short, compact,
      full: [...di.holidays, ...di.others].join(' · '),
    };
  },
};
