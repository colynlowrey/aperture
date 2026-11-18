# Aperture Look Lab

The prototype for Aperture's **default look**: true blacks, warm hues,
natural texture (no artificial sharpening), plus optional film grain.

This runs on your laptop so we can iterate on the *look* in seconds instead
of rebuilding an Android app for every experiment. The recipe we settle on
here becomes the app's default profile — and later, the baseline the agent
varies when you describe a mood.

## How to use

1. **Drop 2–3 of your own photos** into `samples/` (plain JPGs from your
   phone are perfect). Personal photos are git-ignored — they will NOT be
   pushed to GitHub.
2. Process everything:
   ```
   python process.py samples
   ```
3. Open `out/` — each photo gets:
   - `*_compare.jpg` — original | processed, side by side
   - `*_aperture.jpg` — the processed photo alone

## Tuning the look

Every knob has a default (Aperture's starting recipe):

```
python process.py samples/yourphoto.jpg --blacks 0.02 --warmth 0.08 --grain 0.35
```

| Knob | Default | What it does |
|---|---|---|
| `--blacks` | 0.02 | Depth of the shadow floor (true blacks) |
| `--contrast` | 1.06 | Punch around mid-gray |
| `--warmth` | 0.08 | Red up / blue down (warm hues) |
| `--saturation` | 1.08 | Color richness |
| `--grain` | 0.35 | Film grain (0 = off) |
| `--sharpen` | 0.0 | Unsharp mask — **off by default, on purpose** |

Every stage is commented in `process.py` with a TEACHING NOTE explaining
the *why* behind it.
