import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReadersWritersMonitor {
    private static final int READERS = 3;
    private static final int WRITERS = 2;
    private static final int OPERATIONS = 2;

    private static int activeReaders = 0;
    private static int sharedData = 0;
    private static boolean writerActive = false;
    private static final List<String> events = Collections.synchronizedList(new ArrayList<>());

    private static void log(String actor, String state, String detail) {
        events.add(actor + " | " + state + " | " + detail);
    }

    // Monitor methods: synchronized methods protect the shared state.
    private static synchronized int startRead(String name, int operation) throws InterruptedException {
        log(name, "HUNGRY", "Requests read operation " + operation);
        while (writerActive) {
            log(name, "WAITING", "Writer is active; waiting to read");
            ReadersWritersMonitor.class.wait();
        }
        activeReaders++;
        log(name, "READING", "Entered read section; active readers = " + activeReaders);
        int value = sharedData;
        log(name, "READING", "Read sharedData = " + value);
        return value;
    }

    private static synchronized void endRead(String name) {
        activeReaders--;
        log(name, "RELEASING", "Left read section; active readers = " + activeReaders);
        if (activeReaders == 0) {
            ReadersWritersMonitor.class.notifyAll();
            log(name, "NOTIFY", "Last reader left; notified waiting threads");
        }
    }

    private static synchronized void startWrite(String name, int operation) throws InterruptedException {
        log(name, "WAITING", "Requests write operation " + operation);
        while (writerActive || activeReaders > 0) {
            log(name, "WAITING", "Waiting for all readers/writer to leave");
            ReadersWritersMonitor.class.wait();
        }
        writerActive = true;
        log(name, "WRITING", "Entered exclusive write section");
    }

    private static synchronized void performWrite(String name) {
        sharedData++;
        log(name, "UPDATED", "sharedData = " + sharedData);
    }

    private static synchronized void endWrite(String name) {
        writerActive = false;
        log(name, "RELEASED", "Left write section");
        ReadersWritersMonitor.class.notifyAll();
        log(name, "NOTIFY", "Writer notified waiting threads");
    }

    private static void reader(int id) {
        String name = "R" + id;
        try {
            for (int operation = 1; operation <= OPERATIONS; operation++) {
                startRead(name, operation);
                try {
                    Thread.sleep(150);
                } finally {
                    endRead(name);
                }
                Thread.sleep(100);
            }
            log(name, "FINISHED", "Completed all reads");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log(name, "INTERRUPTED", "Reader interrupted");
        }
    }

    private static void writer(int id) {
        String name = "W" + id;
        try {
            for (int operation = 1; operation <= OPERATIONS; operation++) {
                startWrite(name, operation);
                try {
                    performWrite(name);
                    Thread.sleep(150);
                } finally {
                    endWrite(name);
                }
                Thread.sleep(100);
            }
            log(name, "FINISHED", "Completed all writes");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log(name, "INTERRUPTED", "Writer interrupted");
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String createHtml() {
        StringBuilder json = new StringBuilder("[");
        StringBuilder rows = new StringBuilder();
        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                String event = events.get(i);
                if (i > 0) json.append(",");
                json.append("\"").append(event.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\r", " ").replace("\n", " ")).append("\"");
                String[] parts = event.split(" \\| ", 3);
                String actor = parts.length > 0 ? parts[0] : "";
                String state = parts.length > 1 ? parts[1] : "";
                String detail = parts.length > 2 ? parts[2] : "";
                rows.append("<tr><td>").append(i + 1).append("</td><td>").append(escape(actor))
                    .append("</td><td>").append(escape(state)).append("</td><td>")
                    .append(escape(detail)).append("</td></tr>");
            }
        }
        json.append("]");
        return """
            <!doctype html>
            <html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Readers-Writers — Monitor Simulation</title>
            <style>
            body{font-family:Arial,sans-serif;margin:24px;background:#f6f8fb;color:#172033}h1{margin-bottom:8px}
            .card{background:white;border:1px solid #d9e0ea;border-radius:12px;padding:16px;margin:14px 0}
            .controls{display:flex;gap:10px;align-items:center;flex-wrap:wrap}button{padding:9px 16px;border:0;border-radius:7px;background:#2457a7;color:white;cursor:pointer}
            button:disabled{opacity:.45;cursor:default}input[type=range]{flex:1;min-width:180px}#eventCard{border-left:6px solid #2457a7}
            .meta{color:#536176}table{border-collapse:collapse;width:100%;background:white}th,td{border:1px solid #d2d9e3;padding:9px;text-align:left}th{background:#e8edf5}
            tr.active{background:#fff0c2} .pill{display:inline-block;background:#e8edf5;padding:4px 8px;border-radius:12px;margin-right:6px}
            </style></head><body>
            <h1>Readers-Writers — Monitor Solution</h1>
            <p class="meta">Readers: 3 | Writers: 2 | Operations per thread: 2 | Synchronization: synchronized, wait(), notifyAll()</p>
            <div class="card"><strong>Final sharedData: FINAL_VALUE</strong><p class="meta">The timeline replays the event log recorded by the actual Java threads.</p>
            <div class="controls"><button id="play">▶ Play</button><button id="pause">⏸ Pause</button><button id="reset">↻ Reset</button><span id="counter">Event 0 / TOTAL</span>
            <input id="slider" type="range" min="0" max="TOTAL" value="0"></div></div>
            <div class="card" id="eventCard"><div><span class="pill" id="actor">Ready</span><span class="pill" id="state">Waiting</span></div><h2 id="detail">Press Play to begin the simulation.</h2></div>
            <div class="card"><h2>Actual Execution Log</h2><table><thead><tr><th>No.</th><th>Thread</th><th>State</th><th>Event details</th></tr></thead><tbody id="logRows">ROWS</tbody></table></div>
            <script>
            const events=EVENTS; let index=0, timer=null;
            const slider=document.getElementById('slider'), counter=document.getElementById('counter');
            function render(){
              counter.textContent=`Event ${index} / ${events.length}`; slider.value=index;
              const e=index?events[index-1]:null;
              document.getElementById('actor').textContent=e?(e.split(' | ')[0]):'Ready';
              document.getElementById('state').textContent=e?(e.split(' | ')[1]||''):'Waiting';
              document.getElementById('detail').textContent=e?(e.split(' | ').slice(2).join(' | ')):'Press Play to begin the simulation.';
              document.querySelectorAll('#logRows tr').forEach((r,i)=>r.classList.toggle('active',i===index-1));
              if(index>=events.length) stop();
            }
            function stop(){if(timer!==null){clearInterval(timer);timer=null;}}
            document.getElementById('play').onclick=()=>{if(timer!==null)return;if(index>=events.length)index=0;timer=setInterval(()=>{if(index<events.length){index++;render()}else stop()},450)};
            document.getElementById('pause').onclick=stop;
            document.getElementById('reset').onclick=()=>{stop();index=0;render()};
            slider.oninput=()=>{stop();index=Number(slider.value);render()};render();
            </script></body></html>
            """.replace("FINAL_VALUE", String.valueOf(sharedData))
               .replace("TOTAL", String.valueOf(events.size()))
               .replace("ROWS", rows.toString())
               .replace("EVENTS", json.toString());
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
        for (Thread thread : threads) {
            thread.start();
            Thread.sleep(30);
        }
        for (Thread thread : threads) thread.join();
        log("SYSTEM", "END", "Final sharedData = " + sharedData);
        Path folder = Path.of("generated_html");
        Files.createDirectories(folder);
        Path output = folder.resolve("readers_writers_monitor.html");
        Files.writeString(output, createHtml());
        System.out.println("All readers and writers finished.");
        System.out.println("Final sharedData = " + sharedData);
        System.out.println("Generated: " + output);
    }
}
