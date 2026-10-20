/** Character animation bus. Real Cubism model becomes available after licensed assets are supplied. */
export type Motion='idle'|'greeting'|'cheer'|'rest'|'ready'|'done'|'touch'|'sleep'|'thinking';
const motions:Motion[]=['idle','greeting','cheer','rest','ready','done','touch','sleep','thinking'];
let motion:Motion='idle';
export function emote(next:Motion){
  motion=motions.includes(next)?next:'idle';
  document.body.dataset.a1Emotion=motion;
  window.dispatchEvent(new CustomEvent('fitwave:assistant-emote',{detail:{state:motion}}));
  const mascot=document.querySelector<HTMLElement>('.mascot-float');
  if(mascot){mascot.classList.remove('a1-react');void mascot.offsetWidth;mascot.classList.add('a1-react');}
}
window.addEventListener('fitwave:rest-start',()=>emote('rest'));
window.addEventListener('fitwave:rest-finished',()=>emote('ready'));
window.addEventListener('fitwave:session-finished',()=>emote('done'));
window.addEventListener('fitwave:assistant-speak',()=>emote('thinking'));
export function motionState(){return motion;}
