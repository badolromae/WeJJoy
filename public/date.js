// Date helpers mirroring the WeJJoy app's DateUtil.kt (epochDay = days since 1970-01-01, UTC).
import { LUNAR } from './lunar.js';

const DAY = 86400000;
const WD = ['일','월','화','수','목','금','토'];

export const D = {
  today() { return Math.floor(Date.now() / DAY); },
  ymd(ed) {
    const dt = new Date(ed * DAY);
    return { y: dt.getUTCFullYear(), m: dt.getUTCMonth() + 1, d: dt.getUTCDate() };
  },
  toEpoch(y, m, d) { return Math.floor(Date.UTC(y, m - 1, d) / DAY); },
  dow(ed) { return new Date(ed * DAY).getUTCDay(); },       // 0=Sun..6=Sat
  firstOfMonth(y, m) { return D.toEpoch(y, m, 1); },
  firstOfMonthOf(ed) { const {y,m} = D.ymd(ed); return D.toEpoch(y, m, 1); },
  addMonths(firstEd, delta) {
    const {y,m} = D.ymd(firstEd);
    const ny = y + Math.floor((m - 1 + delta) / 12);
    const nm = ((m - 1 + delta) % 12 + 12) % 12 + 1;
    return D.toEpoch(ny, nm, 1);
  },
  weekStart(ed) { return ed - D.dow(ed); },
  monthGridStart(firstEd) { return D.weekStart(firstEd); },
  wd(ed) { return WD[D.dow(ed)]; },
  formatFullDate(ed) { const {y,m,d} = D.ymd(ed); return `${y}년 ${m}월 ${d}일 (${D.wd(ed)})`; },
  formatMonthTitle(firstEd) { const {y,m} = D.ymd(firstEd); return `${y}년 ${m}월`; },
  formatShortDate(ed) { const {m,d} = D.ymd(ed); return `${m}/${d}`; },
  formatTime(t) {
    if (t < 0) return '종일';
    const h = Math.floor(t / 60), mi = t % 60;
    const ampm = h < 12 ? '오전' : '오후';
    let h12 = h % 12; if (h12 === 0) h12 = 12;
    return `${ampm} ${h12}:${String(mi).padStart(2,'0')}`;
  },
  formatTimeRangeShort(sd, st, ed2, et) {
    if (ed2 > sd) return `${D.formatShortDate(sd)} ~\n${D.formatShortDate(ed2)}`;
    if (st < 0) return '종일';
    if (et >= 0 && et !== st) return `${D.formatTime(st)}\n~ ${D.formatTime(et)}`;
    return D.formatTime(st);
  },
  lunarShort(ed) { return LUNAR.shortLabel(ed); },
  lunarLong(ed) { return LUNAR.longLabel(ed); },
};
