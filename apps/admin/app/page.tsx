import OrderQueue from './components/OrderQueue';

/** Admin home = live order queue (M3). Catalogue/inventory polish lands later. */
export default function Home() {
  return (
    <>
      <header className="admin-header">
        <div className="admin-header-inner">
          <span className="admin-brand">
            <span className="logo" aria-hidden>
              🧺
            </span>
            Town Basket — Store Admin
          </span>
          <span className="admin-sub">Order queue</span>
        </div>
      </header>
      <main className="admin-main">
        <OrderQueue />
      </main>
    </>
  );
}
