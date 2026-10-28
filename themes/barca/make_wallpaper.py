"""Blaugrana stripes on navy with a thin gold line, drawn in code: python3 make_wallpaper.py wallpaper.jpg"""
import sys
from PIL import Image, ImageDraw, ImageFilter
import numpy as np
W,H=1920,1080
x=np.arange(W)[None,:].repeat(H,0).astype(float); y=np.arange(H)[:,None].repeat(W,1).astype(float)
blue=np.array([0,77,152]); garnet=np.array([165,0,68]); navy=np.array([4,8,22])
# diagonal stripes
t=(x*0.94+y*0.34)/240.0
s=(np.floor(t)%2)[...,None]
frac=t-np.floor(t); edge=np.clip(np.minimum(frac,1-frac)*40,0,1)[...,None]
col=s*garnet+(1-s)*blue
col=col*(0.55+0.45*edge)
# vignette and darkening: brighter top-right, very dark bottom (dock) and left (text)
d=np.sqrt(((x-W*0.78)/W)**2+((y-H*0.15)/H)**2)
light=np.clip(1.05-d*1.35,0.0,1)[...,None]**1.6
bottom=np.clip(1-(y/H-0.45)/0.55,0.0,1)[...,None]
k=np.clip(0.95*(0.25+0.75*light**0.5)*(0.55+0.45*bottom),0,1)
img=navy+(col-navy)*k
# gold thin line
g=np.array([237,187,0])
line=np.exp(-((y-(H*0.62-x*0.36+380))**2)/(2*1.6**2))[...,None]*np.clip(light*1.6,0,1)
img=img+(g-img)*line*0.55
img=np.clip(img,0,255).astype(np.uint8)
im=Image.fromarray(img).filter(ImageFilter.GaussianBlur(1.2))
# grain
n=(np.random.default_rng(1).normal(0,3,(H,W,1))).repeat(3,2)
im=Image.fromarray(np.clip(np.asarray(im).astype(float)+n,0,255).astype(np.uint8))
im.save(sys.argv[1] if len(sys.argv) > 1 else 'wallpaper.jpg',quality=82,optimize=True,progressive=True)
