# Independent OOXML extraction for the frozen audit fixture. Run with Python 3.
import json,re,zipfile,xml.etree.ElementTree as E,collections
from pathlib import Path
base = Path(__file__).resolve().parent
z = zipfile.ZipFile(base / 'schedule-2026-09-30.xlsx')
n = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
strings = [''.join(t.text or '' for t in si.findall('.//m:t', n))
           for si in E.fromstring(z.read('xl/sharedStrings.xml')).findall('m:si', n)]
styles = E.fromstring(z.read('xl/styles.xml')).find('m:cellXfs', n)
sheets = []
for course in range(1, 5):
    cells = {}
    for cell in E.fromstring(z.read(f'xl/worksheets/sheet{course}.xml')).findall('.//m:sheetData/m:row/m:c', n):
        value = cell.find('m:v', n)
        text = value.text if value is not None else ''
        if cell.attrib.get('t') == 's': text = strings[int(text)]
        elif cell.attrib.get('t') == 'inlineStr': text = ''.join(t.text or '' for t in cell.findall('.//m:t', n))
        elif text and re.fullmatch(r'\d+\.0', text): text = text[:-2]
        style = int(cell.attrib.get('s', '0'))
        alignment = styles[style].find('m:alignment', n)
        cells[cell.attrib['r']] = {'text': text or '', 'alignment': alignment.attrib if alignment is not None else {}}
    sheets.append({'course': course, 'cells': cells})
expected = []
ambiguous = []
x='{http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing}'; a='{http://schemas.openxmlformats.org/drawingml/2006/main}'; r='{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
def colname(n):
 s=''
 while n: n,k=divmod(n-1,26);s=chr(65+k)+s
 return s
def splitrooms(v):return [p.strip() for p in re.split(r'[,;\n\r]+|(?<=\d)\s*/\s*(?=\d)',v.replace('ауд.','').replace('ауд','')) if p.strip()]
for si,sheet in enumerate(sheets,1):
 c=sheet['cells'];markers=collections.defaultdict(set)
 rels={el.attrib['Id']:el.attrib['Target'] for el in E.fromstring(z.read(f'xl/drawings/_rels/drawing{si}.xml.rels'))}
 for anchor in E.fromstring(z.read(f'xl/drawings/drawing{si}.xml')):
  fr=anchor.find(x+'from');blip=anchor.find('.//'+a+'blip')
  if fr is None or blip is None:continue
  target=rels[blip.attrib[r+'embed']];color='red' if target.endswith('image9.png') else 'blue' if target.endswith('image1.png') else None
  if color:markers[colname(int(fr.find(x+'col').text)+1)+str(int(fr.find(x+'row').text)+1)].add(color)
 day=''
 for row in range(2,40):
  day=c.get('A'+str(row),{}).get('text','') or day;slot=c.get('C'+str(row),{}).get('text','');time=c.get('B'+str(row),{}).get('text','')
  if not slot.isdigit() or not time:continue
  for col in range(4,80,2):
   key=colname(col)+str(row);v=c.get(key,{});text=v.get('text','').strip();group=c.get(colname(col)+'1',{}).get('text','')
   if not text or not group:continue
   lines=[l.strip() for l in text.splitlines() if l.strip()];entries=[];subject=[]
   for l in lines:
    if re.search(r'[А-ЯЁ][а-яё]+\s+[А-ЯЁ]\.\s*[А-ЯЁ]\.',l):entries.append((' '.join(subject),l));subject=[]
    else:subject.append(l)
   if subject:entries.append((' '.join(subject),''))
   rooms=splitrooms(c.get(colname(col+1)+str(row),{}).get('text',''));total=sum(len(t.split(',')) if t else 1 for _,t in entries);offset=0
   for i,(subject,teacher) in enumerate(entries):
    count=len(teacher.split(',')) if teacher else 1
    if len(entries)==2:week=['red','blue'][i]
    elif len(markers[key])==1:week=next(iter(markers[key]))
    else:week={'top':'red','bottom':'blue'}.get(v.get('alignment',{}).get('vertical'))
    if len(rooms)==total:room='/'.join(rooms[offset:offset+count])
    elif not rooms:room=''
    elif len(entries)==2:room=rooms[min(i,len(rooms)-1)];ambiguous.append((si,key,teacher,rooms)) if count>1 else None
    elif count==1 and week:room=rooms[-1] if week=='blue' else rooms[0]
    else:room='/'.join(rooms)
    expected.append(dict(course=si,cell=key,group=group,day=day,slot=int(slot),time=time,subject=subject,teacher=teacher,room=room,week=week));offset+=count
json.dump(expected,open(base / 'expected-schedule.json','w'),ensure_ascii=False,indent=2)
print('entries',len(expected),'groups',len(set(e['group'] for e in expected)),'ambiguous',ambiguous)
