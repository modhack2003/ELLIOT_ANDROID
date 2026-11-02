import unittest
from evaluate_voice import evaluate, normalize

class VoiceEvaluationTest(unittest.TestCase):
    def trial(self, reference, hypothesis, **kw):
        return dict(id='one', mode='indian_english', reference=reference, hypothesis=hypothesis, **kw)
    def test_hand_scored_deletion(self):
        r=evaluate([self.trial('do not open YouTube', 'do open YouTube')])['by_mode']['indian_english']
        self.assertEqual(1,r['word_edits']);self.assertEqual(.25,r['wer'])
    def test_unicode_and_modes_remain_separate(self):
        self.assertEqual('ব্যাটারি কত',normalize('ব্যাটারি কত?'))
        self.assertEqual('बैटरी कितनी है',normalize('बैटरी कितनी है?'))
        r=evaluate([self.trial('battery', 'battery'),dict(id='two',mode='bengali_english',reference='ব্যাটারি কত',hypothesis='ব্যাটারি')])
        self.assertEqual(.5,r['by_mode']['bengali_english']['wer'])
        self.assertEqual(['hindi_english'],r['missing_modes'])
    def test_empty_reference_retains_insertions_without_dividing_by_zero(self):
        r=evaluate([self.trial('', 'hello', expected_wake=False,detected_wake=True)])['by_mode']['indian_english']
        self.assertIsNone(r['wer']);self.assertEqual(1,r['word_edits']);self.assertEqual(1,r['false_wakes'])
    def test_invalid_observations_are_rejected(self):
        with self.assertRaises(ValueError): evaluate([self.trial('a','a',expected_wake='false',detected_wake=False)])
        with self.assertRaises(ValueError): evaluate([self.trial('a','a')]*2)

if __name__=='__main__': unittest.main()
