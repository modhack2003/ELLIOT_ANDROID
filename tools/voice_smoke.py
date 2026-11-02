import json,time,wave,sys
from pathlib import Path
import numpy as np
import sherpa_onnx as so

root=Path(__file__).resolve().parents[1]/'app/src/main/assets/models'
out=Path(__file__).resolve().parents[1]/'build/host-voice-review';out.mkdir(parents=True,exist_ok=True)
def paths(folder):return {k:str(root/folder/(k+'.onnx')) for k in ['encoder','decoder','joiner']}
start=time.monotonic()
spec=json.loads((root.parents[0]/'voice-engine.json').read_text())
assert spec['runtime']=='1.13.8'
assert spec['kws']['model_type']=='zipformer2' and spec['asr']['model_type']=='zipformer'
tts=so.OfflineTts(so.OfflineTtsConfig(model=so.OfflineTtsModelConfig(vits=so.OfflineTtsVitsModelConfig(model=str(root/'tts/model.onnx'),tokens=str(root/'tts/tokens.txt'),data_dir=str(root/'tts/espeak-ng-data')),num_threads=2),max_num_sentences=1))
audio=tts.generate('Hey Elliot. Set volume to thirty percent.',sid=0,speed=1)
assert audio.sample_rate==22050 and len(audio.samples)>1000 and np.max(np.abs(audio.samples))>.01
with wave.open(str(out/'elliot-male-sample.wav'),'wb') as w:
 w.setnchannels(1);w.setsampwidth(2);w.setframerate(audio.sample_rate);w.writeframes((np.clip(audio.samples,-1,1)*32767).astype('<i2').tobytes())
samples=np.interp(np.arange(0,len(audio.samples),audio.sample_rate/16000),np.arange(len(audio.samples)),audio.samples).astype(np.float32)
samples=np.concatenate([samples,np.zeros(16000,dtype=np.float32)])
recognizer=so.OnlineRecognizer.from_transducer(tokens=str(root/'asr/tokens.txt'),**paths('asr'),decoding_method='modified_beam_search',modeling_unit='bpe',bpe_vocab=str(root/'asr/bpe.vocab'),hotwords_score=1.5,num_threads=2,model_type=spec['asr']['model_type'])
stream=recognizer.create_stream('ELLIOT\nVOLUME\nTIMER\nYOUTUBE\nWHATSAPP')
for i in range(0,len(samples),512):
 stream.accept_waveform(16000,samples[i:i+512])
 while recognizer.is_ready(stream):recognizer.decode_stream(stream)
stream.input_finished()
while recognizer.is_ready(stream):recognizer.decode_stream(stream)
text=recognizer.get_result(stream)
assert 'volume' in text.lower() and 'thirty' in text.lower(),text
# The public 1.13.8 KWS wrapper omits model_type. Build its pinned native config
# explicitly so this test exercises the same loader selection as Android.
kw=so.KeywordSpotter.__init__.__globals__
family='zipformer' if '--regression-wrong-loader' in sys.argv else spec['kws']['model_type']
config=kw['OnlineModelConfig'](transducer=kw['OnlineTransducerModelConfig'](**paths('kws')),tokens=str(root/'kws/tokens.txt'),num_threads=spec['threads'],model_type=family,modeling_unit=spec['kws']['modeling_unit'],bpe_vocab=spec['kws']['bpe_vocab'],provider_config=kw['ProviderConfig'](provider='cpu'))
spotter=so.KeywordSpotter.__new__(so.KeywordSpotter)
spotter.keyword_spotter=kw['_KeywordSpotter'](kw['KeywordSpotterConfig'](feat_config=kw['FeatureExtractorConfig'](sampling_rate=16000,feature_dim=80),model_config=config,max_active_paths=4,num_trailing_blanks=1,keywords_score=2.0,keywords_threshold=.15,keywords_file=str(root/'kws/keywords.txt')))

ks=spotter.create_stream();hits=[]
for i in range(0,len(samples),512):
 ks.accept_waveform(16000,samples[i:i+512])
 while spotter.is_ready(ks):
  spotter.decode_stream(ks)
  hit=spotter.get_result(ks)
  if hit: hits.append(hit);spotter.reset_stream(ks)
assert hits,hits
vad=so.VoiceActivityDetector(so.VadModelConfig(silero_vad=so.SileroVadModelConfig(model=str(root/'silero_vad.onnx'),threshold=.65,min_speech_duration=.192,window_size=512),sample_rate=16000,num_threads=1),buffer_size_in_seconds=20)
detected=0
for i in range(0,len(samples)-511,512):
 vad.accept_waveform(samples[i:i+512]);detected+=int(vad.is_speech_detected())
assert detected>6
vad.reset()
for _ in range(40):
 vad.accept_waveform(np.zeros(512,dtype=np.float32));assert not vad.is_speech_detected()
result={'runtime':'sherpa-onnx 1.13.8 Linux CPU (not Android audio)','tts_samples':len(audio.samples),'tts_sample_rate':audio.sample_rate,'asr_synthesized_command':text,'keyword_hits':hits,'vad_speech_frames':detected,'silence_frames_checked':40,'elapsed_seconds':round(time.monotonic()-start,3),'limits':'Synthetic near-field model smoke only. Not phone echo, false-wake rate, distance, end-to-end latency or Android JNI/audio certification.'}
(out/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
