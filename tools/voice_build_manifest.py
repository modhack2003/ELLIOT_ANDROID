"""Identify the exact source, runtime, model contract and APK used in a voice trial."""
import argparse
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
def sha(data): return hashlib.sha256(data).hexdigest()
def git(*args): return subprocess.check_output(['git','-C',str(root),*args],text=True).strip()

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--capture-profile',choices=['communication','reference_mic'],required=True)
    args=p.parse_args()
    with zipfile.ZipFile(args.apk) as apk:
        contract=apk.read('assets/voice-engine.json')
        models=apk.read('assets/models/manifest.json')
    result=dict(source_commit=git('rev-parse','HEAD'),source_dirty=bool(git('status','--porcelain')),
        apk_name=args.apk.name,apk_bytes=args.apk.stat().st_size,apk_sha256=sha(args.apk.read_bytes()),
        capture_profile=args.capture_profile,profile_note='Caller-supplied build profile; archive build invocation alongside this manifest.',
        packaged_voice_contract=json.loads(contract),packaged_voice_contract_sha256=sha(contract),
        packaged_model_manifest_sha256=sha(models),models=json.loads(models),
        runtime_aar_sha256=sha((root/'app/libs/sherpa-onnx-1.13.8.aar').read_bytes()))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
