"""Restore v0.25.0's Noto flame at half speed, preserving encoded frame artwork.
Run from any directory with Pillow installed. Google Noto Emoji, CC BY 4.0;
existing in-app attribution applies. The fallback is decoded from frame zero.
"""
from pathlib import Path
import subprocess
from io import BytesIO
from PIL import Image

root = Path(__file__).resolve().parents[2]
asset = 'android/app/src/main/res/raw/streak_flame.webp'
original = subprocess.check_output(['git', '-C', str(root), 'show', f'v0.25.0:{asset}'])
slower = bytearray(original)
offset = 12
while offset < len(slower):
    size = int.from_bytes(slower[offset + 4:offset + 8], 'little')
    if slower[offset:offset + 4] == b'ANMF':
        duration = int.from_bytes(slower[offset + 20:offset + 23], 'little')
        slower[offset + 20:offset + 23] = (duration * 2).to_bytes(3, 'little')
    offset += 8 + size + size % 2
(root / asset).write_bytes(slower)
Image.open(BytesIO(original)).convert('RGBA').save(root / 'android/app/src/main/res/drawable-nodpi/streak_flame_still.png')
