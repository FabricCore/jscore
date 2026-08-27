package ws.siri.jscore;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

public class Utils {
    /**
     * lock that blocks only when count is nonzero
     */
    public static class CounterLock {
        private int count;
        private Optional<CountDownLatch> lock = Optional.empty();

        private boolean throwOnNegative;

        public CounterLock(int count, boolean throwOnNegative) {
            this.count = count;
            this.throwOnNegative = throwOnNegative;
            if (count != 0)
                updateWithCount(count);
        }

        private synchronized void updateWithCount(int count) {
            if (count < 0 && throwOnNegative)
                throw new UnsupportedOperationException("count is negative");

            if (count == 0) {
                if (lock.isPresent()) {
                    lock.get().countDown();
                    lock = Optional.empty();
                }
            } else if (lock.isEmpty()) {
                lock = Optional.of(new CountDownLatch(1));
            }
        }

        public synchronized void countUp() {
            updateWithCount(++count);
        }

        public synchronized void countDown() {
            updateWithCount(--count);
        }

        private synchronized Optional<CountDownLatch> getLock() {
            return this.lock.map(l -> l);
        }

        public void await() throws InterruptedException {
            Optional<CountDownLatch> lock = getLock();
            if (lock.isPresent())
                lock.get().await();
        }
    }

    public static void waitFor(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("shouldn't be interrupted", e);
        }
    }

    public static void waitFor(CounterLock lock) {
        try {
            lock.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("shouldn't be interrupted", e);
        }
    }
}
