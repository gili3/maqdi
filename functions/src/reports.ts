import { Query, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { LOCAL_OFFSET_MS } from "./config";
import { buildReport, DeliveredOrder } from "./reportLogic";
import { callable, db, docId, loadActor, obj, str } from "./util";

const MAX_ROWS = 3000;
const MAX_DAYS = 92;

function range(d: Record<string, unknown>) {
  const from = new Date(str(d.from, "from", 8, 40));
  const to = new Date(str(d.to, "to", 8, 40));
  if (isNaN(from.getTime()) || isNaN(to.getTime()) || to <= from) throw new HttpsError("invalid-argument", "invalid_range");
  if ((to.getTime() - from.getTime()) / 86_400_000 > MAX_DAYS) throw new HttpsError("invalid-argument", "invalid_range");
  return { from, to };
}

async function loadDelivered(from: Date, to: Date, filter: { storeId?: string | null; courierUid?: string | null }) {
  let q: Query = db.collection("orders").where("status", "==", "delivered");
  if (filter.storeId) q = q.where("storeId", "==", filter.storeId);
  if (filter.courierUid) q = q.where("courierUid", "==", filter.courierUid);
  q = q.where("deliveredAt", ">=", Timestamp.fromDate(from)).where("deliveredAt", "<", Timestamp.fromDate(to))
    .orderBy("deliveredAt", "asc").limit(MAX_ROWS + 1);
  const snap = await q.get();
  const rows: DeliveredOrder[] = snap.docs.slice(0, MAX_ROWS).map((doc) => {
    const o = doc.data();
    const a = (o.approvedSnapshot ?? {}) as Record<string, any>;
    return {
      storeId: o.storeId, storeName: o.storeName ?? "",
      deliveredAtMs: (o.deliveredAt as Timestamp).toMillis(),
      itemsTotal: a.itemsTotal ?? 0, deliveryFee: a.deliveryFee ?? 0, discount: a.discount ?? 0,
      collected: o.collectedAmount ?? 0,
      items: ((a.items as any[]) ?? []).map((l) => ({ productId: l.productId, name: l.name, qty: l.qty, lineTotal: l.lineTotal })),
    };
  });
  return { rows, truncated: snap.size > MAX_ROWS };
}

async function countStatus(status: string, from: Date, to: Date, storeId: string | null, courierUid: string | null) {
  let q: Query = db.collection("orders").where("status", "==", status);
  if (storeId) q = q.where("storeId", "==", storeId);
  if (courierUid) q = q.where("courierUid", "==", courierUid);
  const r = await q.where("createdAt", ">=", Timestamp.fromDate(from)).where("createdAt", "<", Timestamp.fromDate(to)).count().get();
  return r.data().count;
}

async function otherStatuses(from: Date, to: Date, storeId: string | null) {
  const names = ["rejected", "cancelled", "expired", "delivery_failed"];
  const counts = await Promise.all(names.map((s) => countStatus(s, from, to, storeId, null)));
  return Object.fromEntries(names.map((n, i) => [n, counts[i]]));
}

// ---------- تقرير الإدارة (كل المتاجر أو متجر واحد) ----------
export const adminReport = callable(async (req) => {
  const d = obj(req.data);
  const { from, to } = range(d);
  const storeId = d.storeId ? docId(d.storeId, "storeId") : null;
  await loadActor(req, ["admin"]);
  const { rows, truncated } = await loadDelivered(from, to, { storeId });
  return { ...buildReport(rows, LOCAL_OFFSET_MS), truncated, otherStatuses: await otherStatuses(from, to, storeId) };
});

// ---------- تقرير المتجر (متجره فقط، بلا إيراد المنصة) ----------
export const merchantReport = callable(async (req) => {
  const { from, to } = range(obj(req.data));
  const actor = await loadActor(req, ["merchant"]);
  if (!actor.storeId) throw new HttpsError("failed-precondition", "no_store");
  const { rows, truncated } = await loadDelivered(from, to, { storeId: actor.storeId });
  const r = buildReport(rows, LOCAL_OFFSET_MS);
  return {
    totals: { orders: r.totals.orders, itemsTotal: r.totals.itemsTotal },
    byDay: r.byDay.map((x) => ({ day: x.day, orders: x.orders, itemsTotal: x.itemsTotal })),
    topProducts: r.topProducts,
    truncated,
    otherStatuses: await otherStatuses(from, to, actor.storeId),
  };
});

// ---------- تقرير المندوب (توصيلاته هو) ----------
export const courierReport = callable(async (req) => {
  const { from, to } = range(obj(req.data));
  const actor = await loadActor(req, ["courier", "admin"]);
  const { rows, truncated } = await loadDelivered(from, to, { courierUid: actor.uid });
  const r = buildReport(rows, LOCAL_OFFSET_MS);
  return {
    totals: { orders: r.totals.orders, collected: r.totals.collected },
    byDay: r.byDay.map((x) => ({ day: x.day, orders: x.orders })),
    truncated,
    failed: await countStatus("delivery_failed", from, to, null, actor.uid),
  };
});
