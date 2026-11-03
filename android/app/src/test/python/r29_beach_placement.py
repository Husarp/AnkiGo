"""Offline Pillow replica of StageFit, BackdropDraw.stageCrop and ArenaBackdrop mapping.
Run from any directory with Python + Pillow; outputs are uncommitted design evidence.
"""
from PIL import Image, ImageDraw
from pathlib import Path
ROOT=Path(__file__).resolve().parents[5]; OUT=ROOT/'docs/design/r29-shots'; OUT.mkdir(parents=True,exist_ok=True)
IDS=['tropical-cove','coral-lagoon','sandbar-isle','reef-bay']
SIZES=[(412,869),(412,640),(412,400),(869,253),(240,135),(200,200),(90,600),(360,200),(412,230)]
def stage(iw,ih,w,h,z=1):
    pts=[(.29,.94,.44),(.74,.54,.32)]; pad=.025*ih
    l=max(0,min(x*iw-s*ih*.5 for x,y,s in pts)-pad);r=min(iw,max(x*iw+s*ih*.5 for x,y,s in pts)+pad)
    t=max(0,min(y*ih-s*ih*.82 for x,y,s in pts)-pad);b=min(ih,max(y*ih for x,y,s in pts)+pad)
    cw,ch=(ih*w/h,ih) if iw/ih>w/h else (iw,iw*h/w)
    k=max((r-l)/cw,(b-t)/ch,z);cw*=k;ch*=k
    def place(c,s,lo,hi,p):return (s-c)/2 if c>=s else max(0,min(s-c,max(min(p,lo),hi-c)))
    cx=place(cw,iw,l,r,(iw-cw)/2);cy=place(ch,ih,t,b,ih-ch)
    return cx,cy,cw,ch,[((x*iw-cx)/cw,(y*ih-cy)/ch) for x,y,s in pts]
def crop(iw,ih,w,h,gx,gy,gz,z=1,max_height=.4):
    cx,cy,cw,ch,pts=stage(iw,ih,w,h,z);u,v=pts[1]; zoom=max(1,gz,ch/(ih*max_height));shrink=min(1,iw*zoom/cw,ih*zoom/ch)
    cw=cw/zoom*shrink;ch=ch/zoom*shrink
    x=max(0,min(iw-cw,gx*iw-u*cw));y=max(0,min(ih-ch,gy*ih-v*ch))
    return (x,y,x+cw,y+ch),pts
if __name__=='__main__':
    import re
    catalog=(ROOT/'android/app/src/main/java/com/husarp/ankigo/ui/BackdropCatalog.kt').read_text()
    vals=[]
    for id in IDS:
        line=next(line for line in catalog.splitlines() if f'own("{id}"' in line)
        match=re.search(r'ground = p\(([.\d]+)f, ([.\d]+)f, .*groundZoom = ([.\d]+)f, groundMaxHeight = ([.\d]+)f',line)
        assert match, id
        gx,gy,gz,mh=map(float,match.groups());vals.append((gx,gy,gz,mh))
    count=0
    for id,g in zip(IDS,vals):
        im=Image.open(ROOT/f'android/app/src/main/assets/backdrops/{id}.png');iw,ih=im.size
        # The measured sand patch contains both feet, away from water and the rocky edges.
        shapes=SIZES+[(int(a*400),400) for a in [.5,.6,1,1.25,1.4,2.2,2.6,3.5]]
        for w,h in shapes:
            for z in [.72,1,1.5]:
                box,pts=crop(iw,ih,w,h,*g[:3],z=z,max_height=g[3])
                assert box[0]>=-1e-6 and box[1]>=-1e-6 and box[2]<=iw+1e-6 and box[3]<=ih+1e-6
                assert abs((box[2]-box[0])/(box[3]-box[1])-w/h)<1e-6
                for u,v in pts:
                    x=box[0]+u*(box[2]-box[0]);y=box[1]+v*(box[3]-box[1])
                    assert .34<=x/iw<=.52 and .79<=y/ih<=.95, (id,w,h,z,x/iw,y/ih)
                    patch=im.crop((round(x)-2,round(y)-1,round(x)+3,round(y)+2)).convert('RGB')
                    sand=sum(r>b*1.12 and green>b*1.08 for r,green,b in (patch.getpixel((x,y)) for y in range(patch.height) for x in range(patch.width)))
                    assert sand>=10, (id,w,h,z,'not sand',sand)
                count+=1
        sheet=Image.new('RGB',(900,3*320),'#222222')
        for n,(w,h) in enumerate(SIZES):
            box,pts=crop(iw,ih,w,h,*g[:3],max_height=g[3]); view=im.transform((w,h),Image.Transform.EXTENT,box,Image.Resampling.BICUBIC)
            d=ImageDraw.Draw(view)
            for label,(u,v),col in zip(['P','W'],pts,['#ff0055','#0033ff']):
                x,y=u*w,v*h;d.ellipse((x-5,y-3,x+5,y+3),outline=col,width=2);d.line((x-10,y,x+10,y),fill=col,width=2);d.text((x+6,y-14),label,fill=col)
            view.thumbnail((294,285));sheet.paste(view,((n%3)*300,(n//3)*320+24));ImageDraw.Draw(sheet).text(((n%3)*300+5,(n//3)*320+5),f'{w}x{h}',fill='white')
        sheet.save(OUT/f'f-beach-{id}.png')
    print(f'PASS: {count} scene/host/zoom combinations; both feet on dry sand. Four overlays saved to {OUT}')
