import LoginGate from './components/LoginGate';
import DeliveryQueue from './components/DeliveryQueue';

export default function Home() {
  return (
    <LoginGate>
      <DeliveryQueue />
    </LoginGate>
  );
}
