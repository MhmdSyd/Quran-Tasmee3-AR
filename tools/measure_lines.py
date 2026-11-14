import os,sys,sqlite3,json,statistics as st
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import quran_data as q
from PIL import ImageFont
F=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),'app/src/main/res/font/uthmanic_hafs.ttf')
f=ImageFont.truetype(F,200,layout_engine=ImageFont.Layout.RAQM)
def W(t): return f.getlength(t,direction='rtl',language='ar')/200
verses=q.load_verses(); adjs=q.load_adjustments()
words,vidx,total=q.build_words(verses,adjs)
byid={}
for (s,a),ws in words.items():
    for w in ws: byid[w['id']]=w
cache={}
def ww(w):
    t=w['text']
    if t not in cache: cache[t]=W(t)
    return cache[t]
sp=W(' ')
L=sqlite3.connect(q.INDEX)
rows=list(L.execute("select page,line,type,centered,first_word_id,last_word_id from lines order by page,line"))
res=[]
for p,l,t,c,a,b in rows:
    if t!='ayah': continue
    ws=[byid[i] for i in range(a,b+1) if i in byid]
    n=len(ws); sw=sum(ww(w) for w in ws)
    res.append((p,l,c,n,sw))
json.dump(res,open('/tmp/lines.json','w'))
print('space em',sp, 'nbsp',W('\u00a0'))
# normal lines: p>=3 and not centered
norm=[r for r in res if not r[2]]
import collections
for gap in (0.0,0.10,0.15,0.25):
    mx=max(norm,key=lambda r:r[4]+(r[3]-1)*gap); print('gap',gap,'max line em',round(mx[4]+(mx[3]-1)*gap,2),'page',mx[0],'line',mx[1],'n',mx[3])
sums=[r[4]+(r[3]-1)*0.15 for r in norm]
print('median',st.median(sums),'p5',sorted(sums)[len(sums)//20],'p95',sorted(sums)[-len(sums)//20],'min',min(sums))
# per page max
pm=collections.defaultdict(float)
for p,l,c,n,sw in norm: pm[p]=max(pm[p],sw+(n-1)*0.15)
v=sorted(pm.values()); print('page max: min',v[0],'median',st.median(v),'max',v[-1])
# widest centered lines
cen=[r for r in res if r[2]]
print('centered max',max(r[4]+(r[3]-1)*0.25 for r in cen))
