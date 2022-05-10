package eskimo220.akidog.easywall;

import net.bytebuddy.utility.RandomString;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class IdGen {

    private IdGen() {
    }

    private static final AtomicLong LAST_TIME_MS = new AtomicLong();

    /**
     * 生成id
     *
     * @return
     */
    public static String nextId() {
        long now = System.currentTimeMillis();
        while (true) {
            long lastTime = LAST_TIME_MS.get();
            if (lastTime >= now) now = lastTime + 1;
            if (LAST_TIME_MS.compareAndSet(lastTime, now)) {
                SimpleDateFormat f = new SimpleDateFormat("mmssSSS");
                return f.format(new Date(now));
            }
        }
    }

    public static String nextId2() {
        return new RandomString(3).nextString().toLowerCase();
    }
}
