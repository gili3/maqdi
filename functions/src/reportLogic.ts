/** منطق التقارير النقي (بلا اعتماد على Firebase) حتى يمكن اختباره */
export interface DeliveredLine { productId: string; name: string; qty: number; lineTotal: number }

export interface DeliveredOrder {
  storeId: string;
  storeName: string;
  deliveredAtMs: number;
  itemsTotal: number;
  deliveryFee: number;
  discount: number;
  collected: number;
  items: DeliveredLine[];
}

export interface Report {
  totals: { orders: number; itemsTotal: number; deliveryRevenue: number; collected: number };
  byDay: { day: string; orders: number; itemsTotal: number; deliveryRevenue: number }[];
  byStore: { storeId: string; storeName: string; orders: number; itemsTotal: number }[];
  topProducts: { productId: string; name: string; qty: number; revenue: number }[];
}

/** يوم الطلب بتوقيت السودان بصيغة YYYY-MM-DD */
export function localDay(ms: number, offsetMs: number): string {
  return new Date(ms + offsetMs).toISOString().slice(0, 10);
}

export function buildReport(orders: DeliveredOrder[], offsetMs: number, topN = 10): Report {
  const totals = { orders: 0, itemsTotal: 0, deliveryRevenue: 0, collected: 0 };
  const days = new Map<string, { day: string; orders: number; itemsTotal: number; deliveryRevenue: number }>();
  const stores = new Map<string, { storeId: string; storeName: string; orders: number; itemsTotal: number }>();
  const products = new Map<string, { productId: string; name: string; qty: number; revenue: number }>();

  for (const o of orders) {
    const revenue = o.deliveryFee - o.discount; // الخصم يُحمَّل على إيراد التوصيل
    totals.orders += 1;
    totals.itemsTotal += o.itemsTotal;
    totals.deliveryRevenue += revenue;
    totals.collected += o.collected;

    const day = localDay(o.deliveredAtMs, offsetMs);
    const d = days.get(day) ?? { day, orders: 0, itemsTotal: 0, deliveryRevenue: 0 };
    d.orders += 1; d.itemsTotal += o.itemsTotal; d.deliveryRevenue += revenue;
    days.set(day, d);

    const s = stores.get(o.storeId) ?? { storeId: o.storeId, storeName: o.storeName, orders: 0, itemsTotal: 0 };
    s.orders += 1; s.itemsTotal += o.itemsTotal;
    stores.set(o.storeId, s);

    for (const l of o.items) {
      const key = `${o.storeId}:${l.productId}`;
      const p = products.get(key) ?? { productId: l.productId, name: l.name, qty: 0, revenue: 0 };
      p.qty += l.qty; p.revenue += l.lineTotal;
      products.set(key, p);
    }
  }

  return {
    totals,
    byDay: [...days.values()].sort((a, b) => a.day.localeCompare(b.day)),
    byStore: [...stores.values()].sort((a, b) => b.itemsTotal - a.itemsTotal),
    topProducts: [...products.values()].sort((a, b) => b.qty - a.qty || b.revenue - a.revenue).slice(0, topN),
  };
}
