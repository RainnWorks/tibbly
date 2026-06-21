import { Hero } from "./sections/Hero";
import { LiveCounter } from "./sections/LiveCounter";
import { ProblemSolution } from "./sections/ProblemSolution";
import { FeatureGrid } from "./sections/FeatureGrid";
import { Demo } from "./sections/Demo";
import { PricingTiers } from "./sections/PricingTiers";
import { FAQ } from "./sections/FAQ";
import { Footer } from "./sections/Footer";

export default function App() {
  return (
    <main className="min-h-screen bg-osrs-bg text-osrs-text">
      <Hero />
      <LiveCounter />
      <ProblemSolution />
      <FeatureGrid />
      <Demo />
      <PricingTiers />
      <FAQ />
      <Footer />
    </main>
  );
}
