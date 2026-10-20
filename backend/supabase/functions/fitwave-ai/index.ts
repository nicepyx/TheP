// Deploy as a Supabase Edge Function with JWT verification enabled.
// Set AI_API_KEY and optionally AI_MODEL / AI_BASE_URL as server-side secrets.
import { serve } from 'https://deno.land/std@0.224.0/http/server.ts';

const cors:Record<string,string> = {
  'Access-Control-Allow-Origin':'*',
  'Access-Control-Allow-Headers':'authorization, apikey, content-type',
  'Access-Control-Allow-Methods':'POST, OPTIONS',
  'Cache-Control':'no-store'
};
serve(async(req)=>{
  if(req.method==='OPTIONS')return new Response(null,{headers:cors});
  if(req.method!=='POST')return Response.json({error:'Method not allowed'},{status:405,headers:cors});
  const supabase=Deno.env.get('SUPABASE_URL');
  const anon=Deno.env.get('SUPABASE_ANON_KEY');
  const aiKey=Deno.env.get('AI_API_KEY');
  const jwt=(req.headers.get('Authorization')||'').replace(/^Bearer /i,'');
  if(!supabase||!anon||!aiKey)return Response.json({error:'Server not configured'},{status:503,headers:cors});
  if(!jwt)return Response.json({error:'Login required'},{status:401,headers:cors});
  try {
    const auth=await fetch(`${supabase}/auth/v1/user`,{headers:{apikey:anon,Authorization:`Bearer ${jwt}`}});
    if(!auth.ok)return Response.json({error:'Invalid login'},{status:401,headers:cors});
    const input=await req.json();
    const message=String(input.message||'').slice(0,1000).trim();
    if(!message)return Response.json({error:'Empty message'},{status:400,headers:cors});
    const response=await fetch(`${(Deno.env.get('AI_BASE_URL')||'https://api.openai.com/v1').replace(/\/$/,'')}/chat/completions`,{
      method:'POST',
      headers:{Authorization:`Bearer ${aiKey}`,'Content-Type':'application/json'},
      body:JSON.stringify({model:Deno.env.get('AI_MODEL')||'gpt-4.1-mini',max_tokens:280,temperature:0.6,messages:[
        {role:'system',content:'你是 FitWave 的 A1 奶霜丝带角色——温柔、简明、重视科学训练的健身助手。只用中文回答，一般不超过120字。不虚构训练成绩。不要作医疗诊断、极端饮食或危险训练建议。你只能提供建议，不能直接改写用户训练记录。'},
        {role:'user',content:message}
      ]})
    });
    if(!response.ok)return Response.json({error:`AI provider ${response.status}`},{status:502,headers:cors});
    const result=await response.json();
    const answer=String(result.choices?.[0]?.message?.content||'').slice(0,1000);
    return Response.json({answer},{headers:cors});
  }catch(e){return Response.json({error:'Request failed'},{status:500,headers:cors});}
});
