import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dining Philosophers — semaphore solution.
 * Four philosophers, four chopsticks, real Java threads.
 * The HTML timeline is generated after execution from the recorded events.
 */
public class DiningSemaphore {
    private static final int N = 4;
    private static final int MEALS_PER_PHILOSOPHER = 2;
    private static final Semaphore[] chopsticks = new Semaphore[N];
    private static final Semaphore room = new Semaphore(N - 1, true);
    private static final EventLog LOG = new EventLog();

    static {
        for (int i = 0; i < N; i++) {
            chopsticks[i] = new Semaphore(1, true);
        }
    }

    private static final class Event {
        final long sequence;
        final long elapsedMs;
        final String philosopher;
        final String state;
        final String detail;

        Event(long sequence, long elapsedMs, String philosopher, String state, String detail) {
            this.sequence = sequence;
            this.elapsedMs = elapsedMs;
            this.philosopher = philosopher;
            this.state = state;
            this.detail = detail;
        }
    }

    private static final class EventLog {
        private final List<Event> events = new ArrayList<>();
        private final long startNanos = System.nanoTime();
        private final AtomicLong sequence = new AtomicLong();

        synchronized void add(int philosopher, String state, String detail) {
            long seq = sequence.incrementAndGet();
            long ms = (System.nanoTime() - startNanos) / 1_000_000;
            Event event = new Event(seq, ms, "P" + philosopher, state, detail);
            events.add(event);
            System.out.printf("[%03d | %4d ms] P%d | %-12s | %s%n",
                    seq, ms, philosopher, state, detail);
        }

        synchronized List<Event> snapshot() {
            return new ArrayList<>(events);
        }
    }

    private static final class Philosopher extends Thread {
        private final int id;
        private final int left;
        private final int right;

        Philosopher(int id) {
            super("Philosopher-" + id);
            this.id = id;
            left = id;
            right = (id + 1) % N;
        }

        @Override
        public void run() {
            try {
                for (int meal = 1; meal <= MEALS_PER_PHILOSOPHER; meal++) {
                    LOG.add(id, "THINKING", "Thinking before meal " + meal);
                    Thread.sleep(150 + id * 35L);

                    LOG.add(id, "HUNGRY", "Wants to eat meal " + meal);
                    LOG.add(id, "WAITING", "Waiting for a room permit");
                    room.acquire();

                    boolean leftHeld = false;
                    boolean rightHeld = false;
                    try {
                        LOG.add(id, "ACQUIRING", "Requesting left chopstick F" + left);
                        chopsticks[left].acquire();
                        leftHeld = true;
                        LOG.add(id, "ACQUIRING", "Picked up left chopstick F" + left);

                        LOG.add(id, "ACQUIRING", "Requesting right chopstick F" + right);
                        chopsticks[right].acquire();
                        rightHeld = true;
                        LOG.add(id, "EATING", "Eating meal " + meal + " with F" + left + " and F" + right);
                        Thread.sleep(180 + id * 20L);
                    } finally {
                        if (rightHeld) {
                            chopsticks[right].release();
                            LOG.add(id, "RELEASING", "Released right chopstick F" + right);
                        }
                        if (leftHeld) {
                            chopsticks[left].release();
                            LOG.add(id, "RELEASING", "Released left chopstick F" + left);
                        }
                        room.release();
                        LOG.add(id, "THINKING", "Finished meal " + meal + "; room permit returned");
                    }
                }
                LOG.add(id, "FINISHED", "Completed all meals");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LOG.add(id, "INTERRUPTED", "Thread interrupted; held chopsticks are released by finally");
            }
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("DINING PHILOSOPHERS — SEMAPHORE SOLUTION");
        System.out.println("4 philosophers | 4 chopsticks | " + MEALS_PER_PHILOSOPHER + " meals each");
        System.out.println("Event log below is the source data for the HTML timeline.\n");

        Philosopher[] philosophers = new Philosopher[N];
        for (int i = 0; i < N; i++) {
            philosophers[i] = new Philosopher(i);
            philosophers[i].start();
        }
        for (Philosopher philosopher : philosophers) philosopher.join();

        writeHtml(LOG.snapshot(), Path.of("generated_html", "dining_philosophers_semaphore.html"),
                "Dining Philosophers — Semaphore Solution", "Semaphore");
        System.out.println("\nAll philosophers have finished.");
        System.out.println("Generated: generated_html/dining_philosophers_semaphore.html");
    }

    private static void writeHtml(List<Event> events, Path output, String title, String solution)
            throws IOException {
        Files.createDirectories(output.getParent());
        StringBuilder rows = new StringBuilder();
        StringBuilder jsEvents = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            Event e = events.get(i);
            rows.append("<tr><td>").append(e.sequence).append("</td><td>")
                .append(e.elapsedMs).append("</td><td>").append(escape(e.philosopher))
                .append("</td><td>").append(escape(e.state)).append("</td><td>")
                .append(escape(e.detail)).append("</td></tr>\n");
            if (i > 0) jsEvents.append(",");
            jsEvents.append("{seq:").append(e.sequence)
                .append(",ms:").append(e.elapsedMs)
                .append(",p:'").append(jsEscape(e.philosopher))
                .append("',state:'").append(jsEscape(e.state))
                .append("',detail:'").append(jsEscape(e.detail)).append("'}");
        }
        jsEvents.append("]");
        String html = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s</title>
            <style>
            body{font-family:Arial,sans-serif;margin:24px;background:#f5f7fb;color:#202533}
            h1{margin-bottom:6px} .note{color:#515b6b}
            .stats{display:flex;gap:12px;flex-wrap:wrap;margin:18px 0}
            .stat{background:white;padding:12px 16px;border-radius:10px;box-shadow:0 1px 5px #ccd2df}
            button{padding:8px 12px;margin:4px;border:0;border-radius:7px;cursor:pointer;background:#273b67;color:white}
            input{vertical-align:middle} table{border-collapse:collapse;width:100%%;background:white;margin-top:16px}
            th,td{padding:9px;border-bottom:1px solid #e1e5ed;text-align:left;font-size:14px}
            th{background:#e9edf5;position:sticky;top:0}.timeline{display:grid;grid-template-columns:100px 1fr;gap:8px;margin:16px 0}
            .track{background:white;border-radius:8px;padding:8px;min-height:32px}
            .pill{display:inline-block;margin:2px;padding:4px 6px;border-radius:5px;background:#dce8ff;font-size:12px}
            .controls{margin:12px 0} .table-wrap{overflow:auto;max-height:480px}
            </style></head><body>
            <h1>%s</h1>
            <p class="note">This page was generated by the Java program from its recorded thread events after the run. It is not a pre-scripted trace.</p>
            <div class="stats"><div class="stat"><b>Solution</b><br>%s</div>
            <div class="stat"><b>Philosophers</b><br>4</div><div class="stat"><b>Chopsticks</b><br>4</div>
            <div class="stat"><b>Recorded events</b><br><span id="count"></span></div></div>
            <h2>Event timeline</h2>
            <p class="note">Use Play/Pause or the slider to replay the events in the order they were recorded.</p>
            <div class="controls"><button id="play">Play</button><button id="pause">Pause</button><button id="reset">Reset</button>
            <input id="seek" type="range" min="0" max="0" value="0" style="width:min(520px,60%%)">
            <span id="position"></span></div>
            <div id="timeline" class="timeline"></div>
            <h2>Actual execution log</h2><div class="table-wrap"><table><thead><tr><th>#</th><th>Elapsed ms</th><th>Philosopher</th><th>State</th><th>Event detail</th></tr></thead>
            <tbody>%s</tbody></table></div>
            <script>
            const events=%s; document.getElementById('count').textContent=events.length;
            const timeline=document.getElementById('timeline'), seek=document.getElementById('seek');
            seek.max=Math.max(0,events.length-1); let cursor=0, timer=null;
            function render(){cursor=Math.max(0,Math.min(cursor,events.length-1));seek.value=cursor;
              document.getElementById('position').textContent=(events.length?cursor+1:0)+' / '+events.length;
              timeline.innerHTML='';
              for(let p=0;p<4;p++){let label=document.createElement('div');label.textContent='Philosopher P'+p;
                timeline.appendChild(label);let track=document.createElement('div');track.className='track';
                events.slice(0,cursor+1).filter(e=>e.p==='P'+p).slice(-5).forEach(e=>{
                  let pill=document.createElement('span');pill.className='pill';pill.textContent=e.state+' — '+e.detail;track.appendChild(pill);
                });timeline.appendChild(track);}
            }
            document.getElementById('play').onclick=()=>{if(timer)return;timer=setInterval(()=>{if(cursor>=events.length-1){clearInterval(timer);timer=null;return;}cursor++;render();},350);};
            document.getElementById('pause').onclick=()=>{clearInterval(timer);timer=null;};
            document.getElementById('reset').onclick=()=>{clearInterval(timer);timer=null;cursor=0;render();};
            seek.oninput=()=>{cursor=Number(seek.value);render();};render();
            </script></body></html>
            """.formatted(escape(title), escape(title), escape(solution), rows, jsEvents);
        Files.writeString(output, html, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private static String jsEscape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'")
                    .replace("\r", " ").replace("\n", " ");
    }
}
