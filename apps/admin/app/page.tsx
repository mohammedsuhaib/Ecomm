import AdminSections from './components/AdminSections';
import AdminShell from './components/AdminShell';

/**
 * Admin home, gated behind staff/admin login (M4). The shell renders the header
 * (with the signed-in staffer + Logout) and wraps the body in <LoginGate>; once
 * authenticated, <AdminSections> offers an Orders | Catalogue switch — the live
 * order queue (M3) and the catalogue-management UI (M5).
 */
export default function Home() {
  return (
    <AdminShell>
      <AdminSections />
    </AdminShell>
  );
}
