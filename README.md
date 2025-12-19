# dART: Distributed Adaptive Resonance Theory

Implementation of Grossberg's ART-1 neural network using the Akka Actor Model in Scala.

## Project Overview

This project explores whether asynchronous message-passing actors can better simulate biological neural dynamics compared to traditional synchronized tensor frameworks. The implementation focuses on Adaptive Resonance Theory (ART-1), a neural network architecture designed to solve the stability-plasticity dilemma.

## Directory Structure

```
dART/
├── src/
│   ├── main/scala/edu/rit/dart/
│   │   ├── core/              # Phase 1: Basic neuron components
│   │   │   ├── Messages.scala
│   │   │   ├── ShuntingNeuron.scala
│   │   │   └── InhibitoryField.scala
│   │   ├── art/               # Phase 2: ART-1 network
│   │   │   ├── ARTMessages.scala
│   │   │   ├── F1Layer.scala
│   │   │   ├── F2Layer.scala
│   │   │   ├── OrientingSubsystem.scala
│   │   │   └── ARTNetwork.scala
│   │   ├── optimization/      # Phase 3: Performance enhancements
│   │   │   └── TransactionManager.scala
│   │   ├── analysis/          # Phase 4: Testing framework
│   │   │   └── StabilityTests.scala
│   │   └── visualization/
│   │       └── ConsoleRenderer.scala
│   ├── main/scala/examples/
│   │   ├── SimpleNeuronDemo.scala
│   │   └── SimpleART1Demo.scala
│   └── test/scala/
│       ├── core/ShuntingNeuronSpec.scala
│       └── optimization/TransactionManagerSpec.scala
├── benchmarks/
│   └── numpy_art1.py          # NumPy baseline for comparison
├── docs/                       # Documentation
└── build.sbt                   # SBT build configuration
```

## Technologies

- **Scala** 2.13.12
- **Akka** 2.8.5 (Actor Model framework)
- **ScalaTest** 3.2.17 (Testing)
- **Python/NumPy** (Baseline implementation)

## Building and Running

### Prerequisites
- Java 11 or higher
- SBT 1.9.7+
- Python 3.x (for benchmarks)

### Compile
```bash
sbt compile
```

### Run Tests
```bash
sbt test
```

### Run Demos
```bash
# Phase 1: Single neuron dynamics
sbt "runMain examples.SimpleNeuronDemo"

# Phase 2: ART-1 pattern learning
sbt "runMain examples.SimpleART1Demo"
```

### Run Benchmarks
```bash
cd benchmarks
python3 numpy_art1.py
```

## Phase Progress

- ✅ **Phase 1**: Core neuron components (shunting neurons, lateral inhibition)
- ✅ **Phase 2**: ART-1 network implementation (F1, F2, orienting subsystem)
  - Pattern learning and recognition working
  - Vigilance parameter controls category granularity
  - No catastrophic forgetting
- 🔄 **Phase 3**: Optimization (speculative execution, benchmarking)
  - Transaction manager implemented
  - NumPy baseline complete (145 patterns/sec @ 64-bit)
  - Integration with F2 layer in progress
- 📋 **Phase 4**: Analysis and documentation

## Key Features

### Shunting Neurons
Implements Grossberg's shunting inhibition equation:
```
dx/dt = -Ax + (B - x)[I+ + ΣS+] - (D + x)[I- + ΣS-]
```

- Bounded activation [D, B]
- Multiplicative (shunting) inhibition
- Asynchronous Euler integration

### ART-1 Network
- **F1 Layer**: Pattern comparison field
- **F2 Layer**: Category recognition with winner-take-all
- **Orienting Subsystem**: Vigilance-based reset mechanism
- Fast learning (one-shot category formation)
- Stable memory (no catastrophic forgetting)

### Distributed Architecture
- Each neuron is an independent Akka actor
- Message passing for neural communication
- O(N) lateral inhibition via mean-field approach
- Transaction manager for speculative execution

## Research Questions

1. Can asynchronous actors better model biological neural timing than synchronized frameworks?
2. Does the Actor Model provide advantages for distributed neural simulation?
3. How does performance compare to traditional NumPy/PyTorch implementations?

## References

- Carpenter & Grossberg (1987): "A Massively Parallel Architecture for a Self-Organizing Neural Pattern Recognition Machine"
- Grossberg (1988): "Nonlinear neural networks: Principles, mechanisms, and architectures"

## Author

Anurag Lakhera  
RIT CSCI 633  
Independent Study - Biologically Inspired Intelligent Systems

## License

MIT License - Academic Research Project
