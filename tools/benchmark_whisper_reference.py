"""Run a local whisper.cpp binary on an explicitly consented WAV. No network calls."""
import argparse,hashlib,json,subprocess,time,wave
from pathlib import Path
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['audio','model','executable','output']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--model-sha256',required=True)
    p.add_argument('--languages',nargs='+',default=['auto','bn'])
    a=p.parse_args()
    if sha(a.model)!=a.model_sha256:raise ValueError('Model checksum mismatch')
    with wave.open(str(a.audio),'rb') as w:
        if (w.getnchannels(),w.getsampwidth(),w.getframerate())!=(1,2,16000):raise ValueError('Expected mono PCM16 at 16 kHz')
        duration=w.getnframes()/16000
        if not 0<duration<=28:raise ValueError('Expected a bounded speech clip')
    a.output.mkdir(parents=True,exist_ok=True)
    rows=[]
    for lang in a.languages:
        if lang not in ['auto','en','hi','bn']:raise ValueError('Unsupported test language')
        prefix=a.output/lang
        args=[str(a.executable.resolve()),'-m',str(a.model.resolve()),'-f',str(a.audio.resolve()),'-l',lang,'-t','4','-ng','-bo','1','-bs','1','-oj','-of',str(prefix.resolve())]
        start=time.monotonic()
        r=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=300)
        prefix.with_suffix('.log').write_bytes(r.stdout)
        if r.returncode:raise RuntimeError(f'whisper.cpp exited {r.returncode}; inspect {prefix}.log')
        value=json.loads(prefix.with_suffix('.json').read_text(encoding='utf-8'))
        row={'language':lang,'detected_language':value.get('result',{}).get('language'),'wall_seconds':round(time.monotonic()-start,3),'text':''.join(s['text'] for s in value['transcription']).strip()}
        rows.append(row);print(json.dumps(row,ensure_ascii=False),flush=True)
    report={'audio_sha256':sha(a.audio),'audio_seconds':duration,'model_sha256':sha(a.model),'executable_sha256':sha(a.executable),'decoding':'CPU; 4 threads; beam-size=1; best-of=1; transcription','results':rows,'limitation':'Model precision/export and runtime may differ from Android. This is not a controlled runtime-only comparison.'}
    (a.output/'comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
if __name__=='__main__':main()
