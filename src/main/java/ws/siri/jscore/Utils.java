package ws.siri.jscore;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Utils {
    @SuppressWarnings("unchecked")
    public static <T, U> Optional<U> dangerouslyCastOptional(Optional<T> value) {
        return (Optional<U>) value;
    }

    public static class BinaryLatch {
        private boolean isLocked = true;
        private CountDownLatch lock = new CountDownLatch(1);
        private Lock countdownLatchAccesMutex = new ReentrantLock();

        public void countDown() {
            countdownLatchAccesMutex.lock();
            lock.countDown();
            isLocked = false;
            countdownLatchAccesMutex.unlock();
        }

        public boolean isLocked() {
            countdownLatchAccesMutex.lock();
            boolean out = isLocked;
            countdownLatchAccesMutex.unlock();
            return out;
        }

        public void await() throws InterruptedException {
            lock.await();
        }
    }

    /**
     * lock that blocks only when count is nonzero
     */
    public static class CounterLock {
        private int count = 0;
        private Optional<CountDownLatch> lock = Optional.empty();
        private Lock countdownLatchAccesMutex = new ReentrantLock();

        private void updateWithCount(int count) {
            countdownLatchAccesMutex.lock();
            if (count == 0) {
                lock.get().countDown();
                lock = Optional.empty();
            } else if (lock.isEmpty()) {
                lock = Optional.of(new CountDownLatch(1));
            }
            countdownLatchAccesMutex.unlock();
        }

        public void countUp() {
            countdownLatchAccesMutex.lock();
            updateWithCount(++count);
            countdownLatchAccesMutex.unlock();
        }

        public void countDown() {
            countdownLatchAccesMutex.lock();
            updateWithCount(--count);
            countdownLatchAccesMutex.unlock();
        }

        public void await() throws InterruptedException {
            countdownLatchAccesMutex.lock();
            Optional<CountDownLatch> lock = this.lock.map(l -> l);
            countdownLatchAccesMutex.unlock();
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
