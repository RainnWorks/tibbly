import { Hero } from "./sections/Hero";
import { LiveCounter } from "./sections/LiveCounter";
import { TrustStrip } from "./sections/TrustStrip";
import { ProblemSolution } from "./sections/ProblemSolution";
import { Demo } from "./sections/Demo";
import { Companion } from "./sections/Companion";
import { FreeTierStrip } from "./sections/FreeTierStrip";
import { FeatureGrid } from "./sections/FeatureGrid";
import { PricingTiers } from "./sections/PricingTiers";
import { FAQ } from "./sections/FAQ";
import { Footer } from "./sections/Footer";

export default function App() {
  return (
    <main className="min-h-screen bg-osrs-bg text-osrs-text">
      {/* Thin status strip (demoted LiveCounter per IA §2 verdict #10). */}
      <LiveCounter />
      <Hero />
      <TrustStrip />
      <ProblemSolution />
      <Demo />
      {/*
        Companion section sits after the demo (voice proof) and before
        the Free tier strip (conversion accelerator). The magical-moment
        beat earns the right to ask for the install. Brief: RAI-66.
      */}
      <Companion />
      <FreeTierStrip />
      <FeatureGrid />
      <PricingTiers />
      <FAQ />
      <Footer />
    </main>
  );
}
