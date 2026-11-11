"""Rebuild the half-speed streak loop from Google's original 512px Noto Fire.

Run with Pillow installed; optionally pass a local copy of the source WebP.
Only ANMF durations change: no resized or re-encoded animation artwork.
Google Animated Noto Emoji, CC BY 4.0; existing in-app attribution applies.
"""
from pathlib import Path
from io import BytesIO
from hashlib import sha256
import sys
from urllib.request import urlopen
from PIL import Image

root = Path(__file__).resolve().parents[2]
url = 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f525/512.webp'
original = Path(sys.argv[1]).read_bytes() if len(sys.argv) > 1 else urlopen(url).read()
assert sha256(original).hexdigest() == '8c18972a71041700a1da4d9a1586640fb6d55eff4dc64efd29efde6a384a2ff5'
image = Image.open(BytesIO(original))
assert image.size == (512, 512) and image.n_frames == 33
slower = bytearray(original)
offset = 12
frames = 0
while offset < len(slower):
    size = int.from_bytes(slower[offset + 4:offset + 8], 'little')
    if slower[offset:offset + 4] == b'ANMF':
        assert int.from_bytes(slower[offset + 20:offset + 23], 'little') == 30
        slower[offset + 20:offset + 23] = (60).to_bytes(3, 'little')
        frames += 1
    offset += 8 + size + size % 2
assert frames == 33
(root / 'android/app/src/main/res/raw/streak_flame.webp').write_bytes(slower)
image.convert('RGBA').save(root / 'android/app/src/main/res/drawable-nodpi/streak_flame_still.png')
