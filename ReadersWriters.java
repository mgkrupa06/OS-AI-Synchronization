import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class ReadersWriters {
    static final int READERS = 3, WRITERS = 2, OPERATIONS = 2;
    static final Semaphore resource = new Semaphore(1, true);
    static final Semaphore readerMutex = new Semaphore(1, true);
    static int activeReaders = 0;
    static int sharedData = 0;
    static final EventLog log = new EventLog();

    static void delay() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(80, 220));
    }

    static void reader(int id) {
        String actor = "R" + id;
        try {
            for (int op = 1; op <= OPERATIONS; op++) {
                log.add(actor, "WANTS_TO_READ", "Requests read access, operation " + op);
                readerMutex.acquire();
                try {
                    synchronized (ReadersWriters.class) {
                        activeReaders++;
                        log.add(actor, "READER_COUNT", "Active readers = " + activeReaders);
                    }
                    if (activeReaders == 1) {
                        log.add(actor, "WAITING_FOR_RESOURCE", "First reader requests resource semaphore");
                        resource.acquire();
                        log.add(actor, "READ_LOCKED", "First reader acquired resource for readers");
                    }
                } finally { readerMutex.release(); }

                try {
                    int value;
                    synchronized (ReadersWriters.class) {
                        value = sharedData;
                        log.add(actor, "READING", "Reading sharedData = " + value);
                    }
                    delay();
                    log.add(actor, "READ_COMPLETE", "Finished reading value " + value);
                } finally {
                    readerMutex.acquire();
                    try {
                        synchronized (ReadersWriters.class) {
                            activeReaders--;
                            log.add(actor, "READER_COUNT", "Active readers = " + activeReaders);
                            if (activeReaders == 0) {
                                log.add(actor, "RELEASING_RESOURCE", "Last reader releases resource semaphore");
                                resource.release();
                            }
                        }
                    } finally { readerMutex.release(); }
                }
                delay();
            }
            log.add(actor, "FINISHED", "Completed all read operations");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.add(actor, "INTERRUPTED", "Reader interrupted");
        }
    }

    static void writer(int id) {
        String actor = "W" + id;
        try {
            for (int op = 1; op <= OPERATIONS; op++) {
                log.add(actor, "WANTS_TO_WRITE", "Requests exclusive access, operation " + op);
                resource.acquire();
                try {
                    log.add(actor, "WRITING", "Entered exclusive writing section");
                    synchronized (ReadersWriters.class) {
                        sharedData++;
                        log.add(actor, "DATA_UPDATED", "sharedData updated to " + sharedData);
                    }
                    delay();
                    log.add(actor, "WRITE_COMPLETE", "Finished writing");
                } finally {
                    resource.release();
                    log.add(actor, "RELEASED_RESOURCE", "Released resource semaphore");
                }
                delay();
            }
            log.add(actor, "FINISHED", "Completed all write operations");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.add(actor, "INTERRUPTED", "Writer interrupted");
        }
    }

    public static void main(String[] args) throws Exception {
        log.add("SYSTEM", "START", "Readers-Writers semaphore + synchronized solution started");
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < READERS; i++) {
            final int id = i;
            threads.add(new Thread(() -> reader(id), "Reader-" + id));
        }
        for (int i = 0; i < WRITERS; i++) {
            final int id = i;
            threads.add(new Thread(() -> writer(id), "Writer-" + id));
        }
        for (Thread t : threads) { t.start(); Thread.sleep(20); }
        for (Thread t : threads) t.join();
        log.add("SYSTEM", "END", "All threads finished; final sharedData = " + sharedData);

        Path out = Path.of("generated_html", "readers_writers_semaphores_synchronized.html");
        Files.createDirectories(out.getParent());
        Files.writeString(out, log.html(), StandardCharsets.UTF_8);
        System.out.println("All readers and writers have finished.");
        System.out.println("Final sharedData = " + sharedData + " (expected 4)");
        System.out.println("Recorded events = " + log.events.size());
        System.out.println("Generated: " + out);
    }

    static class Event {
        long no, ms; String actor, state, detail;
        Event(long no, long ms, String actor, String state, String detail) {
            this.no=no; this.ms=ms; this.actor=actor; this.state=state; this.detail=detail;
        }
    }
    static class EventLog {
        final long start = System.nanoTime();
        final AtomicLong seq = new AtomicLong();
        final List<Event> events = new ArrayList<>();
        synchronized void add(String actor, String state, String detail) {
            events.add(new Event(seq.incrementAndGet(), (System.nanoTime()-start)/1_000_000, actor, state, detail));
        }
        synchronized String html() {
            StringBuilder rows = new StringBuilder(), data = new StringBuilder("[");
            for (int i=0; i<events.size(); i++) {
                Event e=events.get(i);
                rows.append("<tr data-i='").append(i).append("'><td>").append(e.no).append("</td><td>")
                    .append(e.ms).append("</td><td>").append(esc(e.actor)).append("</td><td>")
                    .append(esc(e.state)).append("</td><td>").append(esc(e.detail)).append("</td></tr>");
                if (i>0) data.append(",");
                data.append("{n:").append(e.no).append(",ms:").append(e.ms).append(",actor:'")
                    .append(js(e.actor)).append("',state:'").append(js(e.state)).append("',detail:'")
                    .append(js(e.detail)).append("'}");
            }
            data.append("]");
            return """
            <!doctype html><html><head><meta charset="utf-8"><title>Readers-Writers simulation</title>
            <style>body{font-family:Arial;margin:24px;background:#f5f7fa;color:#222}table{border-collapse:collapse;width:100%;background:white}
            td,th{padding:8px;border-bottom:1px solid #ddd;text-align:left}th{background:#e8edf4}tr.active{background:#fff0bd}
            .panel{padding:16px;background:white;margin:16px 0;border:1px solid #ddd;border-radius:8px}
            button{padding:7px 14px;margin-right:5px}#slider{width:min(600px,95%)}.wrap{overflow:auto;max-height:65vh}</style></head><body>
            <h1>Readers-Writers — Semaphores + Synchronization</h1>
            <p>HTML generated from events recorded during this actual concurrent run, not a pre-scripted trace.</p>
            <div class="panel"><b>Readers:</b> 3 &nbsp; <b>Writers:</b> 2 &nbsp; <b>Operations per thread:</b> 2
            &nbsp; <b>Recorded events:</b> COUNT &nbsp; <b>Final sharedData:</b> FINAL</div>
            <div class="panel"><h2>Event timeline</h2><button id="play">Play</button><button id="pause">Pause</button>
            <button id="reset">Reset</button> <span id="pos">0 / COUNT</span><p><input id="slider" type="range" min="0" max="MAX" value="0"></p>
            <h3 id="headline">Ready</h3><p id="detail"></p><div id="actors"></div></div>
            <div class="panel"><h2>Actual execution log</h2><div class="wrap"><table><thead><tr><th>#</th><th>Elapsed ms</th>
            <th>Actor</th><th>State</th><th>Event detail</th></tr></thead><tbody>ROWS</tbody></table></div></div>
            <script>const ev=DATA;let i=0,t=null;const s=document.getElementById('slider');
            function draw(){document.querySelectorAll('tbody tr').forEach((r,j)=>r.classList.toggle('active',j===i-1));
            document.getElementById('pos').textContent=i+' / '+ev.length;s.value=i;
            let e=ev[i-1];document.getElementById('headline').textContent=e?'#'+e.n+' | '+e.actor+' | '+e.state:'Ready';
            document.getElementById('detail').textContent=e?e.ms+' ms — '+e.detail:'';
            let a={};ev.slice(0,i).forEach(x=>{if(x.actor!=='SYSTEM')a[x.actor]=x.state+' — '+x.detail;});
            document.getElementById('actors').innerHTML=Object.entries(a).map(([k,v])=>'<p><b>'+k+'</b>: '+v+'</p>').join('');}
            function stop(){if(t){clearInterval(t);t=null;}}document.getElementById('play').onclick=()=>{stop();t=setInterval(()=>{if(i>=ev.length){stop();return;}i++;draw();},450)};
            document.getElementById('pause').onclick=stop;document.getElementById('reset').onclick=()=>{stop();i=0;draw()};
            s.oninput=()=>{stop();i=+s.value;draw()};draw();</script></body></html>
            """.replace("COUNT", ""+events.size()).replace("MAX", ""+events.size())
               .replace("FINAL", ""+sharedData).replace("ROWS", rows.toString()).replace("DATA", data.toString());
        }
        static String esc(String x) { return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("'","&#39;"); }
        static String js(String x) { return x.replace("\\","\\\\").replace("'","\\'").replace("\n"," "); }
    }
}
