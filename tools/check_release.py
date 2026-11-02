"""Verify delivered APK's actual models and 16 KB alignment of 64-bit ELF loads.
Usage: python3 tools/check_release.py path/to.apk
APK signing and zipalign checks still require Android build-tools separately.
"""
import hashlib,json,struct,sys,zipfile
from pathlib import Path
p=Path(sys.argv[1]); rows=[]
with zipfile.ZipFile(p) as z:
    assert "AndroidManifest.xml" in z.namelist(), "Incomplete APK: missing AndroidManifest.xml"
    assert any(n.endswith(".dex") for n in z.namelist()), "Incomplete APK: missing bytecode"
    manifest=json.loads(z.read("assets/models/manifest.json"))
    for row in manifest["files"]:
        data=z.read("assets/models/"+row["path"])
        assert len(data)==row["bytes"],row["path"]
        assert hashlib.sha256(data).hexdigest()==row["sha256"],row["path"]
    for name in z.namelist():
        if name.endswith(".so") and ("arm64-v8a/" in name or "x86_64/" in name):
            d=z.read(name);assert d[:5]==b"\x7fELF\x02",name
            off=struct.unpack_from("<Q",d,32)[0]; count=struct.unpack_from("<H",d,56)[0]; size=struct.unpack_from("<H",d,54)[0]
            loads=[]
            for i in range(count):
                at=off+i*size
                if struct.unpack_from("<I",d,at)[0]==1:
                    file_offset,address=struct.unpack_from("<QQ",d,at+8)
                    align=struct.unpack_from("<Q",d,at+48)[0]
                    assert align>=16384 and file_offset%16384==address%16384,(name,align)
                    loads.append(align)
            assert loads,name
            rows.append({"library":name,"load_alignments":loads})
print(json.dumps({"file":p.name,"bytes":p.stat().st_size,"sha256":hashlib.sha256(p.read_bytes()).hexdigest(),"model_files_verified":len(manifest["files"]),"model_bytes_verified":sum(x["bytes"] for x in manifest["files"]),"aligned_64_bit_libraries":rows},indent=2))
