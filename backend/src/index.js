const enc=new TextEncoder();
const headers={'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Strict-Transport-Security':'max-age=31536000; includeSubDomains'};
const json=(v,s=200,h={})=>new Response(JSON.stringify(v),{status:s,headers:{...headers,...h}});
const error=(s,code,message)=>json({error:{code,message}},s);
const hex=buf=>Array.from(new Uint8Array(buf)).map(x=>x.toString(16).padStart(2,'0')).join('');
const sha=async s=>hex(await crypto.subtle.digest('SHA-256',enc.encode(s)));
const rand=()=>{const b=new Uint8Array(32);crypto.getRandomValues(b);return Array.from(b).map(x=>x.toString(16).padStart(2,'0')).join('')};
async function passwordHash(p,s){const k=await crypto.subtle.importKey('raw',enc.encode(p),'PBKDF2',false,['deriveBits']);return hex(await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',salt:enc.encode(s),iterations:100000},k,256))}
function safeEqual(a,b){if(typeof a!=='string'||typeof b!=='string'||a.length!==b.length)return false;let d=0;for(let i=0;i<a.length;i++)d|=a.charCodeAt(i)^b.charCodeAt(i);return d===0}
const now=()=>new Date().toISOString();
const slugRe=/^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const uuidRe=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const select='id,slug,full_name,job_title,email,phone,photo_url,active,public_visible,created_at,updated_at';
const employee=r=>({id:r.id,slug:r.slug,fullName:r.full_name,jobTitle:r.job_title,email:r.email,phone:r.phone,photoUrl:r.photo_url,active:!!r.active,publicVisible:!!r.public_visible,createdAt:r.created_at,updatedAt:r.updated_at});
const publicEmployee=r=>({slug:r.slug,fullName:r.full_name,jobTitle:r.job_title,photoUrl:r.photo_url,...(r.public_visible?{email:r.email,phone:r.phone}:{})});
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const vcf=s=>String(s??'').replace(/\\/g,'\\\\').replace(/\r?\n/g,'\\n').replace(/[,;]/g,'\\$&');
const body=async req=>{if(!req.headers.get('content-type')?.toLowerCase().startsWith('application/json'))throw [415,'unsupported_media_type'];if(Number(req.headers.get('content-length')||0)>16384)throw [413,'payload_too_large'];const s=await req.text();if(s.length>16384)throw [413,'payload_too_large'];try{const v=JSON.parse(s);if(!v||Array.isArray(v)||typeof v!=='object')throw 0;return v}catch{throw [400,'invalid_json']}};
function validate(v,partial=false){const allowed=['slug','fullName','jobTitle','email','phone','photoUrl','active','publicVisible'];if(Object.keys(v).some(k=>!allowed.includes(k)))return 'unknown_field';for(const k of ['slug','fullName','jobTitle','email','phone']){if(!partial||k in v){if(typeof v[k]!=='string'||!v[k].trim()||v[k].length> (k==='email'?254:120))return 'invalid_'+k}}if('slug'in v&&(!slugRe.test(v.slug)||v.slug.length>90))return 'invalid_slug';if('email'in v&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email))return 'invalid_email';if('phone'in v&&!/^\+?[0-9 ()-]{7,25}$/.test(v.phone))return 'invalid_phone';if('photoUrl'in v&&v.photoUrl!==null&&(typeof v.photoUrl!=='string'||v.photoUrl.length>2048||!/^https:\/\/[^\s]+$/i.test(v.photoUrl)))return 'invalid_photoUrl';for(const k of ['active','publicVisible'])if(k in v&&typeof v[k]!=='boolean')return 'invalid_'+k;return null}
async function auth(req,env){const m=/^Bearer ([A-Fa-f0-9]{64})$/.exec(req.headers.get('Authorization')||'');if(!m)return null;return env.DB.prepare('SELECT admins.id,admins.email FROM sessions JOIN admins ON sessions.admin_id=admins.id WHERE sessions.token_hash=? AND sessions.expires_at>?').bind(await sha(m[1]),now()).first()}

async function employeeAuth(req,env){
 const m=/^Bearer ([A-Fa-f0-9]{64})$/.exec(req.headers.get('Authorization')||'');
 if(!m)return null;
 return env.DB.prepare('SELECT '+select.split(',').map(c=>'employees.'+c).join(',')+' FROM employee_sessions JOIN employees ON employee_sessions.employee_id=employees.id WHERE employee_sessions.token_hash=? AND employee_sessions.expires_at>? AND employees.active=1').bind(await sha(m[1]),now()).first();
}
async function hashPassword(p,s){return passwordHash(p,s)}
async function issueSession(env,table,userId,role){
 const accessToken=rand(),expiresAt=new Date(Date.now()+8*3600000).toISOString();
 const sql=table==='sessions'?'INSERT INTO sessions (token_hash,admin_id,expires_at,created_at) VALUES (?,?,?,?)':'INSERT INTO employee_sessions (token_hash,employee_id,expires_at,created_at) VALUES (?,?,?,?)';
 await env.DB.prepare(sql).bind(await sha(accessToken),userId,expiresAt,now()).run();
 return {accessToken,expiresAt,role};
}
async function recordFailedLogin(env,key,t){
 await env.DB.prepare('INSERT INTO login_attempts (key,attempts,window_start) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET attempts=CASE WHEN ?-window_start>=900000 THEN 1 ELSE attempts+1 END,window_start=CASE WHEN ?-window_start>=900000 THEN ? ELSE window_start END').bind(key,1,t,t,t,t).run();
}

async function main(req,env){const u=new URL(req.url),p=u.pathname,method=req.method;const corsOrigin=env.CORS_ORIGIN||'';const origin=req.headers.get('Origin');if(method==='OPTIONS'){if(origin&&origin===corsOrigin)return new Response(null,{status:204,headers:{'Access-Control-Allow-Origin':origin,'Access-Control-Allow-Methods':'GET,POST,PUT,PATCH,OPTIONS','Access-Control-Allow-Headers':'Authorization,Content-Type','Vary':'Origin'}});return error(403,'forbidden','Origin not allowed')}
if(origin&&origin!==corsOrigin)return error(403,'forbidden','Origin not allowed');
let response;
if(method==='GET'&&p==='/v1/health')response=json({status:'ok',service:'alsama-digital-id-api'});
else if(method==='POST'&&p==='/v1/auth/bootstrap'){if(!env.BOOTSTRAP_TOKEN)return error(503,'bootstrap_unavailable','Bootstrap is not configured');const c=await env.DB.prepare('SELECT COUNT(*) AS n FROM admins').first();if(c.n)return error(409,'already_initialized','Administrator already exists');const token=req.headers.get('X-Bootstrap-Token')||'';if(!safeEqual(await sha(token),await sha(env.BOOTSTRAP_TOKEN)))return error(403,'forbidden','Invalid setup token');const v=await body(req);if(typeof v.email!=='string'||! /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email)||typeof v.password!=='string'||v.password.length<14||v.password.length>128)return error(422,'validation_error','Valid email and password of at least 14 characters required');const salt=rand();await env.DB.prepare('INSERT INTO admins (id,email,password_hash,password_salt,created_at) VALUES (?,?,?,?,?)').bind(crypto.randomUUID(),v.email.toLowerCase(),await passwordHash(v.password,salt),salt,now()).run();response=json({initialized:true},201)}
else if(method==='POST'&&p==='/v1/auth/login'){
 const v=await body(req);
 if(typeof v.email!=='string'||typeof v.password!=='string'||v.email.length>254||v.password.length>128)return error(400,'invalid_credentials','Invalid credentials');
 const email=v.email.toLowerCase().trim(),ip=req.headers.get('CF-Connecting-IP')||'unknown';
 const key=await sha(email+'|'+ip),t=Date.now();
 const attempt=await env.DB.prepare('SELECT attempts,window_start FROM login_attempts WHERE key=?').bind(key).first();
 if(attempt&&t-attempt.window_start<900000&&attempt.attempts>=5)return error(429,'rate_limited','Too many attempts');
 const admin=await env.DB.prepare('SELECT * FROM admins WHERE email=?').bind(email).first();
 const staff=admin?null:await env.DB.prepare('SELECT '+select.split(',').map(c=>'employees.'+c).join(',')+',ec.password_hash AS employee_hash,ec.password_salt AS employee_salt FROM employees JOIN employee_credentials ec ON ec.employee_id=employees.id WHERE lower(employees.email)=? AND employees.active=1').bind(email).first();
 const salt=admin?.password_salt||staff?.employee_salt||'placeholder-salt';
 const hashed=await hashPassword(v.password,salt);
 if((!admin&&!staff)||!safeEqual(hashed,admin?.password_hash||staff?.employee_hash)){
   await recordFailedLogin(env,key,t);
   return error(401,'invalid_credentials','Invalid credentials');
 }
 await env.DB.prepare('DELETE FROM login_attempts WHERE key=?').bind(key).run();
 const session=await issueSession(env,admin?'sessions':'employee_sessions',admin?.id||staff.id,admin?'admin':'employee');
 response=json({...session,...(admin?{admin:{id:admin.id,email:admin.email}}:{employee:employee(staff)})});
}
else if(method==='POST'&&p==='/v1/auth/activate'){
 const v=await body(req);
 if(typeof v.email!=='string'||typeof v.code!=='string'||typeof v.password!=='string'||v.password.length<14||v.password.length>128)return error(422,'validation_error','Correo, código y contraseña de 14-128 caracteres obligatorios');
 const email=v.email.toLowerCase().trim();
 const invited=await env.DB.prepare('SELECT ei.employee_id,ei.code_hash,ei.expires_at FROM employee_invites ei JOIN employees e ON e.id=ei.employee_id WHERE lower(e.email)=? AND e.active=1').bind(email).first();
 const inputHash=await sha(v.code);
 if(!invited||invited.expires_at<now()||!safeEqual(invited.code_hash,inputHash))return error(403,'invalid_invitation','Código inválido o vencido');
 const salt=rand(),hashed=await hashPassword(v.password,salt),date=now();
 const stored=await env.DB.prepare('INSERT INTO employee_credentials (employee_id,password_hash,password_salt,updated_at) VALUES (?,?,?,?) ON CONFLICT(employee_id) DO UPDATE SET password_hash=excluded.password_hash,password_salt=excluded.password_salt,updated_at=excluded.updated_at').bind(invited.employee_id,hashed,salt,date).run();
 await env.DB.prepare('DELETE FROM employee_sessions WHERE employee_id=?').bind(invited.employee_id).run();
 await env.DB.prepare('DELETE FROM employee_invites WHERE employee_id=? AND code_hash=?').bind(invited.employee_id,inputHash).run();
 response=json({activated:true});
}
else if(method==='GET'&&p==='/v1/me'){
 const staff=await employeeAuth(req,env);
 if(!staff)return error(401,'unauthorized','Inicia sesión para ver tu credencial');
 response=json({role:'employee',employee:employee(staff)});
}
else if(method==='POST'&&p==='/v1/auth/change-password'){
 const staff=await employeeAuth(req,env);
 if(!staff)return error(401,'unauthorized','Inicia sesión para cambiar tu contraseña');
 const v=await body(req);
 if(typeof v.currentPassword!=='string'||typeof v.newPassword!=='string'||v.newPassword.length<14||v.newPassword.length>128)return error(422,'validation_error','Nueva contraseña de 14-128 caracteres obligatoria');
 const cred=await env.DB.prepare('SELECT password_hash,password_salt FROM employee_credentials WHERE employee_id=?').bind(staff.id).first();
 if(!cred||!safeEqual(await hashPassword(v.currentPassword,cred.password_salt),cred.password_hash))return error(401,'invalid_credentials','Contraseña actual incorrecta');
 const salt=rand();
 await env.DB.prepare('UPDATE employee_credentials SET password_hash=?,password_salt=?,updated_at=? WHERE employee_id=?').bind(await hashPassword(v.newPassword,salt),salt,now(),staff.id).run();
 await env.DB.prepare('DELETE FROM employee_sessions WHERE employee_id=?').bind(staff.id).run();
 response=json({changed:true,reauthenticate:true});
}
else if(method==='POST'&&p==='/v1/auth/logout'){
 const token=req.headers.get('Authorization')?.match(/^Bearer ([a-fA-F0-9]{64})$/)?.[1];
 if(!token)return error(401,'unauthorized','Authentication required');
 const h=await sha(token);
 const a=await auth(req,env),e=await employeeAuth(req,env);
 if(!a&&!e)return error(401,'unauthorized','Authentication required');
 await env.DB.prepare('DELETE FROM sessions WHERE token_hash=?').bind(h).run();
 await env.DB.prepare('DELETE FROM employee_sessions WHERE token_hash=?').bind(h).run();
 response=json({loggedOut:true});
}
else if(p.startsWith('/v1/admin/')){const a=await auth(req,env);if(!a)return error(401,'unauthorized','Authentication required');if(p==='/v1/admin/employees'&&method==='GET'){const r=await env.DB.prepare('SELECT '+select+' FROM employees ORDER BY full_name').all();response=json({employees:r.results.map(employee)})}
else if(p==='/v1/admin/employees'&&method==='POST'){const v=await body(req),e=validate(v);if(e)return error(422,'validation_error',e);const id=crypto.randomUUID(),date=now();try{await env.DB.prepare('INSERT INTO employees ('+select+') VALUES (?,?,?,?,?,?,?,?,?,?,?)').bind(id,v.slug,v.fullName,v.jobTitle,v.email,v.phone,v.photoUrl??null,v.active===false?0:1,v.publicVisible===true?1:0,date,date).run()}catch(e){if(String(e).includes('UNIQUE'))return error(409,'duplicate_slug','Employee slug already exists');throw e}response=json({employee:employee(await env.DB.prepare('SELECT '+select+' FROM employees WHERE id=?').bind(id).first())},201)}
else if(method==='POST'&&/^\/v1\/admin\/employees\/[0-9a-f-]+\/invite$/.test(p)){
 const id=p.split('/')[4];
 if(!uuidRe.test(id))return error(404,'not_found','Employee not found');
 const staff=await env.DB.prepare('SELECT id,email,active FROM employees WHERE id=?').bind(id).first();
 if(!staff||!staff.active)return error(404,'not_found','Employee not found or inactive');
 const code=rand(),expiration=new Date(Date.now()+24*3600000).toISOString();
 await env.DB.prepare('INSERT INTO employee_invites(employee_id,code_hash,expires_at,created_at) VALUES(?,?,?,?) ON CONFLICT(employee_id) DO UPDATE SET code_hash=excluded.code_hash,expires_at=excluded.expires_at,created_at=excluded.created_at').bind(id,await sha(code),expiration,now()).run();
 response=json({email:staff.email,activationCode:code,expiresAt:expiration});
}
else{const match=/^\/v1\/admin\/employees\/([0-9a-f-]+)(\/status)?$/.exec(p);if(!match||!uuidRe.test(match[1]))return error(404,'not_found','Resource not found');const id=match[1],old=await env.DB.prepare('SELECT '+select+' FROM employees WHERE id=?').bind(id).first();if(!old)return error(404,'not_found','Employee not found');if(method==='GET'&&!match[2])response=json({employee:employee(old)});
else if(method==='PATCH'&&match[2]){const v=await body(req);if(Object.keys(v).length!==1||typeof v.active!=='boolean')return error(422,'validation_error','active must be boolean');await env.DB.prepare('UPDATE employees SET active=?,updated_at=? WHERE id=?').bind(v.active?1:0,now(),id).run();response=json({employee:employee(await env.DB.prepare('SELECT '+select+' FROM employees WHERE id=?').bind(id).first())})}
else if(method==='PUT'&&!match[2]){const v=await body(req),e=validate(v,true);if(e||!Object.keys(v).length)return error(422,'validation_error',e||'empty_update');const fields={slug:'slug',fullName:'full_name',jobTitle:'job_title',email:'email',phone:'phone',photoUrl:'photo_url',active:'active',publicVisible:'public_visible'};const keys=Object.keys(v);try{await env.DB.prepare('UPDATE employees SET '+keys.map(k=>fields[k]+'=?').join(',')+',updated_at=? WHERE id=?').bind(...keys.map(k=>typeof v[k]==='boolean'?(v[k]?1:0):v[k]),now(),id).run()}catch(e){if(String(e).includes('UNIQUE'))return error(409,'duplicate_slug','Employee slug already exists');throw e}response=json({employee:employee(await env.DB.prepare('SELECT '+select+' FROM employees WHERE id=?').bind(id).first())})}
else return error(405,'method_not_allowed','Method not allowed')}}
else{const m=/^\/v1\/public\/staff\/([a-z0-9-]+)(\/contact\.vcf)?$/.exec(p),card=/^\/p\/([a-z0-9-]+)$/.exec(p);if(method!=='GET'||(!m&&!card))return error(404,'not_found','Resource not found');const slug=(m||card)[1];if(!slugRe.test(slug))return error(404,'not_found','Resource not found');const r=await env.DB.prepare('SELECT '+select+' FROM employees WHERE slug=? AND active=1').bind(slug).first();if(!r||!r.public_visible)return error(404,'not_found','Employee not found');if(m&&!m[2])response=json(publicEmployee(r));
else if(m&&m[2]){if(!r.public_visible)return error(404,'not_found','Contact not publicly available');const lines=['BEGIN:VCARD','VERSION:3.0','FN:'+vcf(r.full_name),'ORG:Alsama Tours Costa Rica','TITLE:'+vcf(r.job_title),'EMAIL;TYPE=WORK:'+vcf(r.email),'TEL;TYPE=WORK:'+vcf(r.phone),'END:VCARD'];response=new Response(lines.join('\r\n')+'\r\n',{headers:{...headers,'Content-Type':'text/vcard; charset=utf-8','Content-Disposition':'attachment; filename="contact.vcf"'}})}
else{const publicLink='/v1/public/staff/'+encodeURIComponent(slug)+'/contact.vcf';const contact=r.public_visible?'<p><a href="mailto:'+esc(r.email)+'">'+esc(r.email)+'</a></p><p><a href="tel:'+esc(r.phone)+'">'+esc(r.phone)+'</a></p><p><a href="'+publicLink+'">Guardar contacto</a></p>':'';const html='<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex"><title>'+esc(r.full_name)+' | Alsama</title><style>body{font:16px system-ui;background:#eef4f4;display:grid;place-items:center;min-height:100vh;margin:0}main{background:white;padding:2rem;border-radius:20px;max-width:420px;width:80%;text-align:center;box-shadow:0 8px 30px #0001}img{width:120px;height:120px;object-fit:cover;border-radius:50%}a{color:#006a63}</style></head><body><main>'+(r.photo_url?'<img alt="Fotografía de empleado" src="'+esc(r.photo_url)+'">':'')+'<h1>'+esc(r.full_name)+'</h1><p>'+esc(r.job_title)+'</p><p>Alsama Tours Costa Rica</p>'+contact+'</main></body></html>';response=new Response(html,{headers:{...headers,'Content-Type':'text/html; charset=utf-8','Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; img-src https:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"}})}}
if(origin&&origin===corsOrigin){response.headers.set('Access-Control-Allow-Origin',origin);response.headers.set('Vary','Origin')}return response}
export default {async fetch(req,env){try{return await main(req,env)}catch(e){if(Array.isArray(e))return error(e[0],e[1],e[1]);console.error('Request failed',e?.message);return error(500,'internal_error','Internal server error')}}};