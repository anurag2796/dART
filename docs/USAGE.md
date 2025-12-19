# dART Usage Guide

## Quick Start

### 1. Install Prerequisites

```bash
# macOS
brew install sbt

# Verify installation
sbt --version  # Should show 1.9.x
java --version # Should show Java 11+
```

### 2. Build the Project

```bash
cd /Users/anurag/codebase/biologicallyInspiredIntelligentSystems/projects/dART

# Compile
sbt compile

# Run tests
sbt test
```

### 3. Run Demos

#### Phase 1: Lateral Inhibition

```bash
sbt "runMain examples.LateralInhibition"
```

**Expected Output:**
```
✓ Created 5x5 grid of shunting neurons
✓ Registered all neurons with inhibitory field

► Applying stimulation pattern...

SIMULATION RESULTS
══════════════════════════════════════════════════════════

Activation Pattern (ASCII)
· · · · · 
· ░ ░ ░ · 
· ░ █ ░ · 
· ░ ░ ░ · 
· · · · · 

✓ SUCCESS: Contrast enhancement demonstrated!
```

#### Phase 2: ART-1 Learning

```bash
sbt "runMain examples.ART1Demo"
```

**Expected Output:**
```
EXPERIMENT 1: Learning with High Vigilance (ρ = 0.8)
══════════════════════════════════════════════════════════
Training on patterns A, B, C...
  Pattern A: 10101010
  Pattern B: 11110000
  Pattern C: 00001111

F2: Created new category 0
F2: Created new category 1
F2: Created new category 2

Testing recognition...
  Presenting Pattern A again: 10101010
F2: Category 0 matched → RESONANCE

✓ Experiment 1: High vigilance creates fine-grained categories
✓ Experiment 2: Low vigilance creates coarse categories
✓ Experiment 3: Stability maintained (no catastrophic forgetting)
```

---

## Programming API

### Creating a Shunting Neuron

```scala
import edu.rit.dart.core._
import edu.rit.dart.core.Messages._

val neuron = spawn(
  ShuntingNeuron(ShuntingNeuron.Config(
    neuronId = "my-neuron",
    A = 0.1,    // Decay rate
    B = 1.0,    // Max excitation
    D = 0.0,    // Max inhibition
    dt = 10     // Integration timestep (ms)
  ))
)

// Send excitatory input
neuron ! ExcitatorySignal(0.5)

// Query activation
neuron ! GetActivation(replyTo)
```

### Creating an ART-1 Network

```scala
import edu.rit.dart.art._
import edu.rit.dart.art.ARTMessages._

val network = spawn(
  ARTNetwork(ARTNetwork.Config(
    networkId = "my-art-network",
    inputSize = 8,           // 8-bit binary patterns
    maxCategories = 100,     // Max learned categories
    vigilance = 0.7,         // Vigilance parameter ρ
    learningRate = 1.0       // Fast learning
  ))
)

// Present a pattern
val pattern = Vector(1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0)
network ! PresentPattern(pattern)
```

---

## Parameter Tuning

### Shunting Neuron Parameters

| Parameter | Description | Typical Range | Effect |
|-----------|-------------|---------------|--------|
| `A` | Decay rate | 0.05 - 0.2 | Higher = faster decay |
| `B` | Max excitation | 1.0 - 10.0 | Upper bound on activation |
| `D` | Max inhibition | 0.0 or -B | Lower bound on activation |
| `dt` | Timestep (ms) | 5 - 20 | Smaller = more accurate, slower |

### ART-1 Parameters

| Parameter | Description | Typical Range | Effect |
|-----------|-------------|---------------|--------|
| `vigilance` (ρ) | Match threshold | 0.3 - 0.9 | High = fine categories, Low = coarse |
| `learningRate` (L) | Weight update rate | 0.5 - 1.0 | 1.0 = fast learning (ART-1 default) |
| `maxCategories` | Category limit | 10 - 1000 | Maximum memory capacity |

### Vigilance Effects

```scala
// High vigilance (ρ = 0.9): Fine-grained categories
// - Many categories created
// - Each pattern gets its own category
// - High plasticity, low generalization
val fineGrainedNetwork = ARTNetwork.Config(vigilance = 0.9, ...)

// Low vigilance (ρ = 0.3): Coarse categories  
// - Few categories created
// - Similar patterns grouped together
// - Low plasticity, high generalization
val coarseNetwork = ARTNetwork.Config(vigilance = 0.3, ...)

// Medium vigilance (ρ = 0.7): Balanced
val balancedNetwork = ARTNetwork.Config(vigilance = 0.7, ...)
```

---

## Testing

### Run All Tests

```bash
sbt test
```

### Run Specific Test Suite

```bash
sbt "testOnly *ShuntingNeuronSpec"
```

### Test Coverage

Phase 1 tests (`ShuntingNeuronSpec`):
- ✓ Bounded activation [D, B]
- ✓ Excitation increases activation
- ✓ Inhibition decreases activation
- ✓ Decay returns to baseline
- ✓ Reset functionality

Phase 2 tests (to be added):
- Pattern learning
- Pattern recognition
- Vigilance parameter effects
- Category formation

---

## Troubleshooting

### Issue: "sbt: command not found"

**Solution:**
```bash
brew install sbt
```

### Issue: Tests timeout

**Cause:** Akka actors need time to process messages

**Solution:** Increase sleep times in tests or use proper `receiveMessage` with timeout

### Issue: No contrast enhancement in lateral inhibition

**Possible causes:**
1. `inhibitoryStrength` too low (increase to 0.5-0.8)
2. Background excitation too high (reduce to 0.1-0.2)
3. Center excitation too low (increase to 0.8-1.0)

**Fix in `LateralInhibition.scala`:**
```scala
val inhibitoryField = context.spawn(
  InhibitoryField(InhibitoryField.Config(
    "LateralInhibition", 
    inhibitoryStrength = 0.5  // Increase if needed
  )),
  "inhibitory-field"
)
```

### Issue: ART-1 creates too many categories

**Cause:** Vigilance too high

**Solution:** Reduce vigilance parameter:
```scala
val network = ARTNetwork.Config(
  vigilance = 0.5,  // Reduced from 0.8
  ...
)
```

### Issue: ART-1 doesn't distinguish different patterns

**Cause:** Vigilance too low

**Solution:** Increase vigilance parameter:
```scala
val network = ARTNetwork.Config(
  vigilance = 0.8,  // Increased from 0.5
  ...
)
```

---

## Project Structure Reference

```
dART/
├── build.sbt                          # SBT configuration
├── README.md                          # Project overview
├── USAGE.md                           # This file
├── src/
│   ├── main/
│   │   ├── scala/
│   │   │   ├── edu/rit/dart/
│   │   │   │   ├── core/              # Phase 1: Neurons
│   │   │   │   │   ├── Messages.scala
│   │   │   │   │   ├── ShuntingNeuron.scala
│   │   │   │   │   └── InhibitoryField.scala
│   │   │   │   ├── art/               # Phase 2: ART-1
│   │   │   │   │   ├── ARTMessages.scala
│   │   │   │   │   ├── F1Layer.scala
│   │   │   │   │   ├── F2Layer.scala
│   │   │   │   │   ├── OrientingSubsystem.scala
│   │   │   │   │   └── ARTNetwork.scala
│   │   │   │   └── visualization/
│   │   │   │       └── ConsoleRenderer.scala
│   │   │   └── examples/
│   │   │       ├── LateralInhibition.scala    # Phase 1 demo
│   │   │       └── ART1Demo.scala             # Phase 2 demo
│   │   └── resources/
│   │       └── logback.xml            # Logging config
│   └── test/
│       └── scala/
│           └── edu/rit/dart/core/
│               └── ShuntingNeuronSpec.scala
└── project/
    └── build.properties
```

---

## Next Steps: Phase 3 & 4 (Future Work)

### Phase 3: Distributed Optimization
- Implement speculative execution for reset
- Add rollback mechanism (inspired by Pellegrini's work)
- Benchmark Actor model vs NumPy/PyTorch
- Scalability testing (100-1000 actors)

### Phase 4: Analysis & Research
- Empirical stability tests
- Convergence validation
- Comparison study: asynchronous vs synchronous ART
- Research paper draft

---

## References

- **Viability Analysis**: `../documents/grossberg_viability_analysis.md`
- **Research References**: `../documents/research_references.md`
- **Implementation Plan**: `../documents/implementation_plan.md`

---

## Support

For questions about:
- **Grossberg models**: See Scholarpedia ART entry
- **Akka actors**: https://doc.akka.io/
- **Project-specific**: Check implementation plan and viability analysis

---

## License

Academic research project - RIT CSCI 633
