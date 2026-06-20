import { Hero } from "./sections/Hero";
import { ProblemSolution } from "./sections/ProblemSolution";
import { FeatureGrid } from "./sections/FeatureGrid";
import { LiveCounter } from "./sections/LiveCounter";
import { PricingTiers } from "./sections/PricingTiers";
import { Demo } from "./sections/Demo";
import { FAQ } from "./sections/FAQ";
import { Footer } from "./sections/Footer";

export default function App() {
  return (
    <main className="min-h-screen bg-osrs-bg text-osrs-text">
      <Hero />
      <ProblemSolution />
      <FeatureGrid />
      <LiveCounter />
      <PricingTiers />
      <Demo />
      <FAQ />
      <Footer />
    </main>
  );
}
