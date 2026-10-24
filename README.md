# مقضي — الجزء 1: الخلفية (Firebase)

مشروع Firebase: `maqdi-19689` — المنطقة: `europe-west3`.

## المحتويات
- `firestore.rules` / `storage.rules`: قواعد الأمان. كل الكتابة الحساسة مغلقة على التطبيقات وتمر عبر Cloud Functions.
- `firestore.indexes.json`: الفهارس المطلوبة.
- `functions/src`: الدوال (TypeScript).
- `functions/test`: اختبارات آلة الحالات.
- `.github/workflows/firebase-deploy.yml`: النشر عبر GitHub Actions (يشغّل الاختبارات أولًا).

## أول تشغيل
1. أضف السر `FIREBASE_SERVICE_ACCOUNT` في GitHub (Settings → Secrets → Actions).
2. ارفع كل الملفات إلى `main`، ثم Actions → Firebase Deploy → Run workflow.
3. إن ظهر خطأ صلاحيات في النشر (PERMISSION_DENIED)، امنح حساب الخدمة الأدوار التالية من Google Cloud Console → IAM:
   `Cloud Functions Admin`, `Service Account User`, `Cloud Scheduler Admin`, `Artifact Registry Writer`, `Cloud Build Editor`, `Firebase Rules Admin`, `Cloud Datastore Index Admin`.
4. **إنشاء أول مدير:** في Firestore Console أنشئ مجموعة `invites` ووثيقة معرّفها بريدك على Gmail **بأحرف صغيرة**، بحقلين: `role` = `admin` (نص) و`used` = `false` (منطقي). أول تسجيل دخول لك بهذا البريد في تطبيق الشركاء ينشئ حساب المدير.

## نموذج البيانات
| المجموعة | المحتوى |
|---|---|
| `users/{uid}` | email, name, phone, role (`customer/merchant/admin/courier`), storeId, status (`active/suspended`), isTestAccount, savedAddress, fcmTokens |
| `stores/{id}` | name, category, phone, address, status, isOpen |
| `products/{id}` | storeId, name, description, category, price (جنيه صحيح), stockQty (أو null), isAvailable, imagePath |
| `orders/{id}` | الزبون والمتجر والحالة وrequestedItems وproposal وapprovedSnapshot وrevision والإجماليات وexpiresAt والمواعيد وأسباب الرفض/الإلغاء/الفشل وsettlementStatus |
| `orders/{id}/history/*` | كل انتقال: من، إلى، من نفّذ، متى |
| `financial_entries/{id}` | قيود غير قابلة للتعديل: ledger (`cash/store_payable/delivery_revenue`) ومبلغ موقّع وربط بقيد التصحيح |
| `store_balances/{storeId}` | payable (المستحق) وsettled (المسدَّد) |
| `store_settlements/{id}` | تسويات المتاجر وتوزيعها على الطلبات |
| `expenses`, `audit_logs`, `settings/app`, `invites`, `idempotency` | كما هو واضح من أسمائها |

## واجهة الدوال (للتطبيقات)
جميعها `httpsCallable` بمنطقة `europe-west3`.

| الدالة | من يستدعيها | المدخلات |
|---|---|---|
| `ensureProfile` | أي مستخدم بعد Google | `{name?}` |
| `createOrder` | زبون | `{submissionId, storeId, items:[{productId, qty}], phone, address, locationNote?}` — أنشئ `submissionId` مرة واحدة لكل طلب وأعد استخدامه عند إعادة المحاولة |
| `proposeOrder` | تاجر/مدير | `{orderId, items:[{productId, qty, unitPrice?}], note?}` |
| `respondToProposal` | زبون | `{orderId, revision, decision:'approve'|'decline'}` |
| `changeOrderStatus` | حسب الدور | `{orderId, to, reason?}` — `to`: `rejected, cancelled, preparing, ready_for_pickup, out_for_delivery, delivered, delivery_failed` (السبب إلزامي للرفض والإلغاء وتعذر التسليم) |
| `upsertProduct`, `setStoreOpen` | تاجر/مدير | انظر `catalog.ts` |
| `adminUpsertStore`, `adminInviteUser`, `adminSetUserStatus`, `adminUpdateSettings` | مدير | انظر `catalog.ts` و`users.ts` |
| `adminRecordSettlement` | مدير | `{storeId, amount, settlementKey, note?}` |
| `adminCorrectEntry` | مدير | `{entryId, reason, replacementAmount?}` |
| `adminAddExpense` | مدير | `{amount, category, expenseKey, note?}` |
| `adminExportFinancialCsv` | مدير | `{from, to, storeId?}` (تواريخ ISO) |
| `adminDashboard` | مدير | `{}` |

## قرارات اتُّخذت (وكلها قابلة للتغيير)
- **تعذّر التسليم (`delivery_failed`):** تعود البضاعة للمتجر ولا تُسجَّل قيود مالية. الاستثناءات تُسجَّل يدويًا بقيد تصحيح.
- **الإلغاء:** الزبون قبل بدء التجهيز فقط؛ التاجر في `confirmed` و`preparing`؛ المدير في أي حالة غير نهائية قبل التوصيل.
- **الخصم:** الحقل موجود (0 دائمًا حاليًا). إن وُجد خصم يُخصم من إيراد التوصيل ولا يمس مستحق المتجر.
- **رسوم التوصيل** تُثبَّت في الطلب عند إنشائه، وتغييرها لاحقًا يؤثر على الطلبات الجديدة فقط.
- **المخزون:** الكمية تُدار يدويًا من التاجر ولا تُخصم تلقائيًا.
- **الأدوية بوصفة طبية:** غير مدعومة في هذه المرحلة.

## ما لم يُختبر بعد (بصراحة)
- اختبارات آلة الحالات (8 اختبارات) نُفّذت فعلًا وتنجح.
- كود الدوال **لم يُبنَ ولم يُنشر** بعد؛ سيُبنى أول مرة في GitHub Actions.
- قواعد الأمان **لم تُختبر** بعد؛ اختبارها بمحاكي Firebase مخطط له قبل الإطلاق.
- لا يوجد App Check ولا حد لمعدل الطلبات حتى الآن.
