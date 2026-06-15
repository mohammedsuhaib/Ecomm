export default function Home() {
  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: 640 }}>
      <h1>Town Basket — Store Admin</h1>
      <p>Order queue, catalogue management, inventory and store configuration.</p>
      <p style={{ color: '#666' }}>
        Admin scaffold (M1). The live order queue (SSE), catalogue CRUD and bulk
        inventory corrections arrive from M3 onward.
      </p>
    </main>
  );
}
