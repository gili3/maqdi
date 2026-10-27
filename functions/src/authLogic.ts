// حسابات الهاتف: لا رمز SMS، بل بريد داخلي مشتق من الرقم + كلمة مرور.
// الرقم هنا غير موثَّق الملكية، لذلك لا يُمنح هذا الحساب إلا دور «زبون» (الدور يحدده الخادم دائمًا).
export const PHONE_EMAIL_DOMAIN = "phone.maqdi.app";

/** "249912345678@phone.maqdi.app" → "+249912345678"، وأي بريد آخر → null */
export function phoneFromAuthEmail(email: unknown): string | null {
  if (typeof email !== "string") return null;
  const m = /^(\d{8,15})@phone\.maqdi\.app$/.exec(email.trim().toLowerCase());
  return m ? `+${m[1]}` : null;
}
