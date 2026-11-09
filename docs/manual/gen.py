import sys, importlib.util
spec=importlib.util.spec_from_file_location('c','/tmp/claude-0/manual/content.py'); c=importlib.util.module_from_spec(spec); spec.loader.exec_module(c)
repo=sys.argv[1]
def kq(s): return '"' + s.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('$','\\$') + '"'
out=['package it.migliorati.viviapp','','// FILE GENERATO da docs/manual: non modificare a mano.','','/** Una sezione del manuale: titolo e paragrafi (le righe con "• " sono elenchi). */',
     'class ManualSection(val title: UiText, val paragraphs: List<UiText>)','','val manual: List<ManualSection> = listOf(']
for (t,ps) in c.S:
    out.append('    ManualSection(')
    out.append('        { tr(%s, %s, %s) },' % tuple(kq(x) for x in t))
    out.append('        listOf(')
    for p in ps: out.append('            { tr(%s, %s, %s) },' % tuple(kq(x) for x in p))
    out.append('        ),')
    out.append('    ),')
out.append(')')
open(repo+'/app/src/main/java/it/migliorati/viviapp/Manual.kt','w').write('\n'.join(out)+'\n')
heads={0:('ViViApp – User manual','The teacher\'s mixer for headphone yoga classes'),1:('ViViApp – Korisnički priručnik','Mikseta za satove joge sa slušalicama'),2:('ViViApp – Manuale d\'uso','Il mixer per le lezioni di yoga in cuffia')}
names={0:'MANUAL_EN.md',1:'PRIRUCNIK_HR.md',2:'MANUALE_IT.md'}
import os; os.makedirs(repo+'/docs',exist_ok=True)
for i in range(3):
    md=['# '+heads[i][0],'','*'+heads[i][1]+' · ViVi – Oltre il Benessere*','']
    for (t,ps) in c.S:
        md.append('## '+t[i]); md.append('')
        for p in ps: md.append(p[i].replace('• ','- ')); md.append('')
    open(repo+'/docs/'+names[i],'w').write('\n'.join(md))
print('ok', len(c.S), 'sezioni')
