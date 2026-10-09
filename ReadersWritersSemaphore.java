import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;

public class ReadersWritersSemaphore {
    static final int READERS = 3;
    static final int WRITERS = 2;
    static final int OPERATIONS = 2;

    // resource protects sharedData; readerMutex protects activeReaders.
    static final Semaphore resource = new Semaphore(1, true);
    static final Semaphore readerMutex = new Semaphore(1, true);

    static int activeReaders = 0;
    static int sharedData = 0;
    static final List<String> events =
            Collections.synchronizedList(new ArrayList<>());

    static void log(String actor, String state, String detail) {
        events.add(actor + " | " + state + " | " + detail);
    }

    static void reader(int id) {
        String name = "R" + id;
        try {
            for (int i = 1; i <= OPERATIONS; i++) {
                log(name, "HUNGRY", "Wants to read operation " + i);

                readerMutex.acquire();
                try {
                    activeReaders++;
                    if (activeReaders == 1) {
                        log(name, "WAITING", "First reader requests resource");
                        resource.acquire();
                    }
                    log(name, "READING", "Active readers = " + activeReaders);
                } finally {
                    readerMutex.release();
                }

                try {
                    int value = sharedData;
                    log(name, "READING", "Read sharedData = " + value);
                    Thread.sleep(150);
                } finally {
                    readerMutex.acquire();
                    try {
                        activeReaders--;
                        log(name, "RELEASING", "Active readers = " + activeReaders);
                        if (activeReaders == 0) {
                            resource.release();
                            log(name, "RELEASED", "Last reader released resource");
                        }
                    } finally {
                        readerMutex.release();
                    }
                }
                Thread.sleep(100);
            }
            log(name, "FINISHED", "Completed all reads");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log(name, "INTERRUPTED", "Reader interrupted");
        }
    }

    static void writer(int id) {
        String name = "W" + id;
        try {
            for (int i = 1; i <= OPERATIONS; i++) {
                log(name, "WAITING", "Requests exclusive write access");
                resource.acquire();
                try {
                    log(name, "WRITING", "Entered writing section");
                    sharedData++;
                    log(name, "UPDATED", "sharedData = " + sharedData);
                    Thread.sleep(150);
                } finally {
                    resource.release();
                    log(name, "RELEASED", "Released resource");
                }
                Thread.sleep(100);
            }
            log(name, "FINISHED", "Completed all writes");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log(name, "INTERRUPTED", "Writer interrupted");
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    // Produces a safely quoted JavaScript string for the event array.
    private static String jsQuote(String value) {
        return "\"" + value.replace("\\", "\\\\")
                           .replace("\"", "\\\"")
                           .replace("\r", "\\r")
                           .replace("\n", "\\n")
                           .replace("\u2028", "\\u2028")
                           .replace("\u2029", "\\u2029") + "\"";
    }

    public static void main(String[] args) throws Exception {
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < READERS; i++) {
            final int id = i;
            threads.add(new Thread(() -> reader(id), "Reader-" + id));
        }
        for (int i = 0; i < WRITERS; i++) {
            final int id = i;
            threads.add(new Thread(() -> writer(id), "Writer-" + id));
        }

        for (Thread t : threads) {
            t.start();
            Thread.sleep(30);
        }
        for (Thread t : threads) {
            t.join();
        }

        log("SYSTEM", "END", "Final sharedData = " + sharedData);

        StringBuilder rows = new StringBuilder();
        StringBuilder eventArray = new StringBuilder();
        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                String event = events.get(i);
                String[] parts = event.split(" \\\\| ", 3);
                String actor = parts.length > 0 ? parts[0] : "SYSTEM";
                String state = parts.length > 1 ? parts[1] : "EVENT";
                String detail = parts.length > 2 ? parts[2] : event;

                rows.append("<tr id=\"row-").append(i).append("\"><td>")
                    .append(i + 1).append("</td><td>")
                    .append(escapeHtml(actor)).append("</td><td>")
                    .append(escapeHtml(state)).append("</td><td>")
                    .append(escapeHtml(detail)).append("</td></tr>\n");

                if (i > 0) eventArray.append(",\n");
                eventArray.append("  { actor: ").append(jsQuote(actor))
                          .append(", state: ").append(jsQuote(state))
                          .append(", detail: ").append(jsQuote(detail)).append(" }");
            }
        }

        String html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Readers-Writers — Semaphore Simulation</title>
            <style>
              :root { color-scheme: light; }
              body { font-family: Arial, sans-serif; margin: 24px; color: #202124; background: #f7f8fa; }
              h1 { margin-bottom: 8px; }
              .summary, .player, .log-panel { background: white; border: 1px solid #d9dee7; border-radius: 12px; padding: 18px; margin: 16px 0; }
              .summary { display: flex; flex-wrap: wrap; gap: 12px 28px; }
              .metric { font-size: 16px; }
              .metric strong { display: block; font-size: 22px; margin-top: 5px; }
              .status { border-left: 6px solid #64748b; padding: 12px 14px; background: #f1f5f9; border-radius: 6px; margin: 12px 0; }
              .status.READING { border-color: #16803c; background: #ecfdf3; }
              .status.WRITING, .status.UPDATED { border-color: #b45309; background: #fff7ed; }
              .status.WAITING, .status.HUNGRY { border-color: #64748b; background: #f1f5f9; }
              .controls { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
              button { border: 0; border-radius: 7px; padding: 10px 16px; cursor: pointer; background: #1d4ed8; color: white; font-size: 14px; }
              button.secondary { background: #475569; }
              button:disabled { opacity: .55; cursor: not-allowed; }
              input[type=range] { width: min(600px, 100%); }
              .slider-wrap { margin-top: 14px; }
              .progress { height: 8px; background: #e2e8f0; border-radius: 20px; overflow: hidden; margin-top: 10px; }
              .progress > div { height: 100%; width: 0; background: #2563eb; transition: width .12s linear; }
              .table-wrap { overflow-x: auto; max-height: 460px; }
              table { border-collapse: collapse; width: 100%; background: white; }
              th, td { border: 1px solid #d1d5db; padding: 9px 10px; text-align: left; }
              th { background: #e5e7eb; position: sticky; top: 0; }
              tbody tr.current { outline: 3px solid #2563eb; outline-offset: -3px; background: #dbeafe; }
              tbody tr.past { background: #f8fafc; color: #64748b; }
              .hint { color: #475569; font-size: 14px; }
              @media (max-width: 600px) { body { margin: 12px; } .summary, .player, .log-panel { padding: 12px; } }
            </style>
            </head>
            <body>
              <h1>Readers-Writers — Semaphore Solution</h1>
              <p class="hint">Replay is driven by the events recorded during this program's real threaded execution.</p>
              <section class="summary">
                <div class="metric">Readers<strong>3</strong></div>
                <div class="metric">Writers<strong>2</strong></div>
                <div class="metric">Operations per thread<strong>2</strong></div>
                <div class="metric">Final sharedData<strong>FINAL_VALUE</strong></div>
                <div class="metric">Recorded events<strong id="totalEvents">0</strong></div>
              </section>

              <section class="player">
                <h2>Execution Timeline</h2>
                <div id="eventCard" class="status">
                  <strong id="eventTitle">Ready to replay</strong>
                  <p id="eventDetail">Press Play to step through the recorded execution.</p>
                </div>
                <div class="controls">
                  <button id="playBtn" onclick="play()">▶ Play</button>
                  <button id="pauseBtn" class="secondary" onclick="pause()">⏸ Pause</button>
                  <button class="secondary" onclick="resetReplay()">↻ Reset</button>
                  <span id="counter">Event 0 of 0</span>
                </div>
                <div class="slider-wrap">
                  <input id="timeline" type="range" min="0" max="0" value="0" step="1"
                         aria-label="Execution timeline" oninput="jumpTo(this.value)">
                  <div class="progress"><div id="progressBar"></div></div>
                </div>
              </section>

              <section class="log-panel">
                <h2>Actual Execution Log</h2>
                <p class="hint">The highlighted row follows the timeline. Use the slider to inspect any recorded event.</p>
                <div class="table-wrap">
                  <table>
                    <thead><tr><th>No.</th><th>Actor</th><th>State</th><th>Event details</th></tr></thead>
                    <tbody>
                      ROWS
                    </tbody>
                  </table>
                </div>
              </section>

              <script>
                const events = [
            EVENTS_JSON
                ];
                let index = -1;
                let timer = null;
                const timeline = document.getElementById('timeline');
                document.getElementById('totalEvents').textContent = events.length;
                timeline.max = Math.max(0, events.length - 1);

                function showEvent(i) {
                  if (!events.length) return;
                  index = Math.max(0, Math.min(Number(i), events.length - 1));
                  const e = events[index];
                  document.getElementById('eventTitle').textContent =
                    '#' + (index + 1) + ' — ' + e.actor + ' | ' + e.state;
                  document.getElementById('eventDetail').textContent = e.detail;
                  const card = document.getElementById('eventCard');
                  card.className = 'status ' + e.state;
                  document.getElementById('counter').textContent =
                    'Event ' + (index + 1) + ' of ' + events.length;
                  timeline.value = index;
                  document.getElementById('progressBar').style.width =
                    ((index + 1) / events.length * 100) + '%';
                  document.querySelectorAll('tbody tr').forEach((row, rowIndex) => {
                    row.classList.toggle('current', rowIndex === index);
                    row.classList.toggle('past', rowIndex < index);
                  });
                  const currentRow = document.getElementById('row-' + index);
                  if (currentRow) currentRow.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
                }
                function play() {
                  if (timer !== null) return;
                  if (index >= events.length - 1) showEvent(0);
                  timer = setInterval(() => {
                    if (index >= events.length - 1) { pause(); return; }
                    showEvent(index + 1);
                  }, 650);
                }
                function pause() {
                  if (timer !== null) clearInterval(timer);
                  timer = null;
                }
                function resetReplay() {
                  pause();
                  index = -1;
                  document.getElementById('eventTitle').textContent = 'Ready to replay';
                  document.getElementById('eventDetail').textContent =
                    'Press Play to step through the recorded execution.';
                  document.getElementById('eventCard').className = 'status';
                  document.getElementById('counter').textContent = 'Event 0 of ' + events.length;
                  timeline.value = 0;
                  document.getElementById('progressBar').style.width = '0%';
                  document.querySelectorAll('tbody tr').forEach(row => row.classList.remove('current', 'past'));
                }
                function jumpTo(value) {
                  pause();
                  showEvent(value);
                }
                document.getElementById('pauseBtn').addEventListener('click', pause);
                resetReplay();
              </script>
            </body>
            </html>
            """.replace("FINAL_VALUE", String.valueOf(sharedData))
               .replace("ROWS", rows.toString())
               .replace("EVENTS_JSON", eventArray.toString());

        Path folder = Path.of("generated_html");
        Files.createDirectories(folder);
        Path output = folder.resolve("readers_writers_semaphore.html");
        Files.writeString(output, html);

        System.out.println("All readers and writers finished.");
        System.out.println("Final sharedData = " + sharedData);
        System.out.println("Recorded events = " + events.size());
        System.out.println("Generated: " + output.toAbsolutePath());
    }
}
