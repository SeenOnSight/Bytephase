// ---- Edit this list to match your own client's modules ----
const MODULES={
 Combat:["Aim Assist","Auto Armor","Auto Totem","Auto Potion","Criticals","Hitboxes","Reach","Velocity","Trigger","Target Strafe"],
 Visuals:["ESP","Tracers","Nametags","Fullbright","Chams","Zoom","Hand View","Time Changer","Particles","Cape"],
 Player:["Auto Sprint","Fast Place","Inventory Sort","No Fall","Auto Tool","Chest Steal","Freecam","Auto Respawn"],
 Movement:["Speed","Fly","Step","Jesus","Spider","Safe Walk","Long Jump","Sprint"],
 World:["Nuker","Scaffold","Auto Mine","Fast Break","Auto Build","Liquid Interact"],Explore:["Chunk Scanner","Waypoints","Coordinates","Nav Assist","Biome Finder"],Other:["Auto Reconnect","Chat Filter","Spammer","Timer","Panic","Discord RPC"]
};
const ICON={Combat:'<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="2"/><path d="M12 2v4M12 18v4M2 12h4M18 12h4"/>',
Visuals:'<path d="M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"/>',Player:'<circle cx="12" cy="8" r="4"/><path d="M4 21c0-5 4-7 8-7s8 2 8 7"/>',
Movement:'<circle cx="14" cy="4" r="2"/><path d="M8 21l3-7-3-3 3-4 4 3 3 1M11 14l4 2 1 5"/>',Other:'<circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>',
Configs:'<path d="M3 6a2 2 0 012-2h4l2 2h8a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2z"/>',
World:'<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M4 10h16M10 4v16"/>',Explore:'<circle cx="12" cy="12" r="9"/><path d="M15.5 8.5l-2 5-5 2 2-5z"/>',Settings:'<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/>',Friends:'<circle cx="9" cy="8" r="3.5"/><path d="M2 20c0-4 3-6 7-6s7 2 7 6M17 5a3.5 3.5 0 010 7M22 20c0-3-2-5-4-5.5"/>',
Themes:'<path d="M12 3a9 9 0 100 18c1.5 0 2-1 1.5-2s0-2 1.5-2h2a3 3 0 003-3c0-6-4-11-8-11z"/>'};
const THEMES={Indigo:"#6c6ff5",Mint:"#3fb68b",Ocean:"#3b9be8",Violet:"#9b6cf0",Rose:"#ee6d9b",Amber:"#e8a23b",Crimson:"#e5534b"};
const store={get(k,d){try{const v=localStorage.getItem(k);return v?JSON.parse(v):d}catch(e){return d}},set(k,v){try{localStorage.setItem(k,JSON.stringify(v))}catch(e){}}};
let tab=store.get("ng_tab","Combat"),state=store.get("ng_state",{}),binds=store.get("ng_binds",{}),configs=store.get("ng_cfgs",{}),current=store.get("ng_cur",""),friends=store.get("ng_friends",[]),mset=store.get("ng_mset",{}),ui=Object.assign({opacity:45,blur:true,stream:false,uiFont:"Inter",hudFont:"Inter",bold:true,shadow:false,noOverlap:true,labelLimit:48,textRGB:[240,241,255],accRGB:null},store.get("ng_ui",{})),theme=store.get("ng_theme","Indigo"),query="";
const $=s=>document.querySelector(s),body=$("#body");
const toast=m=>{const t=$("#toast");t.textContent=m;t.classList.add("show");clearTimeout(t._t);t._t=setTimeout(()=>t.classList.remove("show"),1800)};
const esc=s=>s.replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const saveAll=()=>{store.set("ng_state",state);store.set("ng_binds",binds);store.set("ng_mset",mset)};
function setTheme(n){theme=n;document.documentElement.style.setProperty("--acc",THEMES[n]);store.set("ng_theme",n)}

function buildNav(){
 const mk=t=>`<button data-t="${t}" class="${t===tab?"on":""}"><svg viewBox="0 0 24 24">${ICON[t]}</svg><span>${t}</span></button>`;
 $("#nav").innerHTML=Object.keys(MODULES).map(mk).join("")+"<hr>"+["Configs","Friends","Themes","Settings"].map(mk).join("");
}
$("#nav").onclick=e=>{const b=e.target.closest("button");if(!b)return;tab=b.dataset.t;query="";$("#q").value="";render()};
$("#q").oninput=e=>{query=e.target.value.toLowerCase();render(true)};

function render(keep){
 buildNav();$("#title").textContent=tab;
 if(MODULES[tab]||(query&&true)){
  const list=query?Object.entries(MODULES).flatMap(([c,a])=>a.map(n=>[c,n])).filter(([c,n])=>n.toLowerCase().includes(query)):MODULES[tab].map(n=>[tab,n]);
  body.innerHTML=list.length?`<div class="grid">${list.map(([c,n])=>{const k=c+"."+n,on=!!state[k];
   return `<div class="mod ${on?"on":""}" tabindex="0" role="switch" aria-checked="${on}" data-k="${k}"><svg class="ci" viewBox="0 0 24 24">${ICON[c]}</svg><span class="n">${n}</span><span class="bind">${binds[k]||""}</span><span class="sw"></span></div>`}).join("")}</div>`:`<div class="empty">No modules match “${esc(query)}”.</div>`;
  return;
 }
 if(tab==="Configs")return renderCfg();
 if(tab==="Friends")return renderFriends();
 if(tab==="Themes")return renderThemes();
 if(tab==="Settings")return renderSettings();
}
body.onclick=e=>{
 const m=e.target.closest(".mod");if(m){state[m.dataset.k]=!state[m.dataset.k];saveAll();m.classList.toggle("on");m.setAttribute("aria-checked",!!state[m.dataset.k]);return}
 const a=e.target.closest("[data-a]");if(a)act(a.dataset.a,a.dataset.n);
};
// ---- Module settings (right click) ----
// Default settings for every module. Add per-module overrides in CUSTOM, e.g. CUSTOM["Movement.Speed"]=[{k:"speed",t:"slider",l:"Speed",min:1,max:5,step:.1,d:1.5}]
const DEF=[{k:"range",t:"slider",l:"Range",min:1,max:8,step:.1,d:3},{k:"delay",t:"slider",l:"Delay (ms)",min:0,max:500,step:10,d:100},{k:"mode",t:"select",l:"Mode",o:["Normal","Smooth","Strict"],d:"Normal"},{k:"show",t:"toggle",l:"Show in HUD",d:true}];
const CUSTOM={};
const sets=k=>CUSTOM[k]||DEF;
const gv=(k,s)=>(mset[k]&&mset[k][s.k]!==undefined)?mset[k][s.k]:s.d;
const setVal=(k,s,v)=>{(mset[k]=mset[k]||{})[s]=v;saveAll()};
const ov=$("#ov");let open=null,capturing=false;
function srow(k,s){const v=gv(k,s);let c="";
 if(s.t==="slider")c=`<div class="sl"><input type="range" data-s="${s.k}" min="${s.min}" max="${s.max}" step="${s.step}" value="${v}" aria-label="${s.l}"><output>${v}</output></div>`;
 if(s.t==="select")c=`<select data-s="${s.k}" aria-label="${s.l}">${s.o.map(o=>`<option ${o===v?"selected":""}>${o}</option>`).join("")}</select>`;
 if(s.t==="toggle")c=`<button class="sw2 ${v?"on":""}" data-tg="${s.k}" aria-label="${s.l}"></button>`;
 return `<div class="pr"><span>${s.l}</span>${c}</div>`}
function openMod(k){open=k;capturing=false;const n=k.split(".")[1];
 ov.innerHTML=`<div class="pc" role="dialog" aria-label="${n} settings"><div class="ph"><b>${n}</b><button class="btn g" data-p="close">Close</button></div>
 <div class="pr"><span>Enabled</span><button class="sw2 ${state[k]?"on":""}" data-p="en" aria-label="Enabled"></button></div>
 <div class="pr"><span>Keybind</span><button class="btn g" data-p="bind">${binds[k]||"None"}</button></div>
 ${sets(k).map(s=>srow(k,s)).join("")}
 <div class="pf"><button class="btn d" data-p="reset">Reset module</button></div></div>`;
 ov.classList.add("show")}
function closeMod(){open=null;capturing=false;ov.classList.remove("show");ov.innerHTML=""}
const refresh=()=>{if(MODULES[tab]||query)render(true)};
body.oncontextmenu=e=>{const m=e.target.closest(".mod");if(!m)return;e.preventDefault();openMod(m.dataset.k)};
ov.addEventListener("click",e=>{if(e.target===ov)return closeMod();const b=e.target.closest("[data-p],[data-tg]");if(!b)return;const k=open;
 if(b.dataset.tg){const on=!b.classList.contains("on");b.classList.toggle("on",on);return setVal(k,b.dataset.tg,on)}
 const p=b.dataset.p;
 if(p==="close")closeMod();
 if(p==="en"){state[k]=!state[k];b.classList.toggle("on",!!state[k]);saveAll();refresh()}
 if(p==="bind"){capturing=true;b.textContent="Press a key…"}
 if(p==="reset"){delete mset[k];delete binds[k];state[k]=false;saveAll();openMod(k);refresh()}});
ov.addEventListener("input",e=>{const t=e.target;if(!t.dataset.s)return;const v=t.type==="range"?+t.value:t.value;if(t.type==="range")t.nextElementSibling.textContent=v;setVal(open,t.dataset.s,v)});
document.addEventListener("keydown",e=>{if(!open)return;
 if(capturing){e.preventDefault();e.stopPropagation();
  if(e.code!=="ShiftRight"&&e.key!=="Escape"){if(e.key==="Backspace"||e.key==="Delete")delete binds[open];else binds[open]=e.key.toUpperCase().slice(0,8);saveAll();refresh()}
  return openMod(open)}
 if(e.key==="Escape")closeMod()},true);
body.onkeydown=e=>{if((e.key==="Enter"||e.key===" ")&&e.target.classList.contains("mod")){e.preventDefault();e.target.click()}};

// ---- Configs ----
const snap=()=>({modules:{...state},binds:{...binds},mset:JSON.parse(JSON.stringify(mset)),theme});
function renderCfg(keepText){
 const names=Object.keys(configs);
 body.innerHTML=`<div class="sec">Create config</div>
 <div class="row"><input id="cn" placeholder="Config name" maxlength="32" aria-label="Config name"><button class="btn" data-a="create">Save current</button></div>
 <div class="sec">Your configs</div>
 ${names.length?names.map(n=>{const c=configs[n],cnt=Object.values(c.modules).filter(Boolean).length;
  return `<div class="cfg ${n===current?"cur":""}"><div class="n">${esc(n)}<small>${cnt} modules enabled${n===current?" · loaded":""}</small></div>
  <button class="btn" data-a="load" data-n="${esc(n)}">Load</button><button class="btn g" data-a="overwrite" data-n="${esc(n)}">Overwrite</button><button class="btn g" data-a="copy" data-n="${esc(n)}">Copy</button><button class="btn d" data-a="del" data-n="${esc(n)}">Delete</button></div>`}).join(""):`<div class="empty">No configs yet. Name one above and select Save current.</div>`}
 <div class="sec" style="margin-top:18px">Import</div>
 <textarea id="imp" placeholder="Paste a copied config here" aria-label="Import config"></textarea>
 <div class="row" style="margin-top:10px"><input id="in" placeholder="Name for imported config" maxlength="32"><button class="btn" data-a="import">Import</button></div>`;
}
function act(a,n){
 if(a==="create"){const v=$("#cn").value.trim();if(!v)return toast("Enter a config name");if(configs[v]&&!confirm("Replace existing config “"+v+"”?"))return;configs[v]=snap();current=v}
 if(a==="load"){const c=configs[n];state={...c.modules};binds={...c.binds};mset=JSON.parse(JSON.stringify(c.mset||{}));if(THEMES[c.theme])setTheme(c.theme);current=n;saveAll();toast("Loaded "+n)}
 if(a==="overwrite"){configs[n]=snap();current=n;toast("Saved "+n)}
 if(a==="del"){if(!confirm("Delete config “"+n+"”?"))return;delete configs[n];if(current===n)current=""}
 if(a==="copy"){const t=JSON.stringify(configs[n]);(navigator.clipboard?navigator.clipboard.writeText(t):Promise.reject()).then(()=>toast("Copied to clipboard"),()=>{$("#imp").value=t;toast("Copy the text from the import box")});return}
 if(a==="import"){try{const c=JSON.parse($("#imp").value),nm=$("#in").value.trim();if(!nm)return toast("Enter a name for the import");if(!c.modules)throw 0;configs[nm]={modules:c.modules,binds:c.binds||{},mset:c.mset||{},theme:c.theme||theme};toast("Imported "+nm)}catch(e){return toast("That config is not valid")}}
 store.set("ng_cfgs",configs);store.set("ng_cur",current);renderCfg();
}
// ---- Friends ----
function renderFriends(){
 body.innerHTML=`<div class="row"><input id="fn" placeholder="Add a friend by name" maxlength="24"><button class="btn" data-a="fadd">Add</button></div>
 ${friends.length?friends.map(f=>`<div class="cfg"><div class="n">${esc(f)}</div><button class="btn d" data-a="frem" data-n="${esc(f)}">Remove</button></div>`).join(""):`<div class="empty">No friends added yet.</div>`}`;
}
const _act=act;act=function(a,n){if(a==="fadd"){const v=$("#fn").value.trim();if(v&&!friends.includes(v)){friends.push(v);store.set("ng_friends",friends)}return renderFriends()}
 if(a==="frem"){friends=friends.filter(f=>f!==n);store.set("ng_friends",friends);return renderFriends()}
 if(a==="theme"){ui.accRGB=null;setTheme(n);applyUI();return renderThemes()}return _act(a,n)};
function renderThemes(){
 body.innerHTML=`<div class="sec">Accent color</div><div class="sws">${Object.entries(THEMES).map(([n,c])=>`<button title="${n}" aria-label="${n}" class="${n===theme?"on":""}" style="background:${c}" data-a="theme" data-n="${n}"></button>`).join("")}</div>`;
}
// ---- Settings, fonts, themes, dialog controls, keyboard ----
window.confirm=()=>true;
const FONTS={Inter:'Inter,system-ui,sans-serif',Outfit:'Outfit,system-ui,sans-serif',"JetBrains Mono":'"JetBrains Mono",ui-monospace,monospace',Minecraft:'"Pixelify Sans","Press Start 2P",monospace'};
const OPT={uiFont:Object.keys(FONTS),hudFont:Object.keys(FONTS)};
const h2r=s=>[1,3,5].map(i=>parseInt(s.slice(i,i+2),16));
function applyUI(){const r=document.documentElement.style;
 r.setProperty("--panel",`rgba(26,24,80,${ui.opacity/100})`);r.setProperty("--font",FONTS[ui.uiFont]);r.setProperty("--hud",FONTS[ui.hudFont]);
 r.setProperty("--blur",ui.blur?"16px":"0px");r.setProperty("--hw",ui.bold?700:500);r.setProperty("--tsh",ui.shadow?"0 1px 2px rgba(0,0,0,.85)":"none");
 r.setProperty("--tx",`rgb(${ui.textRGB})`);if(ui.accRGB)r.setProperty("--acc",`rgb(${ui.accRGB})`);store.set("ng_ui",ui)}
function cycle(k,d){const o=OPT[k];ui[k]=o[(o.indexOf(ui[k])+d+o.length)%o.length];applyUI();renderSettings()}
function renderSettings(){
 const sw=k=>`<button class="sw2 ${ui[k]?"on":""}" data-u="${k}" aria-label="${k}"></button>`;
 const md=k=>`<button class="btn g" data-u="${k}" data-m="1" style="font-family:${FONTS[ui[k]]}">${ui[k]}</button>`;
 const sl=(k,a,b)=>`<div class="sl"><input type="range" data-u="${k}" min="${a}" max="${b}" step="1" value="${ui[k]}" aria-label="${k}"><output>${ui[k]}</output></div>`;
 const R=(l,c,d)=>`<div class="pr"><span>${l}${d?`<small style="display:block;color:var(--mut);font-weight:400;font-size:11px">${d}</small>`:""}</span>${c}</div>`;
 body.innerHTML=`<div class="pc" style="width:100%;max-height:none;box-shadow:none;background:var(--card)">`+R("PanelOpacity",sl("opacity",10,100))+R("BackgroundBlur",sw("blur"))+R("StreamProof",sw("stream"),"Ctrl+Shift+F9 toggles. Record Minecraft’s original window.")+R("UIFont",md("uiFont"),"Click for next, right-click for previous")+R("HUDFont",md("hudFont"),"Used by the KeyBinds overlay")+R("BoldHeaders",sw("bold"))+R("TextShadow",sw("shadow"))+R("AvoidLabelOverlap",sw("noOverlap"))+R("WorldLabelLimit",sl("labelLimit",0,128))+`</div>`}
function rgbRows(p,rgb){return "RGB".split("").map((c,i)=>`<div class="pr"><span>${c}</span><div class="sl"><input type="range" data-c="${p}${i}" min="0" max="255" value="${rgb[i]}" aria-label="${p} ${c}"><output>${rgb[i]}</output></div></div>`).join("")}
function renderThemes(){
 body.innerHTML=`<div class="sec">Accent presets</div><div class="sws">${Object.entries(THEMES).map(([n,c])=>`<button title="${n}" aria-label="${n}" class="${n===theme&&!ui.accRGB?"on":""}" style="background:${c}" data-a="theme" data-n="${n}"></button>`).join("")}</div><div class="sec" style="margin-top:18px">Accent</div>${rgbRows("a",ui.accRGB||h2r(THEMES[theme]))}<div class="sec" style="margin-top:14px">Text</div>${rgbRows("t",ui.textRGB)}`}
body.addEventListener("click",e=>{const b=e.target.closest("[data-u]");if(!b)return;const k=b.dataset.u;if(b.dataset.m)cycle(k,1);else if(b.classList.contains("sw2")){ui[k]=!ui[k];b.classList.toggle("on",ui[k]);applyUI()}});
body.addEventListener("contextmenu",e=>{const b=e.target.closest("[data-m]");if(b){e.preventDefault();cycle(b.dataset.u,-1)}});
body.addEventListener("input",e=>{const t=e.target,c=t.dataset.c,u=t.dataset.u;
 if(c){const k=c[0]==="a"?"accRGB":"textRGB";ui[k]=ui[k]||h2r(THEMES[theme]);ui[k][+c[1]]=+t.value}else if(u&&t.type==="range")ui[u]=+t.value;else return;
 t.nextElementSibling.textContent=t.value;applyUI()});
// module dialog: mode selector (left next / right previous), color RGB, action, conditional settings
DEF.push({k:"smooth",t:"slider",l:"Smoothing",min:1,max:10,step:1,d:4,w:"Smooth"},{k:"color",t:"color",l:"Color",d:[108,111,245]},{k:"act",t:"action",l:"Clear targets"});
function srow(k,s){const v=gv(k,s);let c="";
 if(s.t==="slider")c=`<div class="sl"><input type="range" data-s="${s.k}" min="${s.min}" max="${s.max}" step="${s.step}" value="${v}" aria-label="${s.l}"><output>${v}</output></div>`;
 if(s.t==="select")c=`<button class="btn g" data-md="${s.k}">${v}</button>`;
 if(s.t==="toggle")c=`<button class="sw2 ${v?"on":""}" data-tg="${s.k}" aria-label="${s.l}"></button>`;
 if(s.t==="action")c=`<button class="btn g" data-ac="1">Run</button>`;
 if(s.t==="color")return `<div class="pr" style="display:block"><span>${s.l}</span>${[0,1,2].map(i=>`<div class="sl" style="margin-top:6px"><span style="width:14px">${"RGB"[i]}</span><input type="range" data-s2="${s.k}" data-ci="${i}" min="0" max="255" value="${v[i]}" aria-label="${s.l} ${"RGB"[i]}" style="flex:1"><output>${v[i]}</output></div>`).join("")}</div>`;
 return `<div class="pr"><span>${s.l}</span>${c}</div>`}
function openMod(k){open=k;capturing=false;const n=k.split(".")[1],md=gv(k,DEF[2]);
 ov.innerHTML=`<div class="pc" role="dialog" aria-label="${n} settings"><div class="ph"><b>${n}</b><button class="btn g" data-p="close">Close</button></div>
 <div class="pr"><span>Enabled</span><button class="sw2 ${state[k]?"on":""}" data-p="en" aria-label="Enabled"></button></div>
 <div class="pr"><span>Keybind</span><button class="btn g" data-p="bind">${binds[k]||"None"}</button></div>
 ${sets(k).filter(s=>!s.w||s.w===md).map(s=>srow(k,s)).join("")}
 <div class="pf"><button class="btn d" data-p="reset">Reset module</button></div></div>`;ov.classList.add("show")}
const mdCycle=(k,sk,d)=>{const s=sets(k).find(x=>x.k===sk),o=s.o,i=(o.indexOf(gv(k,s))+d+o.length)%o.length;setVal(k,sk,o[i]);openMod(k)};
ov.addEventListener("click",e=>{const b=e.target.closest("[data-md]");if(b)mdCycle(open,b.dataset.md,1);if(e.target.closest("[data-ac]"))toast("Action triggered")});
ov.addEventListener("contextmenu",e=>{const b=e.target.closest("[data-md]");if(b){e.preventDefault();mdCycle(open,b.dataset.md,-1)}
 if(e.target.closest('[data-p="bind"]')){e.preventDefault();delete binds[open];saveAll();refresh();openMod(open)}});
ov.addEventListener("input",e=>{const t=e.target;if(t.dataset.ci===undefined)return;const s=sets(open).find(x=>x.k===t.dataset.s2),c=[...gv(open,s)];c[+t.dataset.ci]=+t.value;setVal(open,s.k,c);t.nextElementSibling.textContent=t.value});
// configs: 1-32 chars [A-Za-z0-9_-], second click to replace or delete
const _act2=act;let pend=null;
act=function(a,n){
 if(a==="create"||a==="import"){const v=$(a==="create"?"#cn":"#in").value.trim();
  if(!/^[A-Za-z0-9_-]{1,32}$/.test(v))return toast("Names use 1–32 letters, numbers, _ or -");
  if(configs[v]&&pend!==a+v){pend=a+v;return toast("“"+v+"” exists. Click again to replace")}pend=null}
 if(a==="del"){if(pend!=="d"+n){pend="d"+n;return toast("Click Delete again to confirm")}pend=null}
 return _act2(a,n)};
// remember page and per-page scroll
const scr=store.get("ng_scr",{});let last=tab;const _r=render;
render=function(k){if(last!==tab){scr[last]=body.scrollTop;last=tab;store.set("ng_scr",scr);store.set("ng_tab",tab);_r(k);body.scrollTop=scr[tab]||0}else _r(k)};
// keyboard: Ctrl/Cmd+F, arrows, Enter/Space, Shift+Enter, Escape, Ctrl+Shift+F9
let sel=-1;
document.addEventListener("keydown",e=>{
 if(e.ctrlKey&&e.shiftKey&&e.code==="F9"){e.preventDefault();ui.stream=!ui.stream;applyUI();toast("StreamProof "+(ui.stream?"on":"off"));if(tab==="Settings")renderSettings();return}
 if($("#gui").classList.contains("hide")||open)return;
 if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==="f"){e.preventDefault();$("#q").focus();return}
 const typing=/INPUT|TEXTAREA|SELECT/.test(e.target.tagName);
 if(e.key==="Escape"){if(typing)e.target.blur();else $("#gui").classList.add("hide");return}
 if(typing||e.target.classList.contains("mod")||!(MODULES[tab]||query))return;
 const c=[...body.querySelectorAll(".mod")];if(!c.length)return;
 const mv={ArrowRight:1,ArrowLeft:-1,ArrowDown:2,ArrowUp:-2}[e.key];
 if(mv){e.preventDefault();sel=Math.max(0,Math.min(c.length-1,sel+mv));c.forEach((x,i)=>x.classList.toggle("selc",i===sel));c[sel].scrollIntoView({block:"nearest"});return}
 if(c[sel]&&(e.key==="Enter"||e.key===" ")){e.preventDefault();if(e.shiftKey&&e.key==="Enter")openMod(c[sel].dataset.k);else c[sel].click()}});
// ---- Toggle GUI with Right Shift ----
document.addEventListener("keydown",e=>{
 if(e.code==="ShiftRight"){$("#gui").classList.toggle("hide");return}
 if(/INPUT|TEXTAREA/.test(e.target.tagName)||$("#gui").classList.contains("hide"))return;
 const k=Object.keys(binds).find(x=>binds[x]===e.key.toUpperCase());if(k){state[k]=!state[k];saveAll();if(MODULES[tab]||query)render(true);toast((state[k]?"Enabled ":"Disabled ")+k.split(".")[1])}
});
// stars
for(let i=0;i<14;i++){const s=document.createElement("i");s.style.left=Math.random()*100+"%";s.style.top=Math.random()*100+"%";s.style.opacity=.2+Math.random()*.5;$("#stars").appendChild(s)}
setTheme(THEMES[theme]?theme:"Indigo");applyUI();render();
