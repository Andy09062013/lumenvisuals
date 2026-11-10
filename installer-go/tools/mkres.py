# Builds a COFF .syso with a Windows VERSIONINFO + manifest so the exe has normal app metadata.
import struct, sys

def u16z(s): return (s + '\0').encode('utf-16-le')
def pad4(b): return b + b'\0' * ((4 - len(b) % 4) % 4)

def block(key, value=b'', vtype=0, children=b'', vlen=None):
    head = struct.pack('<HHH', 0, len(value) if vlen is None else vlen, vtype) + u16z(key)
    head = pad4(head)
    body = pad4(head + value) + children
    return struct.pack('<H', len(body)) + body[2:]

def ver(a, b, c, d): return (a << 16 | b, c << 16 | d)

def versioninfo(fields, v):
    ms, ls = ver(*v)
    fixed = struct.pack('<13I', 0xFEEF04BD, 0x10000, ms, ls, ms, ls, 0x3F, 0, 0x40004, 1, 0, 0, 0)
    strings = b''.join(pad4(block(k, u16z(val), 1, vlen=len(val) + 1)) for k, val in fields.items())
    table = block('040904B0', children=strings, vtype=1)
    sfi = block('StringFileInfo', children=pad4(table), vtype=1)
    var = block('Translation', struct.pack('<HH', 0x0409, 0x04B0), 0)
    vfi = block('VarFileInfo', children=pad4(var), vtype=1)
    return block('VS_VERSION_INFO', fixed, 0, children=pad4(sfi) + vfi)

manifest = b'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
  <assemblyIdentity type="win32" name="Lumen.Installer" version="1.3.0.0" processorArchitecture="amd64"/>
  <description>Lumen Installer</description>
  <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3"><security><requestedPrivileges>
    <requestedExecutionLevel level="asInvoker" uiAccess="false"/>
  </requestedPrivileges></security></trustInfo>
  <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1"><application>
    <supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}"/>
  </application></compatibility>
</assembly>
'''

vi = versioninfo({
    'CompanyName': 'Lumen',
    'FileDescription': 'Lumen Installer',
    'FileVersion': '1.3.0.0',
    'InternalName': 'LumenInstaller',
    'LegalCopyright': 'Lumen (MIT)',
    'OriginalFilename': 'LumenInstaller.exe',
    'ProductName': 'Lumen',
    'ProductVersion': '1.3.0.0',
}, (1, 3, 0, 0))

res = [(16, vi), (24, manifest)]  # RT_VERSION, RT_MANIFEST (each id 1, lang 0x409)

# layout: root dir, then per type: type dir, name dir; then data entries; then data
n = len(res)
root_sz = 16 + 8 * n
type_dirs, name_dirs = [], []
off = root_sz
for _ in res: type_dirs.append(off); off += 24
for _ in res: name_dirs.append(off); off += 24
entries = []
for _ in res: entries.append(off); off += 16
datas = []
for _, d in res:
    off = (off + 7) & ~7
    datas.append(off); off += len(d)
total = (off + 7) & ~7

sec = bytearray(total)
def dirhdr(o, count): sec[o:o+16] = struct.pack('<IIHHHH', 0, 0, 0, 0, 0, count)
dirhdr(0, n)
for i, (t, _) in enumerate(res):
    struct.pack_into('<II', sec, 16 + 8 * i, t, 0x80000000 | type_dirs[i])
    dirhdr(type_dirs[i], 1); struct.pack_into('<II', sec, type_dirs[i] + 16, 1, 0x80000000 | name_dirs[i])
    dirhdr(name_dirs[i], 1); struct.pack_into('<II', sec, name_dirs[i] + 16, 0x409, entries[i])
    struct.pack_into('<IIII', sec, entries[i], datas[i], len(res[i][1]), 0, 0)
    sec[datas[i]:datas[i] + len(res[i][1])] = res[i][1]

relocs = b''.join(struct.pack('<IIH', e, 0, 3) for e in entries)  # IMAGE_REL_AMD64_ADDR32NB
hdr_sz = 20 + 40
raw_ptr = hdr_sz
rel_ptr = raw_ptr + total
sym_ptr = rel_ptr + len(relocs)
coff = struct.pack('<HHIIIHH', 0x8664, 1, 0, sym_ptr, 1, 0, 0)
shdr = b'.rsrc\0\0\0' + struct.pack('<IIIIIIHHI', 0, 0, total, raw_ptr, rel_ptr, 0, len(relocs) // 10, 0, 0x40000040)
sym = b'.rsrc\0\0\0' + struct.pack('<IhHBB', 0, 1, 0, 3, 0)
open(sys.argv[1], 'wb').write(coff + shdr + bytes(sec) + relocs + sym + struct.pack('<I', 4))
