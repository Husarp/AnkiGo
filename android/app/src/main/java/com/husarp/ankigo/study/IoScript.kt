package com.husarp.ankigo.study

/**
 * Image Occlusion cards (Anki's own note type): the page needs `anki.imageOcclusion.setup()` and a little CSS.
 * The backend already turns each occlusion into a hidden `<div class="cloze|cloze-inactive|cloze-highlight" data-shape=...>`
 * (coordinates are fractions 0..1 of the picture); [JS] paints them on the canvas that sits over the picture.
 */
object IoScript {
    /** True when the rendered card is an Image Occlusion card. */
    fun needed(html: String) = html.contains("image-occlusion-container") || html.contains("anki.imageOcclusion")

    /** Goes BEFORE the note type's CSS so a note type can still change the colours. */
    val CSS = """
#image-occlusion-container{position:relative;margin:0 auto;max-height:calc(95vh - 40px)}
#image-occlusion-container img{position:absolute;top:0;left:0;width:100%;height:100%;max-width:unset;max-height:unset}
#image-occlusion-canvas{position:absolute;top:0;left:0;width:100%;height:100%;--inactive-shape-color:#ffeba2;--active-shape-color:#ff8e8e;--inactive-shape-border:1px #212121;--active-shape-border:1px #212121;--highlight-shape-color:#ff8e8e00;--highlight-shape-border:1px #ff8e8e}
#toggle{font:inherit;font-size:16px;margin:12px 0 2px;padding:8px 16px;min-height:40px}
"""

    /** Goes in `<head>`, before the body: the card template calls `anki.imageOcclusion.setup()` while the body is parsed. */
    val JS = """
(function(){
var MASK='#ffeba2',TEXT_BG='#ffffff',TEXT_FG='#000000',TEXT_FONT='Arial',TEXT_PAD=5,TEXT_SIZE=40,MAX_PIXELS=4096*4096;
var cfg={},hidden=false,wired=false;
function num(v,d){var n=parseFloat(v);return isFinite(n)?n:d}
function storedAngle(v){var n=Number(v)%10000;return isNaN(n)?0:n/10000*360}
function readShape(el){
 var d=el.dataset,t=d.shape;
 if(t!=='rect'&&t!=='ellipse'&&t!=='polygon'&&t!=='text')return null;
 var s={type:t,occludeInactive:d.occludeinactive==='1',left:num(d.left,0),top:num(d.top,0),angle:storedAngle(d.angle),fill:d.fill||null};
 if(t==='rect'){s.width=num(d.width,0);s.height=num(d.height,0)}
 else if(t==='ellipse'){s.rx=num(d.rx,0);s.ry=num(d.ry,0)}
 else if(t==='polygon'){
  s.points=(d.points||'').split(' ').filter(Boolean).map(function(p){var xy=p.split(',');return {x:num(xy[0],0),y:num(xy[1],0)}});
  if(!s.points.length)s.points=[{x:0,y:0}];
 }else{s.text=d.text||'';s.scale=num(d.scale,1);s.fontSize=num(d.fontSize,0)}
 return s;
}
function readShapes(sel,onlyText){
 var out=[],all=document.querySelectorAll(sel);
 for(var i=0;i<all.length;i++){
  if(all[i].tagName!=='DIV')continue;
  var s=readShape(all[i]);
  if(s&&(!onlyText||s.type==='text'))out.push(s);
 }
 return out;
}
function border(v,w,c){
 var m=/^\s*(-?[0-9.]+)(?:px)?\s*(.*?)\s*${'$'}/.exec(v||'');
 return m?{width:isFinite(parseFloat(m[1]))?parseFloat(m[1]):w,color:m[2]||c}:{width:w,color:c};
}
function props(canvas){
 var st=getComputedStyle(canvas);
 function g(n){return (st.getPropertyValue(n)||'').trim()}
 return {
  activeShapeColor:g('--active-shape-color')||'#ff8e8e',
  inActiveShapeColor:g('--inactive-shape-color')||MASK,
  highlightShapeColor:g('--highlight-shape-color')||'#ff8e8e00',
  activeBorder:border(g('--active-shape-border'),1,'#212121'),
  inActiveBorder:border(g('--inactive-shape-border'),1,'#212121'),
  highlightShapeBorder:border(g('--highlight-shape-border'),1,'#ff8e8e')
 };
}
function spin(c,x,y,a){c.save();c.translate(x,y);c.rotate(a);c.translate(-x,-y)}
/** Paints one shape. o = {context,size:{width,height},shape,fill,stroke,strokeWidth}; shape coordinates are fractions 0..1. */
function drawShape(o){
 var c=o.context,s=o.shape,w=o.size.width,h=o.size.height;
 function X(v){return Math.round(v*w)}
 function Y(v){return Math.round(v*h)}
 var L=X(s.left),T=Y(s.top),a=(s.angle||0)*Math.PI/180;
 c.fillStyle=o.fill;c.strokeStyle=o.stroke;c.lineWidth=o.strokeWidth;
 if(s.type==='rect'){
  if(a)spin(c,L,T,a);
  c.fillRect(L,T,X(s.width),Y(s.height));
  if(o.strokeWidth)c.strokeRect(L,T,X(s.width),Y(s.height));
  if(a)c.restore();
 }else if(s.type==='ellipse'){
  var rx=X(s.rx),ry=Y(s.ry);
  if(a)spin(c,L,T,a);
  c.beginPath();c.ellipse(L+rx,T+ry,rx,ry,0,0,Math.PI*2,false);c.closePath();c.fill();
  if(o.strokeWidth)c.stroke();
  if(a)c.restore();
 }else if(s.type==='polygon'){
  var pts=s.points.map(function(p){return {x:X(p.x),y:Y(p.y)}}),minX=pts[0].x,minY=pts[0].y;
  pts.forEach(function(p){if(p.x<minX)minX=p.x;if(p.y<minY)minY=p.y});
  c.save();c.translate(L-minX,T-minY);
  c.beginPath();c.moveTo(pts[0].x,pts[0].y);
  pts.forEach(function(p){c.lineTo(p.x,p.y)});
  c.closePath();c.fill();
  if(o.strokeWidth)c.stroke();
  c.restore();
 }else if(s.type==='text'){
  var sc=s.scale||1,size=s.fontSize?s.fontSize*h:TEXT_SIZE;
  c.save();
  c.font=size+'px '+TEXT_FONT;c.textBaseline='top';c.scale(sc,sc);
  var lines=(s.text||'').split('\n'),m=c.measureText('M');
  var lineH=1.5*(m.actualBoundingBoxAscent+m.actualBoundingBoxDescent),maxW=0,l=L/sc,t=T/sc;
  lines.forEach(function(ln){var lw=c.measureText(ln).width;if(lw>maxW)maxW=lw});
  if(a){c.translate(l,t);c.rotate(a);c.translate(-l,-t)}
  c.fillStyle=TEXT_BG;c.fillRect(l,t,maxW+TEXT_PAD,lines.length*lineH+TEXT_PAD);
  c.fillStyle=s.fill||TEXT_FG;
  lines.forEach(function(ln,i){c.fillText(ln,l,t+i*lineH)});
  c.restore();
 }
}
function paint(canvas,onlyText){
 var ctx=canvas.getContext('2d'),size={width:canvas.width,height:canvas.height};
 var d={activeShapes:readShapes('.cloze',onlyText),inactiveShapes:readShapes('.cloze-inactive',onlyText),highlightShapes:readShapes('.cloze-highlight',onlyText),properties:props(canvas)};
 try{if(cfg.onWillDrawShapes){var r=cfg.onWillDrawShapes(d,ctx);if(r)d=r}}catch(e){}
 var p=d.properties;
 function one(s,fill,b){try{drawShape({context:ctx,size:size,shape:s,fill:fill,stroke:b.color,strokeWidth:b.width})}catch(e){}}
 d.activeShapes.forEach(function(s){one(s,p.activeShapeColor,p.activeBorder)});
 d.inactiveShapes.filter(function(s){return s.occludeInactive}).forEach(function(s){one(s,s.fill&&s.fill!==MASK?s.fill:p.inActiveShapeColor,p.inActiveBorder)});
 d.highlightShapes.forEach(function(s){one(s,p.highlightShapeColor,p.highlightShapeBorder)});
 try{if(cfg.onDidDrawShapes)cfg.onDidDrawShapes(d,ctx)}catch(e){}
}
function layout(){
 try{
  var box=document.getElementById('image-occlusion-container'),cv=document.getElementById('image-occlusion-canvas');
  var img=document.querySelector('#image-occlusion-container img');
  var btn=document.getElementById('toggle');
  if(btn&&!document.querySelector('[data-occludeinactive="1"]'))btn.style.display='none';
  if(!box||!cv||!img||!img.naturalWidth||!img.naturalHeight)return;
  var nw=img.naturalWidth,nh=img.naturalHeight,st=getComputedStyle(box);
  var vw=box.parentElement.clientWidth,cmw=parseFloat(st.getPropertyValue('max-width'));
  var maxW=isNaN(cmw)||cmw>vw?vw:cmw;
  var def=document.documentElement.clientHeight*0.95-40,cmh=parseFloat(st.getPropertyValue('max-height')),maxH;
  if(isNaN(cmh))maxH=Math.max(nh,def);
  else if(cmh<def)maxH=cmh;
  else maxH=Math.max(def,Math.min(nh,cmh));
  if(!(maxW>0)||!(maxH>0))return;
  var r=Math.min(maxW/nw,maxH/nh),w=nw*r,h=nh*r;
  box.style.width=w+'px';box.style.height=h+'px';
  var dpr=window.devicePixelRatio||1,cw=w*dpr,ch=h*dpr;
  if(cw*ch>MAX_PIXELS){var k=Math.sqrt(MAX_PIXELS/(cw*ch));cw*=k;ch*=k}
  cv.width=Math.max(1,Math.floor(cw));cv.height=Math.max(1,Math.floor(ch));
  paint(cv,hidden);
 }catch(e){}
}
function toggle(){
 var btn=document.getElementById('toggle');
 if(!btn||btn.style.display==='none')return;
 hidden=!hidden;layout();
}
function setup(options){
 try{
  cfg=options||{};
  var img=document.querySelector('#image-occlusion-container img');
  var go=function(){window.requestAnimationFrame(layout)};
  if(!img||img.complete)go();
  else{img.addEventListener('load',go);img.addEventListener('error',go)}
  if(!wired){
   wired=true;
   window.addEventListener('resize',go);
   window.addEventListener('load',go);
   document.addEventListener('click',function(e){if(e.target.closest&&e.target.closest('#toggle'))toggle()});
   window.addEventListener('keydown',function(e){if(e.key==='M')toggle()});
  }
 }catch(e){}
 return Promise.resolve();
}
window.anki=window.anki||{};
window.anki.imageOcclusion={setup:setup,drawShape:drawShape};
})();
"""
}
