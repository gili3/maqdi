/** تطبيع النص العربي للبحث: يطابق نسخة التطبيق (Text.kt) تمامًا */
export function normalizeSearch(s: string): string {
  return s
    .normalize("NFKC")
    .toLowerCase()
    .replace(/[\u064B-\u065F\u0670\u0640]/g, "") // التشكيل والتطويل
    .replace(/[أإآٱ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ة/g, "ه")
    .replace(/\s+/g, " ")
    .trim();
}
