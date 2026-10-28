// الدخول بالهاتف: Firebase Phone Auth برمز SMS، فالرقم موثَّق الملكية فعلًا.
// الخادم لا يثق بفحص التطبيق: يقرأ الرقم من توكن Firebase نفسه ويتحقق من صيغته.
// أي الدول تُقبل يحدده Firebase «SMS region policy» (Authentication ← Settings)، لا هذا الملف.
// الدور يحدده الخادم دائمًا (زبون)؛ الترقية من شاشة المستخدمين فقط.

/** صيغة E.164: + ثم مفتاح الدولة (لا يبدأ بصفر) ثم الرقم، 8 إلى 15 رقمًا بالمجموع */
export const PHONE_E164 = /^\+[1-9]\d{7,14}$/;

/** قيمة phone_number من توكن Firebase → الرقم إن كانت صيغته صالحة، وإلا null */
export function phoneFromToken(v: unknown): string | null {
  return typeof v === "string" && PHONE_E164.test(v) ? v : null;
}
