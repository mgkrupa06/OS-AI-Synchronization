# Operating Systems Programming Assignment — Report Outline

Complete this report using your own test results and screenshots. Replace all bracketed prompts.

## 1. Objective
Implement the Dining Philosophers problem for exactly four philosophers and four chopsticks using (a) semaphores and (b) a monitor, then generate HTML visualizations from actual execution events.

## 2. Environment
- Operating system: [fill in]
- Java/JDK version: [fill in]
- IDE / terminal: [fill in]

## 3. Semaphore implementation
Explain:
- One binary semaphore is assigned to each chopstick.
- A room semaphore permits at most three philosophers to enter the chopstick-acquisition area.
- Explain how this breaks circular wait and how `finally` blocks release acquired permits.
- Identify the critical section.

## 4. Monitor implementation
Explain:
- The monitor owns the shared state array.
- A philosopher becomes hungry and waits while either neighbour is eating.
- `wait()` releases the monitor lock while waiting.
- After a philosopher finishes, `notifyAll()` wakes waiting philosophers to recheck the condition.

## 5. HTML generation
Describe how each Java program records sequence number, elapsed time, philosopher ID, state, and event detail during the actual run. After all threads join, the program writes those recorded events into an HTML event table and replay timeline.

## 6. Testing and observations
- Test 1: [command and result]
- Test 2: [command and result]
- Screenshot: [insert your own terminal screenshot]
- Screenshot: [insert your own HTML screenshot]
- Observed event ordering differences between runs: [describe your own observation]

## 7. Comparison
Compare how each approach controls access, prevents deadlock, represents waiting, and handles fairness/starvation.

## 8. AI assistance and integrity
Use your completed `ai_prompts/prompts.md` and disclose manual changes. Do not claim unperformed testing.

## 9. References
List the Java documentation, course materials, or other sources you actually consulted, using the citation style requested by your instructor.
