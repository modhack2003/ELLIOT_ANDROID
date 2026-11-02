"""Score user-approved transcripts; never records audio or calls a network service."""
import argparse
import json
import unicodedata
from pathlib import Path

MODES = ('indian_english', 'hindi_english', 'bengali_english')

def normalize(text):
    text = unicodedata.normalize('NFC', text).casefold()
    return ' '.join(''.join(c if unicodedata.category(c)[0] in 'LNM' or c.isspace() else ' ' for c in text).split())

def distance(a, b):
    row = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        nxt = [i]
        for j, y in enumerate(b, 1):
            nxt.append(min(nxt[-1]+1, row[j]+1, row[j-1]+(x != y)))
        row = nxt
    return row[-1]

def evaluate(rows):
    groups = {}; seen = set()
    for item in rows:
        if not isinstance(item, dict): raise ValueError('Each trial must be an object')
        if not isinstance(item.get('id'), str) or not item['id'] or item['id'] in seen:
            raise ValueError('Trial IDs must be nonempty and unique')
        seen.add(item['id'])
        if item.get('mode') not in MODES: raise ValueError('Unknown speech mode')
        for key in ('reference', 'hypothesis'):
            if not isinstance(item.get(key), str): raise ValueError(f'{key} must be text')
        for key in ('expected_wake', 'detected_wake'):
            if key in item and type(item[key]) is not bool: raise ValueError(f'{key} must be boolean')
        if ('expected_wake' in item) != ('detected_wake' in item):
            raise ValueError('Wake observations need both expected and detected fields')
        ref, hyp = normalize(item['reference']), normalize(item['hypothesis'])
        stats = groups.setdefault(item['mode'], dict(trials=0, word_edits=0, reference_words=0,
            character_edits=0, reference_characters=0, exact_transcripts=0,
            wake_positive_trials=0, wake_hits=0, wake_negative_trials=0, false_wakes=0))
        stats['trials'] += 1
        stats['word_edits'] += distance(ref.split(), hyp.split())
        stats['reference_words'] += len(ref.split())
        stats['character_edits'] += distance(ref.replace(' ', ''), hyp.replace(' ', ''))
        stats['reference_characters'] += len(ref.replace(' ', ''))
        stats['exact_transcripts'] += ref == hyp
        if 'expected_wake' in item:
            if item['expected_wake']:
                stats['wake_positive_trials'] += 1; stats['wake_hits'] += item['detected_wake']
            else:
                stats['wake_negative_trials'] += 1; stats['false_wakes'] += item['detected_wake']
    if not seen: raise ValueError('No trials supplied')
    for stats in groups.values():
        stats['wer'] = stats['word_edits']/stats['reference_words'] if stats['reference_words'] else None
        stats['cer'] = stats['character_edits']/stats['reference_characters'] if stats['reference_characters'] else None
        stats['wake_recall'] = stats['wake_hits']/stats['wake_positive_trials'] if stats['wake_positive_trials'] else None
    return dict(schema_version=1, normalization='NFC/casefold; punctuation to spaces; no transliteration or translation',
        missing_modes=[m for m in MODES if m not in groups], by_mode=groups,
        limitations=['Prompted negative trials do not establish false wakes per hour.',
                      'WER can exceed 1.0 when insertions exceed the reference length.',
                      'Transcript scores do not measure command execution correctness.'])

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True, help='UTF-8 JSONL trial file')
    parser.add_argument('--output', type=Path, required=True)
    args=parser.parse_args()
    result=evaluate([json.loads(line) for line in args.input.read_text(encoding='utf-8-sig').splitlines() if line.strip()])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
