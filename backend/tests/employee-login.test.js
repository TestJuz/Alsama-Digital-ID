import test from 'node:test';
import assert from 'node:assert/strict';

const base=process.env.API_BASE_URL||'http://127.0.0.1:8787';
async function req(path,method='GET',data,token,extra={}){
  const response=await fetch(base+path,{method,headers:{...(data?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{}),...extra},body:data?JSON.stringify(data):undefined});
  const body=await response.text();
  return {status:response.status,body:body?JSON.parse(body):{}};
}

test('Admin creates email-bound employee account without exposing a password',async()=>{
  const secret=process.env.TEST_BOOTSTRAP_TOKEN;
  if(!secret) return;
  const username='ci-admin@example.test',adminPassword='CI-only-expendable-secret-1234';
  const setup=await req('/v1/auth/bootstrap','POST',{email:username,password:adminPassword},undefined,{'X-Bootstrap-Token':secret});
  assert.equal(setup.status,201,JSON.stringify(setup.body));
  const adminLogin=await req('/v1/auth/login','POST',{email:username,password:adminPassword});
  assert.equal(adminLogin.status,200,JSON.stringify(adminLogin.body));
  assert.equal(adminLogin.body.role,'admin');
  const adminToken=adminLogin.body.accessToken;

  const slug='ci-person-'+Date.now();
  const email='staff-'+Date.now()+'@example.test';
  const person=await req('/v1/admin/employees','POST',{slug,fullName:'Integration Test',jobTitle:'Sales',email,phone:'+506 7118 0081',active:true,publicVisible:false},adminToken);
  assert.equal(person.status,201,JSON.stringify(person.body));
  const id=person.body.employee.id;
  assert.equal((await req('/v1/me')).status,401);
  assert.equal((await req('/v1/admin/employees/'+id+'/invite','POST',{})).status,401);
  const invitation=await req('/v1/admin/employees/'+id+'/invite','POST',{},adminToken);
  assert.equal(invitation.status,200,JSON.stringify(invitation.body));
  assert.equal(invitation.body.email,email);
  assert.match(invitation.body.activationCode,/^[a-f0-9]{64}$/);

  const password='CI-only-employee-password-1234';
  const invalid=await req('/v1/auth/activate','POST',{email,code:'0'.repeat(64),password});
  assert.equal(invalid.status,403);
  const active=await req('/v1/auth/activate','POST',{email,code:invitation.body.activationCode,password});
  assert.equal(active.status,200,JSON.stringify(active.body));
  assert.equal(active.body.activated,true);
  assert.equal((await req('/v1/auth/activate','POST',{email,code:invitation.body.activationCode,password})).status,403);

  const login=await req('/v1/auth/login','POST',{email:email.toUpperCase(),password});
  assert.equal(login.status,200,JSON.stringify(login.body));
  assert.equal(login.body.role,'employee');
  assert.equal(login.body.employee.id,id);
  const token=login.body.accessToken;
  assert.equal((await req('/v1/admin/employees','GET',undefined,token)).status,401);
  const mine=await req('/v1/me','GET',undefined,token);
  assert.equal(mine.status,200,JSON.stringify(mine.body));
  assert.equal(mine.body.employee.slug,slug);
  assert.equal(mine.body.employee.email,email);
  assert.equal((await req('/v1/public/staff/'+slug)).status,404);

  const changed=await req('/v1/auth/change-password','POST',{currentPassword:password,newPassword:'CI-only-employee-updated-1234'},token);
  assert.equal(changed.status,200,JSON.stringify(changed.body));
  assert.equal((await req('/v1/me','GET',undefined,token)).status,401);
  assert.equal((await req('/v1/auth/login','POST',{email,password})).status,401);
  const newLogin=await req('/v1/auth/login','POST',{email,password:'CI-only-employee-updated-1234'});
  assert.equal(newLogin.status,200,JSON.stringify(newLogin.body));
  const newToken=newLogin.body.accessToken;

  assert.equal((await req('/v1/admin/employees/'+id+'/status','PATCH',{active:false},adminToken)).status,200);
  assert.equal((await req('/v1/me','GET',undefined,newToken)).status,401);
  assert.equal((await req('/v1/auth/login','POST',{email,password:'CI-only-employee-updated-1234'})).status,401);
  // One email, one password: the server grants admin privileges based on the account.
  const dual=await req('/v1/admin/employees','POST',{slug:'ci-dual-'+Date.now(),fullName:'Test Admin and Staff',jobTitle:'IT Leader',email:username,phone:'+506 7177 8171',active:true,publicVisible:false},adminToken);
  assert.equal(dual.status,201,JSON.stringify(dual.body));
  const dualId=dual.body.employee.id;
  const dualInvite=await req('/v1/admin/employees/'+dualId+'/invite','POST',{},adminToken);
  assert.equal(dualInvite.status,409,JSON.stringify(dualInvite.body));
  const dualLogin=await req('/v1/auth/login','POST',{email:username,password:adminPassword});
  assert.equal(dualLogin.status,200,JSON.stringify(dualLogin.body));
  assert.equal(dualLogin.body.isAdmin,true);
  assert.equal(dualLogin.body.employee.id,dualId);
  const dualToken=dualLogin.body.accessToken;
  const dualMine=await req('/v1/me','GET',undefined,dualToken);
  assert.equal(dualMine.status,200,JSON.stringify(dualMine.body));
  assert.equal(dualMine.body.isAdmin,true);
  assert.equal(dualMine.body.employee.id,dualId);
  assert.equal((await req('/v1/admin/employees','GET',undefined,dualToken)).status,200);
  assert.equal((await req('/v1/public/staff/'+dual.body.employee.slug)).status,404);
  assert.equal((await req('/v1/auth/activate','POST',{email:username,code:'0'.repeat(64),password:'CI-separate-password-not-allowed'})).status,409);
  const adminChanged=await req('/v1/auth/change-password','POST',{currentPassword:adminPassword,newPassword:'CI-unified-admin-updated-password'},dualToken);
  assert.equal(adminChanged.status,200,JSON.stringify(adminChanged.body));
  assert.equal((await req('/v1/me','GET',undefined,dualToken)).status,401);
  assert.equal((await req('/v1/auth/login','POST',{email:username,password:adminPassword})).status,401);
  const adminAgain=await req('/v1/auth/login','POST',{email:username,password:'CI-unified-admin-updated-password'});
  assert.equal(adminAgain.status,200,JSON.stringify(adminAgain.body));
  assert.equal(adminAgain.body.isAdmin,true);
  assert.equal(adminAgain.body.employee.id,dualId);
  assert.equal((await req('/v1/auth/logout','POST',{},adminAgain.body.accessToken)).status,200);
  assert.equal((await req('/v1/admin/employees','GET',undefined,adminToken)).status,401);
});
