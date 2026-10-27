// الدخول بالهاتف: Firebase Phone Auth برمز SMS، فالرقم موثَّق الملكية فعلًا.
// الخادم لا يثق بفحص التطبيق: يقبل فقط رقمًا سودانيًا بصيغة E.164 من توكن Firebase نفسه.
// الدور يحدده الخادم دائمًا (زبون)؛ الترقية من شاشة المستخدمين فقط.

/** جوال سوداني: +249 ثم 9 أرقام تبدأ بـ 9 أو 1 (نفس قاعدة normalizeSudanPhone في التطبيق) */
export const SUDAN_E164 = /^\+249[19]\d{8}$/;

/** قيمة phone_number من توكن Firebase → الرقم إن كان سودانيًا صالحًا، وإلا null */
export function sudanPhoneFromToken(v: unknown): string | null {
  return typeof v === "string" && SUDAN_E164.test(v) ? v : null;
}
