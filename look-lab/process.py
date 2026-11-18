"""
Aperture Look Lab
=================
Prototype for Aperture's default "look": true blacks, warm hues,
natural texture (no artificial sharpening), plus optional film grain.

Usage
-----
Process one photo:
    python process.py samples/yourphoto.jpg

Process everything in samples/:
    python process.py samples

Tune the look (all knobs optional, values below are the defaults):
    python process.py samples/yourphoto.jpg --blacks 0.02 --warmth 0.08 ^
        --saturation 1.08 --contrast 1.06 --grain 0.35 --sharpen 0.0

A side-by-side comparison (original | processed) is written to out/ so
you can judge the look quickly.

The numbers this pipeline settles on become Aperture's "default recipe" --
the baseline the agent will later vary when you describe a mood.
"""

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

# Default recipe (Aperture's look). Tune these, then we freeze them.
DEFAULTS = {
    "blacks": 0.02,      # shadow floor crush: how deep the blacks go
    "contrast": 1.06,    # S-curve-ish contrast around mid gray
    "warmth": 0.08,      # red up / blue down shift (warm hues)
    "saturation": 1.08,  # color richness
    "grain": 0.35,       # film grain amount (0 = off)
    "sharpen": 0.0,      # unsharp mask amount (0 = OFF by default, on purpose)
}


def load(path: Path) -> np.ndarray:
    """Load an image as float RGB in [0, 1]."""
    img = Image.open(path).convert("RGB")
    return np.asarray(img, dtype=np.float64) / 255.0


def crush_blacks(img: np.ndarray, blacks: float) -> np.ndarray:
    """
    TEACHING NOTE -- "true blacks":
    Phone JPEGs rarely have pixels near pure black; shadows sit at a muddy
    gray floor (e.g. 0.05-0.10). Subtracting a small black point remaps that
    floor to 0 so blacks read as TRUE black. Dividing by (1 - blacks)
    renormalizes so highlights are untouched.
    """
    if blacks <= 0:
        return img
    return np.clip((img - blacks) / (1.0 - blacks), 0.0, 1.0)


def contrast_curve(img: np.ndarray, c: float) -> np.ndarray:
    """
    TEACHING NOTE -- "pop":
    A linear contrast curve pivoted at mid-gray. c > 1 expands distances
    from mid-gray: brights brighter, darks darker. Mild values (1.05-1.10)
    give punch without clipping.
    """
    return np.clip((img - 0.5) * c + 0.5, 0.0, 1.0)


def warm(img: np.ndarray, w: float) -> np.ndarray:
    """
    TEACHING NOTE -- "warm hues":
    A simple white-balance-style shift: red channel up, blue channel down.
    In gamma-encoded (display) space this reads as golden/amber warmth,
    like tungsten light or late-afternoon sun.
    """
    out = img.copy()
    out[..., 0] *= (1.0 + w)   # R up
    out[..., 2] *= (1.0 - w)   # B down
    return np.clip(out, 0.0, 1.0)


def saturate(img: np.ndarray, s: float) -> np.ndarray:
    """
    TEACHING NOTE -- saturation:
    Pull each pixel toward/away from its own luminance. s = 1 is unchanged,
    s = 0 is grayscale. Applied AFTER warmth so the warm shift stays intact.
    """
    luma = img @ np.array([0.2126, 0.7152, 0.0722])
    luma = luma[..., np.newaxis]
    return np.clip(luma + (img - luma) * s, 0.0, 1.0)


def add_grain(img: np.ndarray, amount: float, seed: int = 7) -> np.ndarray:
    """
    TEACHING NOTE -- film grain:
    Real film grain is (a) clumpy, not pixel-level static, and (b) most
    visible in midtones/shadows, nearly invisible in highlights (that's
    where film is densest). So we:
      1. generate Gaussian noise,
      2. blur it slightly -> clumps,
      3. scale its visibility by a curve that fades out in highlights,
      4. add it equally to all channels (luminance grain, no color speckle).
    """
    if amount <= 0:
        return img
    h, w = img.shape[:2]
    rng = np.random.default_rng(seed)
    noise = rng.normal(0.0, 1.0, (h, w)).astype(np.float32)

    # Blur the noise so grains clump like film, via PIL's Gaussian filter.
    noise_img = Image.fromarray(noise, mode="F")
    noise_img = noise_img.filter(ImageFilter.GaussianBlur(radius=0.8))
    clumps = np.asarray(noise_img, dtype=np.float64)

    # Visibility: peaks around midtones (4*x*(1-x) peaks at x=0.5), fades at
    # both extremes -- grain shouldn't dirtify true blacks or blow highlights.
    luma = img @ np.array([0.2126, 0.7152, 0.0722])
    vis = 4.0 * luma * (1.0 - luma)

    strength = amount * 0.12  # scale slider to a sane sigma
    return np.clip(img + clumps[..., np.newaxis] * vis[..., np.newaxis] * strength, 0.0, 1.0)


def unsharp(img: np.ndarray, amount: float) -> np.ndarray:
    """
    TEACHING NOTE -- sharpening:
    Pixel's default output is sharpened, which is exactly the "artificial"
    crispness you dislike -- it adds halos along edges. Aperture's default
    is sharpen = 0. This knob exists mainly so we can compare side by side.
    """
    if amount <= 0:
        return img
    pil = Image.fromarray((img * 255).round().clip(0, 255).astype(np.uint8))
    pil = pil.filter(ImageFilter.UnsharpMask(radius=2.0, percent=int(amount * 100), threshold=3))
    return np.asarray(pil, dtype=np.float64) / 255.0


def to_u8(img: np.ndarray) -> Image.Image:
    return Image.fromarray((img * 255.0).round().clip(0, 255).astype(np.uint8), mode="RGB")


def process(path: Path, args) -> None:
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    img = load(path)
    steps = [
        ("crush blacks", crush_blacks(img, args.blacks)),
        ("contrast", None),
        ("warmth", None),
        ("saturation", None),
        ("grain", None),
        ("sharpen", None),
    ]
    # Apply in order, logging each stage.
    current = img
    current = crush_blacks(current, args.blacks);      print(f"  [1/6] blacks      {args.blacks:.2f}")
    current = contrast_curve(current, args.contrast);  print(f"  [2/6] contrast    {args.contrast:.2f}")
    current = warm(current, args.warmth);              print(f"  [3/6] warmth      {args.warmth:.2f}")
    current = saturate(current, args.saturation);      print(f"  [4/6] saturation  {args.saturation:.2f}")
    current = add_grain(current, args.grain);          print(f"  [5/6] grain       {args.grain:.2f}")
    current = unsharp(current, args.sharpen);          print(f"  [6/6] sharpen     {args.sharpen:.2f}")

    # Side-by-side comparison: original | divider | processed.
    h = max(img.shape[0], current.shape[0])
    divider = np.full((h, 6, 3), 255.0)
    comp = np.concatenate([img, divider, current], axis=1)
    out_name = f"{path.stem}_compare.jpg"
    to_u8(comp).save(out_dir / out_name, quality=92)

    # Processed-only version at full quality.
    to_u8(current).save(out_dir / f"{path.stem}_aperture.jpg", quality=95)
    print(f"  -> {out_dir / out_name}")


def collect_inputs(target: Path) -> list[Path]:
    if target.is_dir():
        files = sorted(
            p for p in target.iterdir()
            if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".heic", ".webp"}
        )
        if not files:
            print(f"No photos found in {target} -- drop a few JPGs in there first.")
            sys.exit(1)
        return files
    return [target]


def main() -> None:
    parser = argparse.ArgumentParser(description="Aperture Look Lab")
    parser.add_argument("input", help="photo file or directory (e.g. samples)")
    for key, default in DEFAULTS.items():
        parser.add_argument(f"--{key}", type=float, default=default)
    parser.add_argument("--out-dir", default="out")
    args = parser.parse_args()

    target = Path(args.input)
    if not target.exists():
        print(f"Input not found: {target}")
        sys.exit(1)

    print(f"Aperture Look Lab -- defaults: blacks={args.blacks}, contrast={args.contrast}, "
          f"warmth={args.warmth}, saturation={args.saturation}, grain={args.grain}, sharpen={args.sharpen}")
    for path in collect_inputs(target):
        print(f"\n{path.name}")
        process(path, args)


if __name__ == "__main__":
    main()
