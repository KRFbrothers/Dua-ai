export default async function handler(){return Response.json({error:'Too many account requests. Retry in one minute.',retryAfterSeconds:60},{status:429,headers:{'Cache-Control':'no-store','Retry-After':'60'}});}
export const config={path:'/api/account-rate-limit'};
