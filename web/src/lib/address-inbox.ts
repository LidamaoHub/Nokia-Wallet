export type AddressMessage={seq:number;from:string;text:string};
/** Fences historical bridge messages so unlock never resurrects a burnt record. */
export class AddressInbox {
 private sequence:number|null=null;
 take(messages:AddressMessage[]){const current=messages.reduce((n,m)=>Math.max(n,m.seq),0);if(this.sequence===null||current<this.sequence){this.sequence=current;return [];}
 const fresh=messages.filter(m=>m.seq>this.sequence!&&m.from==='phone'&&m.text.startsWith('NKW1\n'));this.sequence=current;return fresh;
 }
}
