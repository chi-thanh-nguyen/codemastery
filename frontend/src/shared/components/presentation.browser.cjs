// Optional local-browser verification using an explicitly supplied Playwright installation.
// No browser package is downloaded by this script. See the PR verification record.
// Real Course routes require a configured VITE_API_BASE_URL. A standalone /api
// setup with the optional sixth --standalone argument exercises API error views,
// not browser-to-backend integration. Normal mode verifies the deliberate malformed-ID
// 400 responses; standalone mode retains its exact expected API 404 checks.
// Append --test-api-errors to run only focused response/classifier browser regressions.
const {chromium,webkit} = require(process.argv[2] || 'playwright');
// Optional engine argument uses an installed browser; never downloads one.
const engine=process.argv[5] || 'chromium';
const standalone=process.argv[6] === '--standalone';
const fs = require('node:fs');
const assert = require('node:assert/strict');
const path=require('node:path');
const {createHash}=require('node:crypto');
const out=path.resolve(process.argv[3] || path.join(require('node:os').tmpdir(),'codemastery-fe01-evidence'));
fs.mkdirSync(out,{recursive:true});
const previewUrl=process.argv[4] || 'http://127.0.0.1:5173/';
function isMalformedDetailRequest(request){
 const url=new URL(request.url());
 return request.method()==='GET'&&!request.isNavigationRequest()&&url.pathname==='/api/courses/sample'&&!url.search;
}
function createConsoleRecorder(standalone,previewUrl){
 const consoleErrors=[],consoleWarnings=[],expectedApiConsoleErrors=[],verifiedMalformedIdResponses=[];
 let malformedRead=null;
 function recordConsole(message){
  if(message.type()==='error'){
   const location=message.location().url;
   const url=location&&new URL(location);
   const text=message.text();
   // Defer only this read's exact 400 message until its HTTP responses are verified.
   if(!standalone&&malformedRead&&message.page()===malformedRead.page&&url&&
    url.pathname==='/api/courses/sample'&&!url.search&&
    text==='Failed to load resource: the server responded with a status of 400 (Bad Request)'){
    malformedRead.messages.push({url:location,message:text});return;
   }
   const expected=standalone&&url&&url.origin===new URL(previewUrl).origin&&
    (url.pathname==='/api/courses/sample'&&!url.search||url.pathname==='/api/courses'&&url.search==='?page=0&size=20')&&
    text==='Failed to load resource: the server responded with a status of 404 (Not Found)';
   if(expected)expectedApiConsoleErrors.push({url:location,message:text});else consoleErrors.push(text);
  }
  if(message.type()==='warning')consoleWarnings.push(message.text());
 }
 async function readMalformedDetail(page,navigate){
  if(standalone)return navigate();
  assert.equal(malformedRead,null,'Malformed-ID checks must run sequentially');
  const read={page,responses:[],messages:[]};malformedRead=read;
  const recordResponse=response=>{if(isMalformedDetailRequest(response.request()))read.responses.push({url:response.url(),method:response.request().method(),status:response.status()})};
  page.on('response',recordResponse);
  try{
   await Promise.all([page.waitForResponse(response=>isMalformedDetailRequest(response.request())),navigate()]);
   assert(read.responses.length>0,'Observe the deliberate malformed-ID request');
   const remaining=new Map();
   for(const response of read.responses){
    assert.equal(response.status,400,`${response.method} ${response.url}: malformed UUID must return HTTP 400`);
    remaining.set(response.url,(remaining.get(response.url)||0)+1);
   }
   for(const message of read.messages){
    const count=remaining.get(message.url)||0;
    assert(count>0,`No matching HTTP 400 response for console message at ${message.url}`);
    remaining.set(message.url,count-1);
   }
   verifiedMalformedIdResponses.push(...read.responses);expectedApiConsoleErrors.push(...read.messages);
  }finally{page.off('response',recordResponse);malformedRead=null;}
 }
 return {consoleErrors,consoleWarnings,expectedApiConsoleErrors,verifiedMalformedIdResponses,recordConsole,readMalformedDetail};
}

async function runApiErrorRegressions(context,check){
 const page=await context.newPage(),uncaught=[];
 const endpoint=new URL('/api/courses/sample',previewUrl).href,statuses=new Map([[endpoint,400]]);
 page.on('pageerror',error=>uncaught.push(String(error)));
 await page.route(url=>url.origin===new URL(previewUrl).origin&&url.pathname.startsWith('/api/'),route=>{
  const status=statuses.get(route.request().url())||400;
  return route.fulfill({status,contentType:'application/problem+json',body:JSON.stringify({type:'about:blank',title:{200:'OK',400:'Bad Request',404:'Not Found',500:'Internal Server Error'}[status],status,detail:'The request is invalid.',instance:new URL(route.request().url()).pathname})});
 });
 const fetchResponse=async(url=endpoint,method='GET')=>{
  const consoleMessage=(statuses.get(url)||400)>=400?page.waitForEvent('console',{predicate:message=>message.type()==='error'&&message.location().url===url}):Promise.resolve();
  await Promise.all([consoleMessage,page.evaluate(async({url,method})=>{await(await fetch(url,{method})).text()},{url,method})]);
 };
 const withRecorder=async(mode,run)=>{
  const recorder=createConsoleRecorder(mode,previewUrl);page.on('console',recorder.recordConsole);
  try{await run(recorder);}finally{page.off('console',recorder.recordConsole);}
 };
 try{
  await check('normal mode verifies the malformed-ID navigation and reload HTTP 400s',()=>withRecorder(false,async recorder=>{
   await recorder.readMalformedDetail(page,()=>page.goto(new URL('/courses/sample',previewUrl).href,{waitUntil:'networkidle'}));
   await recorder.readMalformedDetail(page,()=>page.reload({waitUntil:'networkidle'}));
   assert(await page.getByRole('heading',{name:'Could not load course',exact:true}).isVisible());
   assert(recorder.verifiedMalformedIdResponses.length>=2);assert(recorder.expectedApiConsoleErrors.length>=2);
   assert(recorder.verifiedMalformedIdResponses.every(response=>response.method==='GET'&&response.url===endpoint&&response.status===400));
   assert.deepEqual(recorder.consoleErrors,[]);
  }));
  for(const status of [400,404,500])await check(`normal mode retains unrelated HTTP ${status} during the expected read`,()=>withRecorder(false,async recorder=>{
   const unrelated=new URL('/api/courses/unrelated',previewUrl).href;statuses.set(unrelated,status);
   await recorder.readMalformedDetail(page,async()=>{await fetchResponse(unrelated);await fetchResponse();});
   assert.equal(recorder.consoleErrors.length,1);assert.equal(recorder.expectedApiConsoleErrors.length,1);
  }));
  for(const status of [200,404,500])await check(`normal mode rejects HTTP ${status} for the malformed-ID request`,()=>withRecorder(false,async recorder=>{
   statuses.set(endpoint,status);
   try{await assert.rejects(recorder.readMalformedDetail(page,()=>fetchResponse()),/malformed UUID must return HTTP 400/);}
   finally{statuses.set(endpoint,400);}
   assert.equal(recorder.expectedApiConsoleErrors.length,0);
  }));
  await check('normal mode retains the same URL HTTP 400 outside the deliberate read',()=>withRecorder(false,async recorder=>{
   await fetchResponse();assert.equal(recorder.consoleErrors.length,1);assert.equal(recorder.expectedApiConsoleErrors.length,0);
  }));
  await check('normal mode rejects an extra POST HTTP 400 at the same URL',()=>withRecorder(false,async recorder=>{
   await assert.rejects(recorder.readMalformedDetail(page,async()=>{await fetchResponse(endpoint,'POST');await fetchResponse();}),/No matching HTTP 400 response/);
  }));
  await check('normal mode retains a query-bearing HTTP 400 at the same path',()=>withRecorder(false,async recorder=>{
   await recorder.readMalformedDetail(page,async()=>{await fetchResponse(endpoint+'?unexpected=1');await fetchResponse();});
   assert.equal(recorder.consoleErrors.length,1);
  }));
  await check('normal mode retains ordinary console errors during the expected read',()=>withRecorder(false,async recorder=>{
   await recorder.readMalformedDetail(page,async()=>{await page.evaluate(()=>console.error('Unexpected classifier regression sentinel'));await fetchResponse();});
   assert.deepEqual(recorder.consoleErrors,['Unexpected classifier regression sentinel']);
  }));
  await check('standalone mode preserves exactly two detail and one catalogue HTTP 404 exceptions',()=>withRecorder(true,async recorder=>{
   const catalogue=new URL('/api/courses?page=0&size=20',previewUrl).href;statuses.set(endpoint,404);statuses.set(catalogue,404);
   try{await recorder.readMalformedDetail(page,()=>fetchResponse());await recorder.readMalformedDetail(page,()=>fetchResponse());await fetchResponse(catalogue);}
   finally{statuses.set(endpoint,400);}
   assert.equal(recorder.expectedApiConsoleErrors.length,3);assert.deepEqual(recorder.consoleErrors,[]);assert.equal(recorder.verifiedMalformedIdResponses.length,0);
  }));
  for(const status of [400,404,500])await check(`standalone mode retains unrelated HTTP ${status}`,()=>withRecorder(true,async recorder=>{
   const unrelated=new URL('/api/courses/unrelated',previewUrl).href;statuses.set(unrelated,status);await fetchResponse(unrelated);
   assert.equal(recorder.consoleErrors.length,1);assert.equal(recorder.expectedApiConsoleErrors.length,0);
  }));
  for(const status of [400,500])await check(`standalone mode retains HTTP ${status} at the malformed-ID URL`,()=>withRecorder(true,async recorder=>{
   statuses.set(endpoint,status);try{await fetchResponse();}finally{statuses.set(endpoint,400);}
   assert.equal(recorder.consoleErrors.length,1);assert.equal(recorder.expectedApiConsoleErrors.length,0);
  }));
  await check('focused response/classifier regressions have no uncaught exceptions',async()=>assert.deepEqual(uncaught,[]));
 }finally{await page.close();}
}

let browser;
(async()=>{
 assert(['chromium','webkit'].includes(engine),'Expected chromium or webkit');
 browser=await ({chromium,webkit}[engine]).launch({headless:true});
 const context=await browser.newContext({viewport:{width:1440,height:900},deviceScaleFactor:1});
 const page=await context.newPage();
 // Vite's MUI development graph can exceed the browser's default resource-timing buffer.
 await page.addInitScript(()=>performance.setResourceTimingBufferSize(4000));
 const errors=[];const origins=new Set();page.on('request',r=>origins.add(new URL(r.url()).origin));page.on('pageerror',e=>errors.push(String(e)));
 const {consoleErrors,consoleWarnings,expectedApiConsoleErrors,verifiedMalformedIdResponses,recordConsole,readMalformedDetail}=createConsoleRecorder(standalone,previewUrl);
 page.on('console',recordConsole);
 const checks=[];
 async function check(name,fn){await fn();checks.push({name,passed:true});console.log('PASS',name)}
 if(process.argv.includes('--test-api-errors')){
  await runApiErrorRegressions(context,check);
  const report={engine,browser:browser.version(),fixture:'Controlled HTTP responses; not live backend integration',checks};
  fs.writeFileSync(`${out}/api-error-regressions.json`,JSON.stringify(report,null,2));await browser.close();
  console.log(JSON.stringify({passed:checks.length,browser:report.browser,report:`${out}/api-error-regressions.json`}));return;
 }
 function contrast(a,b){const lum=s=>{const v=s.match(/[\d.]+/g).slice(0,3).map(Number).map(x=>{x/=255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4});return v[0]*.2126+v[1]*.7152+v[2]*.0722};const [l,h]=[lum(a),lum(b)].sort((a,b)=>a-b);return(h+.05)/(l+.05)}
 async function colors(locator){return locator.evaluate(e=>{const s=getComputedStyle(e);return {color:s.color,background:s.backgroundColor,outline:s.outline,outlineWidth:s.outlineWidth,transition:s.transitionDuration}})}
 const canonicalArtwork=fs.readFileSync(path.resolve(__dirname,'../../../public/favicon.svg'));
 const canonicalIcon=fs.readFileSync(path.resolve(__dirname,'../../../public/favicon.ico'));
 const branding=[];let brandUrl,brandRatio,faviconUrl;
 async function verifyBrandLogo(locator,height,decorative){
  assert.equal(await locator.count(),1);await locator.evaluate(image=>image.decode());
  const rendered=await locator.evaluate(image=>{
   const s=getComputedStyle(image),b=image.getBoundingClientRect(),p=image.parentElement.getBoundingClientRect();
   return {src:image.currentSrc,alt:image.alt,hidden:image.getAttribute('aria-hidden'),width:b.width,height:b.height,
    background:s.backgroundColor,padding:s.padding,filter:s.filter,transform:s.transform,objectFit:s.objectFit,clipPath:s.clipPath,
    fits:b.left>=p.left&&b.right<=p.right+.1&&b.top>=p.top&&b.bottom<=p.bottom+.1,
    viewportFits:b.left>=0&&b.right<=innerWidth+.1&&b.top>=0&&b.bottom<=innerHeight+.1};
  });
  assert.equal(rendered.src,brandUrl);assert(Math.abs(rendered.height-height)<.1);
  assert(Math.abs(rendered.width/rendered.height-brandRatio)<.002,'Preserve canonical SVG aspect ratio');
  assert.equal(rendered.alt,decorative?'':'CodeMastery');assert.equal(rendered.hidden,decorative?'true':null);
  assert.equal(rendered.background,'rgba(0, 0, 0, 0)');assert.equal(rendered.padding,'0px');assert.equal(rendered.filter,'none');
  assert.equal(rendered.transform,'none');assert.equal(rendered.objectFit,'contain');assert.equal(rendered.clipPath,'none');assert(rendered.fits,'Logo must fit its container');assert(rendered.viewportFits,'Logo must be visible without viewport clipping');
  branding.push({route:new URL(page.url()).pathname,viewport:page.viewportSize(),...rendered});
 }
 await page.goto(previewUrl,{waitUntil:'networkidle'});
 await check('overview is explicitly demo data',async()=>{await page.getByRole('heading',{name:'Your learning, in focus.'}).waitFor();assert(await page.getByText('All learning data below is illustrative. Nothing is saved.').isVisible())});
 let faviconCaching;
 await check('multi-size favicon resolves, matches the canonical logo and revalidates its cache',async()=>{
  const favicon=page.locator('link[rel="icon"]');assert.equal(await favicon.count(),1);assert.equal(await favicon.getAttribute('type'),'image/x-icon');
  assert.equal(await favicon.getAttribute('sizes'),'16x16 32x32 48x48 64x64');
  faviconUrl=await favicon.evaluate(e=>e.href);assert.equal(new URL(faviconUrl).pathname,new URL('favicon.ico',previewUrl).pathname);
  assert.equal(new URL(faviconUrl).searchParams.get('v'),createHash('sha256').update(canonicalIcon).digest('hex').slice(0,12),'Favicon URL version must match its current contents');
  const response=await context.request.get(faviconUrl);assert(response.ok());assert.match(response.headers()['content-type'],/image\/(x-icon|vnd\.microsoft\.icon)/);
  assert((await response.body()).equals(canonicalIcon));
  assert.equal(canonicalIcon.readUInt16LE(0),0);assert.equal(canonicalIcon.readUInt16LE(2),1);assert.equal(canonicalIcon.readUInt16LE(4),4);
  for(const [i,size]of [16,32,48,64].entries()){
   const entry=6+i*16;assert.equal(canonicalIcon[entry],size);assert.equal(canonicalIcon[entry+1],size);
   const length=canonicalIcon.readUInt32LE(entry+8),offset=canonicalIcon.readUInt32LE(entry+12),png=canonicalIcon.subarray(offset,offset+length);
   assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
   const difference=await page.evaluate(async({svg,png,size})=>{
    const original=new Image(),raster=new Image();original.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svg);raster.src='data:image/png;base64,'+png;
    await Promise.all([original.decode(),raster.decode()]);
    if(raster.naturalWidth!==size||raster.naturalHeight!==size)throw new Error('Incorrect favicon frame size');
    const box=new DOMParser().parseFromString(svg,'image/svg+xml').documentElement.viewBox.baseVal,ratio=box.width/box.height;
    const pixels=(image,canonical)=>{const c=document.createElement('canvas');c.width=c.height=size;const ctx=c.getContext('2d');if(canonical)ctx.drawImage(image,(size-size*ratio)/2,0,size*ratio,size);else ctx.drawImage(image,0,0);return ctx.getImageData(0,0,size,size).data};
    const a=pixels(original,true),b=pixels(raster,false);let error=0,visible=0;for(let j=0;j<a.length;j++){error+=Math.abs(a[j]-b[j]);if(j%4===3&&b[j]>32)visible++;}
    if(visible<size)throw new Error('Favicon frame is blank');return error/a.length;
   },{svg:canonicalArtwork.toString('utf8'),png:png.toString('base64'),size});
   assert(difference<12,`${size}px favicon must preserve the canonical SVG artwork`);
  }
  brandUrl=new URL('favicon.svg',previewUrl).href;
  const original=await context.request.get(brandUrl);assert(original.ok());assert((await original.body()).equals(canonicalArtwork));
  brandRatio=await page.evaluate(async({url,source})=>{
   const image=new Image();image.src=url;await image.decode();
   const svg=new DOMParser().parseFromString(source,'image/svg+xml').documentElement,viewBox=svg.viewBox.baseVal;
   if(!image.naturalWidth||!image.naturalHeight)throw new Error('Canonical SVG did not decode');
   return viewBox.width/viewBox.height;
  },{url:brandUrl,source:canonicalArtwork.toString('utf8')});
  const headers=response.headers();faviconCaching={url:faviconUrl,source:brandUrl,cacheControl:headers['cache-control'],etag:headers.etag};
  if(headers.etag){const conditional=await context.request.get(faviconUrl,{headers:{'If-None-Match':headers.etag}});assert.equal(conditional.status(),304);faviconCaching.conditionalStatus=conditional.status();}
  const outdated=await context.request.get(faviconUrl,{headers:{'If-None-Match':'W/"previous-logo"'}});
  assert.equal(outdated.status(),200);assert((await outdated.body()).equals(canonicalIcon));faviconCaching.outdatedValidatorStatus=outdated.status();
 });
 await check('desktop sidebar uses canonical artwork without a background or duplicate accessible name',async()=>{
  await verifyBrandLogo(page.locator('.MuiDrawer-docked nav img'),52,true);
 });
 const typography=[];
 await check('self-hosted variable font loads real 400/500/600/700 weights',async()=>{
  const faces=await page.evaluate(async()=>{
   await document.fonts.ready;
   for(const weight of [400,500,600,700]){
    const loaded=await document.fonts.load(`${weight} 16px "Plus Jakarta Sans"`,'Learning 0123456789');
    if(!loaded.length||loaded.some(f=>f.status!=='loaded'))throw new Error(`Font weight ${weight} unavailable`);
   }
   return [...document.fonts].map(f=>({family:f.family,weight:f.weight,status:f.status}));
  });
  assert(faces.some(f=>f.family==='Plus Jakarta Sans'&&f.weight==='200 800'&&f.status==='loaded'));
  const fontResponses=await page.evaluate(()=>performance.getEntriesByType('resource').filter(e=>e.name.includes('.woff2')).map(e=>({url:e.name,size:e.decodedBodySize})));
  assert(fontResponses.some(f=>new URL(f.url).origin===new URL(previewUrl).origin&&f.size>0));
  const license=await context.request.get(new URL('plus-jakarta-sans-OFL.txt',previewUrl).href);
  assert(license.ok());const licenseText=await license.text();
  assert(licenseText.includes('Copyright 2020 The Plus Jakarta Sans Project Authors'));
  assert(licenseText.includes('SIL OPEN FONT LICENSE Version 1.1'));
 });
 await check('representative text uses the loaded font and shared hierarchy',async()=>{
  const roles=[['page heading','main h1',30,500],['workspace title','header .MuiTypography-h5',22,600],
   ['selected navigation','.MuiDrawer-docked [aria-current] .MuiTypography-root',15,500],['section heading','#learning-rhythm-title',20,600],
   ['KPI value','main p:text-is("4h 20m")',32,700],['supporting caption','main :text-is("Illustrative weekly total")',13,400],
   ['course title','main h3',20,600],['table lesson','tbody .MuiTypography-body2',14,500],
   ['action','main button:text-is("Explore components")',14,500],['search','input[aria-label="Search sample lessons"]',14,400],
   ['account navigation','nav[aria-label="Account navigation"] a',14,500]];
  const cdp=engine==='chromium'?await context.newCDPSession(page):null;
  if(cdp){await cdp.send('DOM.enable');await cdp.send('CSS.enable');}
  for(const [role,selector,size,weight]of roles){
   const loc=page.locator(selector).first();
   const style=await loc.evaluate(e=>{const s=getComputedStyle(e);return {family:s.fontFamily,size:parseFloat(s.fontSize),weight:Number(s.fontWeight),lineHeight:s.lineHeight,letterSpacing:s.letterSpacing}});
   // WebKit omits quotes around multiword computed family names; normalize serialization.
   assert.equal(style.family.split(',')[0].trim().replace(/^["']|["']$/g,''),'Plus Jakarta Sans',role);
   assert.equal(style.size,size,role);assert.equal(style.weight,weight,role);
   if(cdp){
    await loc.evaluate(e=>e.setAttribute('data-font-inspection','current'));
    const {root}=await cdp.send('DOM.getDocument');const {nodeId}=await cdp.send('DOM.querySelector',{nodeId:root.nodeId,selector:'[data-font-inspection="current"]'});
    style.renderedFonts=(await cdp.send('CSS.getPlatformFontsForNode',{nodeId})).fonts;
    assert(style.renderedFonts.some(f=>f.familyName==='Plus Jakarta Sans'&&f.isCustomFont&&f.glyphCount>0),role);
    assert(style.renderedFonts.every(f=>f.familyName==='Plus Jakarta Sans'),`${role}: unintended fallback`);
    await loc.evaluate(e=>e.removeAttribute('data-font-inspection'));
   }
   typography.push({role,...style});
  }
  if(cdp)await cdp.detach();
 });
 await check('sample search animates a white surface and primary border on hover and focus without shifting layout',async()=>{
  const input=page.getByRole('textbox',{name:'Search sample lessons'});
  const root=input.locator('..');
  const rest=await root.evaluate(e=>{const s=getComputedStyle(e),line=getComputedStyle(e.querySelector('fieldset'));return {background:s.backgroundColor,boundary:parseFloat(line.borderTopWidth),height:e.getBoundingClientRect().height}});
  assert(rest.height>=44);assert.notEqual(rest.background,'rgb(255, 255, 255)');assert.equal(rest.boundary,2);
  assert.equal(await root.locator('fieldset').evaluate(e=>getComputedStyle(e).borderTopColor),'rgba(0, 0, 0, 0)');
  await input.hover();
  await page.waitForFunction(()=>{const root=document.querySelector('input[aria-label="Search sample lessons"]').closest('.MuiOutlinedInput-root');return getComputedStyle(root).backgroundColor==='rgb(255, 255, 255)'&&getComputedStyle(root.querySelector('fieldset')).borderTopColor==='rgb(31, 107, 232)'});
  assert.equal(await root.evaluate(e=>e.getBoundingClientRect().height),rest.height);
  assert(parseFloat((await colors(root)).transition)>0);
  await input.focus();
  await page.mouse.move(0,0);
  const focus=await colors(root);const boundary=await root.locator('fieldset').evaluate(e=>{const s=getComputedStyle(e);return {width:parseFloat(s.borderTopWidth),color:s.borderTopColor}});
  assert.equal(boundary.width,2);assert(contrast(boundary.color,rest.background)>=3);
  for(const target of [input,root])assert.equal(await target.evaluate(e=>getComputedStyle(e).outlineStyle),'none');
  for(const pseudo of ['::before','::after'])assert.equal(await root.evaluate((e,p)=>getComputedStyle(e,p).content,pseudo),'none');
  assert(contrast(focus.color,focus.background)>=4.5);
  await input.evaluate(e=>e.blur());
  await page.waitForFunction(()=>{const root=document.querySelector('input[aria-label="Search sample lessons"]').closest('.MuiOutlinedInput-root');return getComputedStyle(root).backgroundColor==='rgb(245, 246, 248)'&&getComputedStyle(root.querySelector('fieldset')).borderTopColor==='rgba(0, 0, 0, 0)'});
 });
 await check('dashboard uses flat surfaces, readable colorful metrics and an unshadowed chart',async()=>{
  assert.equal(await page.locator('#main-content').evaluate(e=>getComputedStyle(e).backgroundColor),'rgb(255, 255, 255)');
  assert.equal(await page.locator('.MuiDrawer-docked nav').evaluate(e=>getComputedStyle(e).backgroundColor),'rgb(255, 255, 254)');
  const frame=page.locator('.MuiDrawer-docked').locator('..');
  for(const surface of [frame,frame.locator('..')]){
   const style=await surface.evaluate(e=>{const s=getComputedStyle(e);return {shadow:s.boxShadow,image:s.backgroundImage}});
   assert.equal(style.shadow,'none');assert.equal(style.image,'none');
  }
  const accents=new Set();
  for(const label of ['Study time','Lessons completed','Practice streak','Course progress']){
   const metric=await page.getByText(label,{exact:true}).evaluate(e=>{
    const card=e.closest('.MuiPaper-root'),icon=card.querySelector('svg').parentElement,c=getComputedStyle(card),i=getComputedStyle(icon);
    return {shadow:c.boxShadow,background:c.backgroundColor,icon:i.color,accent:i.backgroundColor,iconShadow:i.boxShadow,iconBorder:parseFloat(i.borderTopWidth),iconHidden:card.querySelector('svg').getAttribute('aria-hidden'),label:getComputedStyle(e).color,
     text:getComputedStyle(card.querySelector('.MuiTypography-caption')).color};
   });
   assert.equal(metric.shadow,'none');assert.equal(metric.background,'rgb(255, 255, 255)');
   // Decorative glyphs use the reference palette; visible labels and captions carry the information.
   assert.equal(metric.iconHidden,'true');assert(contrast(metric.label,metric.background)>=4.5);assert(contrast(metric.text,metric.background)>=4.5);
   assert.equal(metric.iconBorder,1);assert.notEqual(metric.iconShadow,'none');
   assert.equal(metric.icon,'rgb(255, 255, 255)');
   accents.add(metric.accent);
  }
  assert.equal(accents.size,4);
  const tooltip=page.getByRole('group',{name:'Sample weekly study minutes'}).getByRole('tooltip');
  assert.equal(await tooltip.evaluate(e=>getComputedStyle(e).boxShadow),'none');
 });
 const layouts=[];
 for(const [width,height] of [[1440,900],[1024,768],[768,1024],[390,844],[320,844],[899,900],[900,900],[1199,900],[1200,900]]){
  await page.setViewportSize({width,height}); await page.waitForTimeout(150);
  const layout=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,client:document.documentElement.clientWidth,navs:[...document.querySelectorAll('nav')].filter(n=>n.getBoundingClientRect().width&&getComputedStyle(n).visibility!=='hidden'&&!n.closest('[aria-hidden="true"]')).length}));
  layouts.push({width,height,...layout});
  await check(`no page overflow at ${width}`,async()=>assert(layout.scroll<=layout.client,JSON.stringify(layout)));
  if([320,768,1440].includes(width))await check(`KPI grid adapts at ${width}`,async()=>{
   const positions=[];for(const t of ['Study time','Lessons completed','Practice streak','Course progress'])positions.push((await page.getByText(t,{exact:true}).boundingBox()).y);
   const rows=new Set(positions.map(y=>Math.round(y)));
   assert.equal(rows.size,width===320?4:width===768?2:1,JSON.stringify(positions));
  });
  if([1200,1440].includes(width))await check(`KPI cards align values and captions across wrapped labels at ${width}`,async()=>{
   const metrics=[];
   for(const label of ['Study time','Lessons completed','Practice streak','Course progress'])metrics.push(await page.getByText(label,{exact:true}).evaluate(e=>{
    const card=e.closest('.MuiPaper-root'),value=card.querySelector('.MuiTypography-h3'),caption=card.querySelector('.MuiTypography-caption');
    return {height:card.getBoundingClientRect().height,valueY:value.getBoundingClientRect().y,captionY:caption.getBoundingClientRect().y};
   }));
   for(const key of ['height','valueY','captionY'])assert(Math.max(...metrics.map(m=>m[key]))-Math.min(...metrics.map(m=>m[key]))<1,JSON.stringify(metrics));
   if(width===1440)assert(metrics.every(m=>m.height>=140&&m.height<=160),JSON.stringify(metrics));
  });
  await check(`navigation and heading text fit their containers at ${width}`,async()=>{
   for(const loc of await page.locator('header .MuiTypography-root, header a, main h1, main h2, main h3, .MuiDrawer-docked .MuiListItemText-root').all()){
    if(!await loc.isVisible())continue;
    const fits=await loc.evaluate(e=>e.scrollWidth<=e.clientWidth+1&&e.scrollHeight<=e.clientHeight+1);
    assert(fits,await loc.textContent());
   }
  });
  if([1440,1024,768,390,320].includes(width)){
   await page.evaluate(()=>{document.activeElement?.blur();document.querySelector('[aria-label="Sample lesson table"]').scrollLeft=0;window.scrollTo(0,0)});
   await page.screenshot({path:`${out}/overview-${width}.png`,fullPage:true});
  }
  if(width===320)await check('sample table remains keyboard-scrollable on narrow mobile',async()=>{const table=page.getByRole('region',{name:'Sample lesson table'});await table.focus();assert(await table.evaluate(e=>e===document.activeElement));/* WebKit's first arrow activates the focused scroll view. */await page.keyboard.press('ArrowRight');await page.waitForTimeout(250);await page.keyboard.press('ArrowRight');await page.waitForFunction(()=>document.querySelector('[aria-label="Sample lesson table"]').scrollLeft>0);assert(await table.evaluate(e=>e.scrollLeft>0));});
 }
 await page.setViewportSize({width:1440,height:900});
 const active=page.locator('.MuiDrawer-docked').getByRole('button',{name:'Overview',exact:true});
 const atRest=await colors(active);await active.hover();await page.waitForTimeout(250);const onHover=await colors(active);
 await check('selected navigation normal/hover contrast',async()=>{assert(contrast(atRest.color,atRest.background)>=4.5);assert(contrast(onHover.color,onHover.background)>=4.5)});
 await check('four KPI cards form a single desktop row',async()=>{
  const positions=[];for(const t of ['Study time','Lessons completed','Practice streak','Course progress'])positions.push((await page.getByText(t,{exact:true}).boundingBox()).y);
  assert(Math.max(...positions)-Math.min(...positions)<3,JSON.stringify(positions));
 });
 await check('KPI icons retain exact palette, raised surface and geometry across color preferences and viewport sizes',async()=>{
  const palette=[['Study time','rgb(0, 123, 255)'],['Lessons completed','rgb(1, 208, 18)'],['Practice streak','rgb(255, 0, 0)'],['Course progress','rgb(123, 52, 255)']];
  try{
   for(const colorScheme of ['light','dark',null])for(const width of [390,1440]){
    await page.emulateMedia({colorScheme});await page.setViewportSize({width,height:900});
    for(const [label,expected] of palette){
     const actual=await page.getByText(label,{exact:true}).evaluate(e=>{
      const icon=e.closest('.MuiPaper-root').querySelector('svg'),tile=icon.parentElement,s=getComputedStyle(tile),r=tile.getBoundingClientRect();
      const modifiers=[];for(let parent=tile;parent;parent=parent.parentElement){const p=getComputedStyle(parent);if(p.opacity!=='1'||p.filter!=='none'||p.backdropFilter!=='none'||p.mixBlendMode!=='normal')modifiers.push(parent.tagName);}
      return{background:s.backgroundColor,image:s.backgroundImage,border:s.borderTopColor,borderWidth:s.borderTopWidth,shadow:s.boxShadow,color:getComputedStyle(icon).color,stroke:getComputedStyle(icon).stroke,width:r.width,height:r.height,radius:s.borderRadius,iconSize:getComputedStyle(icon).fontSize,modifiers};
     });
     assert.equal(actual.background,expected,label);assert.equal(actual.border,expected,label);
     assert.equal(actual.color,'rgb(255, 255, 255)',label);assert.equal(actual.stroke,actual.color,label);
     assert.equal(actual.image,'none',label);assert(actual.shadow.includes('inset'),label);assert.deepEqual(actual.modifiers,[],label);
     assert.equal(actual.width,40,label);assert.equal(actual.height,40,label);assert.equal(actual.radius,'10px',label);assert.equal(actual.borderWidth,'1px',label);assert.equal(actual.iconSize,'23px',label);
    }
   }
  }finally{await page.emulateMedia({colorScheme:null});await page.setViewportSize({width:1440,height:900});}
 });
 await check('chart value boundaries and progress indicators meet non-text contrast',async()=>{
  const chart=page.getByRole('group',{name:'Sample weekly study minutes'}),bars=chart.locator('[data-study-bar]');
  const grid=await chart.locator(':scope > div').nth(1).locator(':scope > div').first().locator('div').first().evaluate(e=>getComputedStyle(e).borderTopColor);
  assert.equal(await bars.count(),7);
  for(const bar of await bars.all()){
   const s=await bar.evaluate(e=>{const s=getComputedStyle(e);return {border:s.borderTopColor,borderWidth:parseFloat(s.borderTopWidth),fill:s.backgroundColor,gradient:s.backgroundImage}});
   assert(s.borderWidth>=2);assert(contrast(s.border,'rgb(255,255,255)')>=3);
   assert(s.gradient.startsWith('linear-gradient('));
   const stops=s.gradient.match(/rgb\([^)]+\)/g);assert.equal(stops?.length,2);
   assert(contrast(stops[0],'rgb(255,255,255)')>contrast(stops[1],'rgb(255,255,255)'),'Sky blue must fade toward a lighter bottom');
   // The cap marks the bar against the canvas/grid; it need not contrast with its own fill.
   assert(contrast(s.border,grid)>=3);
  }
  const progress=page.getByRole('progressbar',{name:'Sample course completion',exact:true});const background=await progress.evaluate(e=>getComputedStyle(e).backgroundColor);const fill=await progress.locator('.MuiLinearProgress-bar').evaluate(e=>getComputedStyle(e).backgroundColor);assert(contrast(fill,background)>=3);
 });
 await check('light chart tooltip is readable and selection has a non-color marker',async()=>{
  const chart=page.getByRole('group',{name:'Sample weekly study minutes'}),tooltip=chart.getByRole('tooltip');
  const s=await colors(tooltip);assert.equal(s.background,'rgb(255, 255, 255)');assert(contrast(s.color,s.background)>=4.5);
  const selected=chart.locator('button[aria-pressed="true"]');
  assert.equal(await selected.count(),1);assert((await selected.locator('.MuiTypography-root').evaluate(e=>getComputedStyle(e).textDecorationLine)).includes('underline'));
 });
 await check('chart exposes exact sample values, totals and a proportional scale',async()=>{
  const chart=page.getByRole('group',{name:'Sample weekly study minutes'});
  const minutes=[30,45,25,60,40,35,25];const bars=chart.locator('[data-study-bar]');
  const heights=await bars.evaluateAll(nodes=>nodes.map(n=>n.getBoundingClientRect().height));
  minutes.forEach((value,i)=>assert(Math.abs(heights[i]/heights[3]-value/60)<.02));
  assert.equal(minutes.reduce((a,b)=>a+b),260);assert(await page.getByText('4h 20m',{exact:true}).isVisible());
  for(const [i,day] of ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].entries())assert.equal(await chart.getByRole('button',{name:`${day}: ${minutes[i]} sample study minutes`,exact:true}).count(),1);
 });
 await check('chart supports keyboard selection, visible focus and a named tooltip',async()=>{
  const chart=page.getByRole('group',{name:'Sample weekly study minutes'}),thu=chart.getByRole('button',{name:'Thu: 60 sample study minutes',exact:true});
  await thu.focus();await page.keyboard.press('ArrowRight');
  const fri=chart.getByRole('button',{name:'Fri: 40 sample study minutes',exact:true});assert(await fri.evaluate(e=>e===document.activeElement));assert.equal(await fri.getAttribute('aria-pressed'),'true');
  assert.equal(await chart.getByRole('tooltip').textContent(),'Fri · 40 min');assert(parseFloat((await colors(fri)).outlineWidth)>0);
  await page.keyboard.press('Escape');assert.equal(await chart.getByRole('tooltip').count(),0);assert.equal(await fri.getAttribute('aria-label'),'Fri: 40 sample study minutes');
  await page.keyboard.press('Home');assert.equal(await chart.getByRole('button',{name:'Mon: 30 sample study minutes',exact:true}).getAttribute('aria-pressed'),'true');
  await page.keyboard.press('End');assert.equal(await chart.getByRole('button',{name:'Sun: 25 sample study minutes',exact:true}).getAttribute('aria-pressed'),'true');
 });
 await check('chart hover previews a day without replacing keyboard selection',async()=>{
  const chart=page.getByRole('group',{name:'Sample weekly study minutes'});
  await chart.getByRole('button',{name:'Tue: 45 sample study minutes',exact:true}).hover();assert.equal(await chart.getByRole('tooltip').textContent(),'Tue · 45 min');
  const tue=await chart.getByRole('button',{name:'Tue: 45 sample study minutes',exact:true}).boundingBox();
  const wed=await chart.getByRole('button',{name:'Wed: 25 sample study minutes',exact:true}).boundingBox();
  await page.mouse.move((tue.x+tue.width+wed.x)/2,tue.y+tue.height/2);
  assert.equal(await chart.getByRole('tooltip').textContent(),'Tue · 45 min','Moving through the column gap must retain the hover preview');
  await chart.getByRole('button',{name:'Wed: 25 sample study minutes',exact:true}).hover();assert.equal(await chart.getByRole('tooltip').textContent(),'Wed · 25 min');
  assert.equal(await chart.getByRole('button',{name:'Sun: 25 sample study minutes',exact:true}).getAttribute('aria-pressed'),'true');
  await page.getByRole('heading',{name:'Learning rhythm',exact:true}).hover();assert.equal(await chart.getByRole('tooltip').textContent(),'Sun · 25 min');
 });
 await check('outside clicks clear chart selection and tooltip, with pointer and keyboard reselection',async()=>{
  const chart=page.getByRole('group',{name:'Sample weekly study minutes'}),tue=chart.getByRole('button',{name:'Tue: 45 sample study minutes',exact:true});
  await page.getByRole('textbox',{name:'Search sample lessons'}).click();
  assert.equal(await chart.locator('[aria-pressed="true"]').count(),0);assert.equal(await chart.getByRole('tooltip').count(),0);
  await tue.click();assert.equal(await tue.getAttribute('aria-pressed'),'true');assert.equal(await chart.getByRole('tooltip').textContent(),'Tue · 45 min');
  await page.getByText('Study time',{exact:true}).click();assert.equal(await chart.locator('[aria-pressed="true"]').count(),0);assert.equal(await chart.getByRole('tooltip').count(),0);
  await tue.focus();await page.keyboard.press('ArrowRight');assert.equal(await chart.getByRole('tooltip').textContent(),'Wed · 25 min');
 });
 await check('search filters the local sample table after debounce',async()=>{
  await page.getByRole('textbox',{name:'Search sample lessons'}).fill('decisions');await page.waitForTimeout(450);
  assert.equal(await page.getByRole('cell',{name:'Making decisions Control flow'}).count(),1);
  assert.equal(await page.getByText('Thinking in variables',{exact:true}).count(),0);
  await page.getByRole('textbox',{name:'Search sample lessons'}).fill('');await page.waitForTimeout(450);
 });
 await check('header search is single, local, keyboard-clearable and focuses results on Enter',async()=>{
  const search=page.getByRole('textbox',{name:'Search sample lessons'});assert.equal(await search.count(),1);
  await search.fill('variables');await search.press('Escape');assert.equal(await search.inputValue(),'');await page.waitForTimeout(450);
  await search.fill('no such lesson');await page.waitForTimeout(450);assert(await page.getByRole('heading',{name:'No sample lessons match',exact:true}).isVisible());
  await page.getByRole('button',{name:'Clear filters',exact:true}).click();await page.waitForTimeout(450);assert.equal(await search.inputValue(),'');
  assert(await search.evaluate(e=>e===document.activeElement),'Clearing empty results must return focus to search');
  await search.fill('variables');const clear=page.getByRole('button',{name:'Clear sample lesson search',exact:true});await clear.focus();await page.keyboard.press('Enter');
  assert(await search.evaluate(e=>e===document.activeElement),'Removing the clear button must preserve search focus');
  await search.press('Enter');assert(await page.locator('#sample-learning-path').evaluate(e=>e===document.activeElement));
 });
 await check('adaptive explanation is explicitly static sample content',async()=>assert(await page.getByText('Illustration only. No live mastery calculation is performed.',{exact:true}).isVisible()));
 await check('tabs filter sample rows without shrinking the learning path',async()=>{
  const section=page.locator('#sample-learning-path');const height=await section.evaluate(e=>e.getBoundingClientRect().height);
  await page.getByRole('tab',{name:'Completed',exact:true}).click();assert.equal(await page.getByText('Thinking in variables',{exact:true}).count(),1);assert.equal(await page.getByText('Making decisions',{exact:true}).count(),0);
  assert(Math.abs(await section.evaluate(e=>e.getBoundingClientRect().height)-height)<1);
  await page.getByRole('tab',{name:'All lessons',exact:true}).click();
 });
 await check('sample lesson control opens an honest accessible dialog',async()=>{
  await page.getByRole('button',{name:'Preview sample lesson',exact:true}).click();const dialog=page.getByRole('dialog',{name:'Sample lesson preview'});await dialog.waitFor();
  assert(await dialog.getAttribute('aria-describedby'));await page.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});
 });
 await page.getByRole('button',{name:'Explore components',exact:true}).click();
 await check('outlined action keeps AA contrast immediately after view change and hover',async()=>{
  const action=page.getByRole('button',{name:'Reset sample controls',exact:true});
  async function ratio(){return action.evaluate(e=>{
   function rgba(s){return s.match(/[\d.]+/g).map(Number)}
   let rgb=[0,0,0],alpha=0,node=e;
   while(node&&alpha<1){const c=rgba(getComputedStyle(node).backgroundColor);const a=c[3]??1;rgb=rgb.map((v,i)=>v+c[i]*a*(1-alpha));alpha+=a*(1-alpha);node=node.parentElement}
   rgb=rgb.map(v=>v+255*(1-alpha));
   const lum=c=>c.map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4}).reduce((v,x,i)=>v+x*[.2126,.7152,.0722][i],0);
   const [l,h]=[lum(rgba(getComputedStyle(e).color).slice(0,3)),lum(rgb)].sort((a,b)=>a-b);return(h+.05)/(l+.05);
  })}
  assert(await ratio()>=4.5);await action.hover();await page.waitForTimeout(250);assert(await ratio()>=4.5);
 });
 await page.screenshot({path:`${out}/components-1440.png`,fullPage:true});
 const focused=page.locator('.MuiDrawer-docked [aria-current="true"]');
 await page.keyboard.press('Tab');await focused.focus();await page.waitForTimeout(250);const onFocus=await colors(focused);
 await check('keyboard focus remains visible with readable selected text',async()=>{assert(contrast(onFocus.color,onFocus.background)>=4.5);assert(!onFocus.outline.startsWith('none'));assert(parseFloat(onFocus.outlineWidth)>0,onFocus.outline)});
 const badge=page.locator('.MuiDrawer-docked [aria-current="true"]').getByText('0',{exact:true});
 const badgeColors=await colors(badge);
 await check('zero badge has a container and readable selected contrast',async()=>{assert.equal(await badge.evaluate(e=>e.tagName),'SPAN');assert(contrast(badgeColors.color,badgeColors.background)>=4.5)});
 await check('named indeterminate loader has one associated status message',async()=>{
  const loader=page.getByRole('progressbar',{name:'Loading sample content...'});assert.equal(await loader.count(),1);
  assert.equal(await loader.getAttribute('aria-valuenow'),null);const id=await loader.getAttribute('aria-labelledby');assert.equal(await page.locator(`[id="${id}"]`).getAttribute('role'),'status');
 });
 await check('destructive confirmation starts on cancel, traps focus and restores it',async()=>{
  const trigger=page.getByRole('button',{name:'Open confirmation',exact:true});await trigger.click();
  const dialog=page.getByRole('dialog',{name:'Reset sample controls?'});await dialog.waitFor();
  await page.waitForFunction(()=>document.activeElement?.textContent==='Keep editing');
  await page.keyboard.press('Shift+Tab');assert(await dialog.evaluate(e=>e.contains(document.activeElement)));
  await page.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert(await trigger.evaluate(e=>e===document.activeElement));
 });
 await check('retry invokes a local callback',async()=>{await page.getByRole('button',{name:'Try preview again'}).click();assert(await page.getByText('Preview retry callback received.').isVisible())});
 await page.setViewportSize({width:390,height:844});
 await page.screenshot({path:`${out}/components-390.png`,fullPage:true});
 for(const width of [320,390,1024]){await page.setViewportSize({width,height:844});await check(`component studio has no page overflow at ${width}`,async()=>assert(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth)));}
 await page.setViewportSize({width:390,height:844});
 await check('mobile navigation opens, exposes current item and restores focus on Escape',async()=>{
  const toggle=page.getByRole('button',{name:'Toggle navigation'});await toggle.click();
  const nav=page.getByRole('navigation',{name:'Main navigation'});await nav.waitFor();assert.equal(await nav.locator('[aria-current="true"]').count(),1);
  await page.waitForFunction(()=>{const paper=document.querySelector('.MuiDrawer-modal .MuiDrawer-paper');return paper&&Math.abs(paper.getBoundingClientRect().left)<.5});
  await verifyBrandLogo(nav.locator('img'),52,true);
  await page.screenshot({path:`${out}/drawer-390.png`,fullPage:true});await page.keyboard.press('Escape');await nav.waitFor({state:'hidden'});
  assert(await toggle.evaluate(e=>e===document.activeElement));
 });
 await check('mobile selection closes drawer and changes controlled view',async()=>{
  await page.getByRole('button',{name:'Toggle navigation'}).click();await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Overview',exact:true}).click();
  await page.getByRole('heading',{name:'Your learning, in focus.'}).waitFor();assert.equal(await page.getByRole('button',{name:'Toggle navigation'}).getAttribute('aria-expanded'),'false');
 });
 await check('open mobile drawer closes when crossing desktop breakpoint',async()=>{
  await page.setViewportSize({width:899,height:900});await page.getByRole('button',{name:'Toggle navigation'}).click();await page.setViewportSize({width:900,height:900});await page.waitForTimeout(300);
  assert.equal(await page.locator('.MuiDrawer-modal').isVisible(),false);assert.equal(await page.getByRole('navigation',{name:'Main navigation'}).count(),1);
 });
 await check('auth layout preview is reachable and returns to overview',async()=>{
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Auth layout preview',exact:true}).click();await page.getByRole('heading',{name:'A calm space to start.'}).waitFor();
  await verifyBrandLogo(page.getByRole('img',{name:'CodeMastery',exact:true}),64,false);
  await page.screenshot({path:`${out}/auth-900.png`,fullPage:true});await page.setViewportSize({width:320,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth));await page.screenshot({path:`${out}/auth-320.png`,fullPage:true});await page.getByRole('button',{name:'Back to demo workspace'}).click();
 });
 await check('reduced motion suppresses long animation/transition',async()=>{
  await page.emulateMedia({reducedMotion:'reduce'});await page.getByRole('button',{name:'Explore components',exact:true}).click();
  const duration=await page.getByRole('progressbar',{name:'Loading sample content...'}).evaluate(e=>getComputedStyle(e).animationDuration);assert(duration.split(',').every(x=>parseFloat(x)<.01),duration);
 });
 await check('skip link moves keyboard focus to main content',async()=>{
  await page.goto(previewUrl,{waitUntil:'networkidle'});const skip=page.getByRole('link',{name:'Skip to content',exact:true});
  await skip.focus();assert(await skip.isVisible());await page.keyboard.press('Enter');assert(await page.locator('#main-content').evaluate(e=>e===document.activeElement));
 });
 await check('account navigation preserves real sign-in/register routes and browser history',async()=>{
  const account=page.getByRole('navigation',{name:'Account navigation'});
  await account.getByRole('link',{name:'Sign in',exact:true}).click();await page.getByRole('heading',{name:'Welcome back',exact:true}).waitFor();assert.equal(new URL(page.url()).pathname,'/login');
  await page.goBack();await page.getByRole('heading',{name:'Your learning, in focus.',exact:true}).waitFor();
  await page.getByRole('navigation',{name:'Account navigation'}).getByRole('link',{name:'Register',exact:true}).click();await page.getByRole('heading',{name:'Create account',exact:true}).waitFor();assert.equal(new URL(page.url()).pathname,'/register');
 });
 await check('real login and registration share canonical branding at desktop and mobile sizes',async()=>{
  for(const route of ['/login','/register'])for(const width of [1440,390,320]){
   await page.setViewportSize({width,height:900});await page.goto(new URL(route,previewUrl).href,{waitUntil:'networkidle'});await page.evaluate(()=>document.fonts.ready);
   await verifyBrandLogo(page.getByRole('img',{name:'CodeMastery',exact:true}),64,false);
   assert.equal(await page.locator('link[rel="icon"]').evaluate(e=>e.href),faviconUrl);
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth));
   await page.screenshot({path:`${out}/${route.slice(1)}-${width}.png`,fullPage:true});
  }
 });
 await check('direct public deep links and the anonymous Profile guard remain intact',async()=>{
  await readMalformedDetail(page,()=>page.goto(new URL('/courses/sample',previewUrl).href,{waitUntil:'networkidle'}));await page.getByRole('heading',{name:'Course details',exact:true}).waitFor();
  await readMalformedDetail(page,()=>page.reload({waitUntil:'networkidle'}));assert(await page.getByRole('heading',{name:'Course details',exact:true}).isVisible());
  if(standalone)assert(await page.getByRole('heading',{name:'Could not load course',exact:true}).isVisible(),'Standalone mode must exercise the real API error view');
  await page.goto(new URL('/profile',previewUrl).href,{waitUntil:'networkidle'});await page.getByRole('heading',{name:'Welcome back',exact:true}).waitFor();assert.equal(new URL(page.url()).pathname,'/login');
  assert.equal(await page.locator('dl').count(),0);
 });
 const motionAudit=[],assetLoading=[];
 async function twoFrames(){await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve(null)))))}
 const textFieldAudit=[],labelMotionAudit=[];
 async function inspectLabelMotion(input){
  const samples=await input.evaluate(async input=>{
   const root=input.closest('.MuiOutlinedInput-root'),label=root.closest('.MuiFormControl-root').querySelector('label'),legend=root.querySelector('legend');
   const frames=[];
   for(const state of ['focus','blur','interrupted focus']){
    if(state==='interrupted focus'){input.focus();input.blur();}
    if(state==='blur')input.blur();else input.focus();
    const start=performance.now();
    await new Promise(resolve=>{
     function frame(){
      const l=getComputedStyle(label),g=getComputedStyle(legend),r=getComputedStyle(root),box=label.getBoundingClientRect(),outline=root.getBoundingClientRect();
      frames.push({state,time:performance.now()-start,background:l.backgroundColor,surface:r.backgroundColor,delay:g.transitionDelay,transitionProperty:g.transitionProperty,shrink:label.dataset.shrink==='true',
       transform:l.transform,notchWidth:legend.getBoundingClientRect().width,labelTop:box.top-outline.top,labelBottom:box.bottom-outline.top});
      if(performance.now()-start<250)requestAnimationFrame(frame);else resolve();
     }
     requestAnimationFrame(frame);
    });
   }
   return frames;
  });
  assert(samples.length>6,'Observe intermediate frames, not only the settled input');
  for(const sample of samples){
   assert.equal(sample.background,sample.surface,'Moving label must mask the border immediately, including reversed transitions');
   assert.notEqual(sample.background,'rgba(0, 0, 0, 0)');
   assert.equal(parseFloat(sample.delay),0,'Notch opening must not lag behind label motion');
   assert.equal(sample.transitionProperty,'none','Notch must not animate independently of the opaque moving label');
   if(!sample.shrink)assert(sample.notchWidth<1,'Empty-field border must close immediately on blur, without a trailing gap');
  }
  labelMotionAudit.push({id:await input.getAttribute('id'),viewport:page.viewportSize(),samples});
 }
 async function inspectField(input,state,shrink){
  const metrics=await input.evaluate(e=>{
   const root=e.closest('.MuiOutlinedInput-root'),form=root.closest('.MuiFormControl-root'),label=form.querySelector('label'),legend=root.querySelector('legend');
   const css=getComputedStyle(e),r=getComputedStyle(root),border=getComputedStyle(root.querySelector('fieldset'));
   const l=label&&getComputedStyle(label),b=label?.getBoundingClientRect();
   return {id:e.id,state:null,value:e.value,focused:e===document.activeElement,font:parseFloat(css.fontSize),caret:css.caretColor,
    outline:css.outlineStyle,rootOutline:r.outlineStyle,before:getComputedStyle(root,'::before').content,after:getComputedStyle(root,'::after').content,
    borderWidth:parseFloat(border.borderTopWidth),borderColor:border.borderTopColor,radius:r.borderRadius,
    label:label&&{font:parseFloat(l.fontSize),color:l.color,shrink:label.dataset.shrink==='true',width:b.width,height:b.height,transform:l.transform},
    legend:{font:parseFloat(getComputedStyle(legend).fontSize),width:legend.getBoundingClientRect().width},
    invalid:e.getAttribute('aria-invalid'),describedBy:e.getAttribute('aria-describedby'),overflow:document.documentElement.scrollWidth>innerWidth};
  });
  assert.equal(metrics.outline,'none',state);assert.equal(metrics.rootOutline,'none',state);
  assert.equal(metrics.before,'none',state);assert.equal(metrics.after,'none',state);assert.equal(metrics.overflow,false,state);
  assert.equal(metrics.radius,'10px');assert.equal(metrics.borderWidth,metrics.focused?2:1,state);
  assert.equal(metrics.label.font,metrics.font,state);assert.equal(metrics.label.shrink,shrink,state);
  if(shrink){
   assert(Math.abs(metrics.legend.font-metrics.label.font*.75)<.1,state);
   assert(Math.abs(metrics.legend.width-metrics.label.width-10)<1,`${state}: legend must clear the complete scaled label and required marker`);
  }else assert(metrics.legend.width<1,`${state}: empty resting field must have a closed notch`);
  if(metrics.invalid==='true'){assert(metrics.describedBy);assert.equal(metrics.label.color,metrics.borderColor,state);}
  textFieldAudit.push({...metrics,state,route:new URL(page.url()).pathname,viewport:page.viewportSize(),preference:await page.evaluate(()=>matchMedia('(prefers-reduced-motion: reduce)').matches?'reduce':'no-preference')});
 }
 for(const preference of ['no-preference','reduce'])for(const width of [1440,320]){
  await check(`TextField label, notch and focus states: ${preference} at ${width}`,async()=>{
   await page.emulateMedia({reducedMotion:preference});await page.setViewportSize({width,height:1000});
   for(const route of ['/login','/register']){
    await page.goto(new URL(route,previewUrl).href,{waitUntil:'networkidle'});await page.evaluate(()=>document.fonts.ready);
    const inputs=page.locator('input');
    for(const input of await inputs.all()){
     await page.mouse.move(0,0);await inspectField(input,'empty resting',false);
     await input.hover();await page.waitForTimeout(250);await inspectField(input,'empty hover',false);
     await inspectLabelMotion(input);
     await input.focus();await page.waitForTimeout(250);await inspectField(input,'focused empty',true);
     const before=await input.locator('..').boundingBox();await input.fill('Example learner');await inspectField(input,'focused populated',true);
     await page.keyboard.press('Tab');await page.waitForTimeout(250);await inspectField(input,'blur populated',true);
     await input.fill('');await input.evaluate(e=>e.blur());await page.waitForTimeout(250);await inspectField(input,'blur cleared',false);
     await input.focus();await input.evaluate(e=>e.blur());await input.focus();
     // Observe the transition in flight, then its final state; neither may resize the control.
     await twoFrames();const during=await input.locator('..').boundingBox();assert.equal(during.height,before.height);assert.equal(during.width,before.width);
     await page.waitForTimeout(250);await inspectField(input,'rapid focus final',true);
     await page.keyboard.press('Tab');await page.keyboard.press('Shift+Tab');assert(await input.evaluate(e=>e===document.activeElement));
     await input.evaluate(e=>e.blur());await page.waitForTimeout(250);
    }
    await page.locator('button[type="submit"]').click();await page.waitForTimeout(250);
    for(const input of await page.locator('input[aria-invalid="true"]').all())await inspectField(input,'validation error',await input.evaluate(e=>e===document.activeElement));
    const first=inputs.first();await first.focus();await page.waitForTimeout(250);
    await page.screenshot({path:`${out}/textfield-${route.slice(1)}-${width}-${preference}.png`,fullPage:true});
   }
   await page.goto(previewUrl,{waitUntil:'networkidle'});await page.getByRole('button',{name:'Explore components',exact:true}).click();
   const shared=page.getByRole('textbox',{name:'Debounce sample input',exact:true});await shared.focus();await page.waitForTimeout(250);
   await inspectField(shared,'shared utility focused',true);await shared.fill('Preview');await shared.evaluate(e=>e.blur());await page.waitForTimeout(250);await inspectField(shared,'shared utility populated',true);
  });
 }
 for(const preference of ['no-preference','reduce'])for(const width of [320,390,768,1024,1440]){
  await check(`interaction and motion matrix: ${preference} at ${width}`,async()=>{
   await page.emulateMedia({reducedMotion:preference});await page.setViewportSize({width,height:900});await page.mouse.move(0,0);
   await page.goto(previewUrl,{waitUntil:'networkidle'});await page.evaluate(()=>document.fonts.ready);
   if(width<900){
    const toggle=page.getByRole('button',{name:'Toggle navigation',includeHidden:true});
    await toggle.focus();await page.keyboard.press('Enter');await page.keyboard.press('Escape');await toggle.press('Enter');
    await page.waitForFunction(()=>{const paper=document.querySelector('.MuiDrawer-modal .MuiDrawer-paper');return paper&&Math.abs(paper.getBoundingClientRect().left)<.5});
    assert.equal(await toggle.getAttribute('aria-expanded'),'true');await page.keyboard.press('Escape');
    await twoFrames();assert.equal(await page.locator('.MuiDrawer-modal').isVisible(),false);
    await page.locator('.MuiDrawer-modal').waitFor({state:'hidden'});assert(await toggle.evaluate(e=>e===document.activeElement));
    await toggle.press('Enter');await page.setViewportSize({width:1024,height:900});await twoFrames();
    assert.equal(await page.locator('.MuiDrawer-modal').count(),0);assert.equal(await page.evaluate(()=>document.body.style.overflow),'');
    assert(await page.locator('.MuiDrawer-docked [aria-current="true"]').evaluate(e=>e===document.activeElement));
    await page.setViewportSize({width,height:900});await twoFrames();assert.equal(await toggle.getAttribute('aria-expanded'),'false');
   }
   const chart=page.getByRole('group',{name:'Sample weekly study minutes'});
   const chartHeight=await chart.evaluate(e=>e.closest('.MuiPaper-root').getBoundingClientRect().height);
   await chart.getByRole('button',{name:'Thu: 60 sample study minutes',exact:true}).focus();await page.keyboard.press('ArrowRight');
   assert.equal(await chart.getByRole('tooltip').textContent(),'Fri · 40 min');
   await chart.getByRole('button',{name:'Mon: 30 sample study minutes',exact:true}).hover();assert.equal(await chart.getByRole('tooltip').textContent(),'Mon · 30 min');
   await page.keyboard.press('Home');assert.equal(await chart.getByRole('button',{name:'Mon: 30 sample study minutes',exact:true}).getAttribute('aria-pressed'),'true');
   const tooltipFits=await chart.getByRole('tooltip').evaluate(e=>{const t=e.getBoundingClientRect(),p=e.closest('.MuiPaper-root').getBoundingClientRect();return t.left>=p.left&&t.right<=p.right&&t.top>=p.top&&t.bottom<=p.bottom});
   assert(tooltipFits);assert.equal(await chart.evaluate(e=>e.closest('.MuiPaper-root').getBoundingClientRect().height),chartHeight);
   await page.keyboard.press('Escape');assert.equal(await chart.getByRole('tooltip').count(),0);await page.mouse.move(0,0);
   const search=page.getByRole('textbox',{name:'Search sample lessons'});await search.fill('decisions');await page.waitForTimeout(450);
   assert.equal(await page.locator('tbody tr').count(),1);assert(await search.evaluate(e=>e===document.activeElement));
   await search.press('Escape');await page.waitForTimeout(450);assert.equal(await page.locator('tbody tr').count(),3);
   await page.getByRole('tab',{name:'All lessons',exact:true}).focus();await page.keyboard.press('End');await page.keyboard.press('Enter');await twoFrames();
   const earlyScroll=await page.locator('.MuiTabs-scroller').evaluate(e=>e.scrollLeft);await page.waitForTimeout(330);
   const settledScroll=await page.locator('.MuiTabs-scroller').evaluate(e=>e.scrollLeft);
   assert(Math.abs(earlyScroll-settledScroll)<1,'Demo tab scrolling must settle immediately for every motion preference');
   const section=page.locator('#sample-learning-path');
   await page.getByRole('tab',{name:'All lessons',exact:true}).click();await twoFrames();
   const stable=await section.evaluate(e=>({height:e.getBoundingClientRect().height,scroll:scrollY,document:document.documentElement.scrollHeight}));
   for(const [label,count] of [['Completed',1],['To explore',2],['All lessons',3]]){
    await page.getByRole('tab',{name:label,exact:true}).click();await twoFrames();assert.equal(await page.locator('tbody tr').count(),count);
    const current=await section.evaluate(e=>({height:e.getBoundingClientRect().height,scroll:scrollY,document:document.documentElement.scrollHeight}));
    for(const key of ['height','scroll','document'])assert(Math.abs(current[key]-stable[key])<=1,`${label} must preserve ${key} at ${width}`);
   }
   await page.waitForTimeout(330);assert.equal(await page.getByRole('tab',{name:'All lessons',exact:true}).getAttribute('aria-selected'),'true');assert.equal(await page.locator('tbody tr').count(),3);
   const indicator=await page.locator('.MuiTabs-indicator').boundingBox(),selectedTab=await page.getByRole('tab',{name:'All lessons',exact:true}).boundingBox();
   assert(Math.abs(indicator.x-selectedTab.x)<1.5&&Math.abs(indicator.width-selectedTab.width)<1.5,'Tab indicator must settle on the final selection');
   await page.getByRole('button',{name:'Explore components',exact:true}).focus();await page.keyboard.press('Enter');
   assert(await page.locator('main h1').evaluate(e=>e===document.activeElement));
   const trigger=page.getByRole('button',{name:'Open confirmation',exact:true});await trigger.click();
   const dialog=page.getByRole('dialog',{name:'Reset sample controls?',exact:true});await dialog.waitFor();
   assert(await dialog.getByRole('button',{name:'Keep editing',exact:true}).evaluate(e=>e===document.activeElement));
   await page.keyboard.press('Tab');const confirm=dialog.getByRole('button',{name:'Reset preview',exact:true});assert(await confirm.evaluate(e=>e===document.activeElement));
   await page.waitForTimeout(260);assert(await confirm.evaluate(e=>e===document.activeElement),'Entry completion must not move user-selected focus');
   await page.keyboard.press('Tab');assert(await dialog.evaluate(e=>e.contains(document.activeElement)));await page.keyboard.press('Escape');
   await twoFrames();assert.equal(await page.locator('.MuiDialog-root').count(),0);assert.equal(await page.evaluate(()=>document.body.style.overflow),'');
   await dialog.waitFor({state:'hidden'});assert(await trigger.evaluate(e=>e===document.activeElement));
   await trigger.press('Enter');await page.keyboard.press('Escape');await trigger.press('Enter');await dialog.waitFor();
   await dialog.getByRole('button',{name:'Keep editing',exact:true}).click();await dialog.waitFor({state:'hidden'});
   const loader=page.getByRole('progressbar',{name:'Loading sample content...'});
   const animations=await loader.evaluate(e=>e.getAnimations({subtree:true}).filter(a=>a.playState==='running').length);
   assert.equal(animations,0,'Demo loading feedback uses its named status without a decorative animation');
   if(width<900){await page.getByRole('button',{name:'Toggle navigation'}).click();await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Auth layout preview',exact:true}).click();}
   else await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Auth layout preview',exact:true}).click();
   assert(await page.locator('main h1').evaluate(e=>e===document.activeElement));await page.getByRole('button',{name:'Back to demo workspace'}).click();
   assert(await page.locator('main h1').evaluate(e=>e===document.activeElement));
   for(const route of ['/login','/register']){
    await page.goto(new URL(route,previewUrl).href,{waitUntil:'networkidle'});await page.locator('button[type="submit"]').click();
    assert(await page.locator('[aria-invalid="true"]').count()>0);assert(await page.locator('[aria-invalid="true"]').evaluateAll(inputs=>inputs.some(e=>e===document.activeElement)));
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth));
   }
   assert.equal(await page.evaluate(()=>document.body.style.overflow),'');
   motionAudit.push({preference,width,earlyTabScroll:earlyScroll,settledTabScroll:settledScroll,loadingAnimations:animations,passed:true});
  });
 }
 await check('canonical SVG reserves its aspect ratio before loading in dashboard and auth layouts',async()=>{
  for(const route of ['/courses','/login']){
   const cold=await context.newPage();cold.on('pageerror',e=>errors.push(String(e)));cold.on('console',recordConsole);await cold.setViewportSize({width:1440,height:900});
   let release;const gate=new Promise(resolve=>{release=resolve});await cold.route('**/favicon.svg',async request=>{await gate;await request.continue()});
   try{
    await cold.goto(new URL(route,previewUrl).href,{waitUntil:'domcontentloaded'});
    const logo=route==='/courses'?cold.locator('.MuiDrawer-docked nav img'):cold.getByRole('img',{name:'CodeMastery',exact:true});await logo.waitFor({state:'attached'});await cold.evaluate(()=>document.fonts.ready);
    const measure=()=>logo.evaluate(e=>{const r=e.getBoundingClientRect(),wordmark=e.parentElement.querySelector('p');return {width:r.width,height:r.height,wordmarkX:wordmark?.getBoundingClientRect().x??null}});
    assert.equal(await logo.evaluate(e=>e.complete),false,'Exercise an SVG that has not loaded yet');
    const pending=await measure();assert(Math.abs(pending.width/pending.height-brandRatio)<.002);release();await logo.evaluate(e=>e.decode());const loaded=await measure();
    assert(Math.abs(pending.width-loaded.width)<.1);assert.equal(pending.height,loaded.height);assert.equal(pending.wordmarkX,loaded.wordmarkX);assetLoading.push({route,pending,loaded});
   }finally{release();await cold.close()}
  }
 });
 await check('cold font loading preserves focus and avoids page overflow',async()=>{
  const cold=await context.newPage();cold.on('pageerror',e=>errors.push(String(e)));cold.on('console',recordConsole);await cold.setViewportSize({width:320,height:900});
  let release;const gate=new Promise(resolve=>{release=resolve});await cold.route('**/*.woff2',async request=>{await gate;await request.continue()});
  try{
   await cold.goto(previewUrl,{waitUntil:'domcontentloaded'});await cold.locator('main h1').waitFor();await cold.waitForFunction(()=>document.fonts.status==='loading');
   const read=()=>cold.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,focusOnHeading:document.activeElement===document.querySelector('main h1'),fontStatus:document.fonts.status}));
   const pending=await read();release();await cold.evaluate(()=>document.fonts.ready);const loaded=await read();
   assert.equal(pending.overflow,false);assert.equal(loaded.overflow,false);assert(pending.focusOnHeading&&loaded.focusOnHeading);assetLoading.push({fontSwap:{pending,loaded}});
  }finally{release();await cold.close()}
 });
 await page.emulateMedia({reducedMotion:'no-preference'});
 await page.goto(previewUrl,{waitUntil:'networkidle'});
 await page.getByRole('button',{name:'Explore components',exact:true}).click();
 await check('no uncaught browser exceptions',async()=>assert.deepEqual(errors,[]));
 await check('no unexpected browser console errors',async()=>{
  assert.deepEqual(consoleErrors,[]);
  if(standalone)assert.equal(expectedApiConsoleErrors.length,3,'Only the two invalid detail reads and cold catalogue read may fail');
 });
 fs.writeFileSync(`${out}/studio-accessibility-tree.yml`,await page.locator('main').ariaSnapshot());
 const report={engine,standalone,expectedApiConsoleErrors,verifiedMalformedIdResponses,integration:'NOT_RUN: this presentation harness does not establish browser-to-REST-to-PostgreSQL behavior',browser:browser.version(),textFieldAudit,labelMotionAudit,motionAudit,assetLoading,branding,faviconCaching,typography,viewports:layouts,checks,networkOrigins:[...origins],contrast:{onFocus:{...onFocus,ratio:contrast(onFocus.color,onFocus.background)},atRest:{...atRest,ratio:contrast(atRest.color,atRest.background)},onHover:{...onHover,ratio:contrast(onHover.color,onHover.background)},badge:{...badgeColors,ratio:contrast(badgeColors.color,badgeColors.background)}},errors,consoleErrors,consoleWarnings,screenReader:'Not executed'};
 fs.writeFileSync(`${out}/browser-report.json`,JSON.stringify(report,null,2));await browser.close();
 console.log(JSON.stringify({passed:checks.length,browser:report.browser,report:`${out}/browser-report.json`}));
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
