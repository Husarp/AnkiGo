"""Retain the Noto still artwork; animate only vertical rising licks, in a seamless 4.8 s loop.
Run from the repository root with Pillow and numpy installed. No horizontal pixel displacement.
"""
from pathlib import Path
import numpy as np
from PIL import Image
base = Path(__file__).resolve().parents[1] / 'app/src/main/res'
im = Image.open(base / 'drawable-nodpi/streak_flame_still.png').convert('RGBA').resize((128, 128), Image.Resampling.LANCZOS)
y, x = np.mgrid[:128, :128].astype(float)
frames = []
for i in range(96):
    phase = 2 * np.pi * i / 96
    # Fixed x coordinates keep side licks apart. Smooth upward waves vary their height.
    strength = np.clip((115-y)/90, 0, 1)
    offset = strength * (2.4*np.sin(phase*2 + y*.075 + x*.035) + 1.8*np.sin(phase*3 + y*.11 - x*.06))
    source_y = np.clip(y + offset, 0, 127)
    lo = np.floor(source_y).astype(int); hi = np.minimum(lo+1,127)
    data = np.asarray(im).astype(float)
    fraction = (source_y-lo)[...,None]
    frame = data[lo,x.astype(int)]*(1-fraction)+data[hi,x.astype(int)]*fraction
    # The same upward-moving wave gently fades the high tips as each lick rises.
    frame[...,3] *= 1 - .22*strength*(.5 + .5*np.sin(phase*2 + y*.075 + x*.035))
    frames.append(Image.fromarray(np.uint8(np.clip(frame,0,255)), 'RGBA'))
frames[0].save(base/'raw/streak_flame.webp',save_all=True,append_images=frames[1:],duration=50,loop=0,lossless=True)
