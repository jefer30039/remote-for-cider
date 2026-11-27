"""Extrae de un zip remoto (URL que admite Range) solo algunos archivos, sin bajarlo entero.
rangezip.py ARCHIVO_CON_URL DESTINO [sufijos,a,saltar] [solo-los-que-contengan]"""
import os, sys, urllib.request, zipfile
url, dest = open(sys.argv[1]).read().strip(), sys.argv[2]
skip = tuple(sys.argv[3].split(',')) if len(sys.argv) > 3 and sys.argv[3] else ('.mp4', '.mov')
only = sys.argv[4] if len(sys.argv) > 4 else None
class R:
    def __init__(s):
        s.size = int(urllib.request.urlopen(urllib.request.Request(url, method='HEAD')).headers['Content-Length']); s.pos = 0
    def seekable(s): return True
    def tell(s): return s.pos
    def seek(s, o, w=0):
        s.pos = o if w == 0 else s.pos + o if w == 1 else s.size + o; return s.pos
    def read(s, n=-1):
        if n < 0: n = s.size - s.pos
        if n == 0 or s.pos >= s.size: return b''
        end = min(s.pos + n, s.size) - 1
        data = urllib.request.urlopen(urllib.request.Request(url, headers={'Range': f'bytes={s.pos}-{end}'})).read()
        s.pos += len(data); return data
z = zipfile.ZipFile(R())
names = [i for i in z.infolist() if not i.is_dir() and ((only in i.filename) if only else not i.filename.endswith(skip))]
print(len(z.infolist()), 'en total;', len(names), 'a bajar,', sum(i.compress_size for i in names) // 1024, 'KB')
for i in names:
    out = os.path.join(dest, i.filename); os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'wb') as o: o.write(z.read(i))
print('listo')
