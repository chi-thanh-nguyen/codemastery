// Optional local-browser verification using an explicitly supplied Playwright installation.
// No browser package is downloaded by this script. See the PR verification record.
const {chromium} = require(process.argv[2] || 'playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const path=require('node:path');
const out=path.resolve(process.argv[3] || path.join(require('node:os').tmpdir(),'codemastery-fe01-evidence'));
fs.mkdirSync(out,{recursive:true});
const previewUrl=process.argv[4] || 'http://127.0.0.1:5173/';
let browser;
(async()=>{
 browser=await chromium.launch({headless:true});
 const context=await browser.newContext({viewport:{width:1440,height:900}});
 const page=await context.newPage();
 const errors=[];const origins=new Set();page.on('request',r=>origins.add(new URL(r.url()).origin));page.on('pageerror',e=>errors.push(String(e)));
 const checks=[];
 async function check(name,fn){await fn();checks.push({name,passed:true});console.log('PASS',name)}
 function contrast(a,b){const lum=s=>{const v=s.match(/[\d.]+/g).slice(0,3).map(Number).map(x=>{x/=255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4});return v[0]*.2126+v[1]*.7152+v[2]*.0722};const [l,h]=[lum(a),lum(b)].sort((a,b)=>a-b);return(h+.05)/(l+.05)}
 async function colors(locator){return locator.evaluate(e=>{const s=getComputedStyle(e);return {color:s.color,background:s.backgroundColor,outline:s.outline,outlineWidth:s.outlineWidth,transition:s.transitionDuration}})}
 await page.goto(previewUrl,{waitUntil:'networkidle'});
 await check('overview is explicitly demo data',async()=>{await page.getByRole('heading',{name:'Your learning, in focus.'}).waitFor();assert(await page.getByText('All learning data below is illustrative. Nothing is saved.').isVisible())});
 const layouts=[];
 for(const [width,height] of [[1440,900],[1024,768],[390,844],[320,844],[899,900],[900,900],[1199,900],[1200,900]]){
  await page.setViewportSize({width,height}); await page.waitForTimeout(150);
  const layout=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,client:document.documentElement.clientWidth,navs:[...document.querySelectorAll('nav')].filter(n=>n.getBoundingClientRect().width&&getComputedStyle(n).visibility!=='hidden'&&!n.closest('[aria-hidden="true"]')).length}));
  layouts.push({width,height,...layout});
  await check(`no page overflow at ${width}`,async()=>assert(layout.scroll<=layout.client,JSON.stringify(layout)));
  if(width===320)await check('sample table remains keyboard-scrollable on narrow mobile',async()=>{const table=page.getByRole('region',{name:'Sample lesson table'});await table.focus();await page.keyboard.press('ArrowRight');await page.waitForTimeout(150);assert(await table.evaluate(e=>e.scrollLeft>0));});
  if([1440,1024,390,320].includes(width))await page.screenshot({path:`${out}/overview-${width}.png`,fullPage:true});
 }
 await page.setViewportSize({width:1440,height:900});
 const active=page.locator('.MuiDrawer-docked').getByRole('button',{name:'Overview',exact:true});
 const atRest=await colors(active);await active.hover();await page.waitForTimeout(250);const onHover=await colors(active);
 await check('selected navigation normal/hover contrast',async()=>{assert(contrast(atRest.color,atRest.background)>=4.5);assert(contrast(onHover.color,onHover.background)>=4.5)});
 await check('four KPI cards form a single desktop row',async()=>{
  const positions=[];for(const t of ['Study time','Lessons completed','Practice streak','Course progress'])positions.push((await page.getByText(t,{exact:true}).boundingBox()).y);
  assert(Math.max(...positions)-Math.min(...positions)<3,JSON.stringify(positions));
 });
 await check('chart outlines and progress indicators meet non-text contrast',async()=>{
  const bars=page.getByRole('img',{name:/Demo study minutes/}).locator('[aria-hidden="true"] > div');
  for(const bar of await bars.all()){const border=await bar.evaluate(e=>getComputedStyle(e).borderTopColor);assert(contrast(border,'rgb(255,255,255)')>=3);}
  const progress=page.getByRole('progressbar',{name:'Sample course completion',exact:true});const background=await progress.evaluate(e=>getComputedStyle(e).backgroundColor);const fill=await progress.locator('.MuiLinearProgress-bar').evaluate(e=>getComputedStyle(e).backgroundColor);assert(contrast(fill,background)>=3);
 });
 await check('search filters the local sample table after debounce',async()=>{
  await page.getByRole('textbox',{name:'Search sample lessons'}).fill('decisions');await page.waitForTimeout(450);
  assert.equal(await page.getByRole('cell',{name:'Making decisions Control flow'}).count(),1);
  assert.equal(await page.getByText('Thinking in variables',{exact:true}).count(),0);
  await page.getByRole('textbox',{name:'Search sample lessons'}).fill('');await page.waitForTimeout(450);
 });
 await check('tabs filter sample rows',async()=>{
  await page.getByRole('tab',{name:'Completed',exact:true}).click();assert.equal(await page.getByText('Thinking in variables',{exact:true}).count(),1);assert.equal(await page.getByText('Making decisions',{exact:true}).count(),0);
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
  await page.screenshot({path:`${out}/auth-900.png`,fullPage:true});await page.setViewportSize({width:320,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth));await page.screenshot({path:`${out}/auth-320.png`,fullPage:true});await page.getByRole('button',{name:'Back to demo workspace'}).click();
 });
 await check('reduced motion suppresses long animation/transition',async()=>{
  await page.emulateMedia({reducedMotion:'reduce'});await page.getByRole('button',{name:'Explore components',exact:true}).click();
  const duration=await page.getByRole('progressbar',{name:'Loading sample content...'}).evaluate(e=>getComputedStyle(e).animationDuration);assert(duration.split(',').every(x=>parseFloat(x)<.01),duration);
 });
 await check('no uncaught browser exceptions',async()=>assert.deepEqual(errors,[]));
 fs.writeFileSync(`${out}/studio-accessibility-tree.yml`,await page.locator('main').ariaSnapshot());
 const report={browser:browser.version(),viewports:layouts,checks,networkOrigins:[...origins],contrast:{onFocus:{...onFocus,ratio:contrast(onFocus.color,onFocus.background)},atRest:{...atRest,ratio:contrast(atRest.color,atRest.background)},onHover:{...onHover,ratio:contrast(onHover.color,onHover.background)},badge:{...badgeColors,ratio:contrast(badgeColors.color,badgeColors.background)}},errors,screenReader:'Not executed'};
 fs.writeFileSync(`${out}/browser-report.json`,JSON.stringify(report,null,2));await browser.close();
 console.log(JSON.stringify({passed:checks.length,browser:report.browser,report:`${out}/browser-report.json`}));
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1});
