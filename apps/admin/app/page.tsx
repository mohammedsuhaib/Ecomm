import AdminShell from './components/AdminShell';
import OrderQueue from './components/OrderQueue';

/**
 * Admin home = live order queue (M3), now gated behind staff/admin login (M4).
 * The shell renders the header (with the signed-in staffer + Logout) and wraps
 * the queue in <LoginGate>, so the queue only mounts once authenticated.
 */
export default function Home() {
  return (
    <AdminShell>
      <OrderQueue />
    </AdminShell>
  );
}
