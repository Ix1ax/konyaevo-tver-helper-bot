package dev.ix1ax.main.bot;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatUpdateDispatcherTest {
    @Test void retainsOrderUnderBurstAndDoesNotBlockUnrelatedChats() throws Exception {
        try (var dispatcher=new ChatUpdateDispatcher(8)) {
            CountDownLatch waiting=new CountDownLatch(1), entered=new CountDownLatch(1), other=new CountDownLatch(1);
            dispatcher.dispatch(42,()-> { entered.countDown(); try { waiting.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
            assertTrue(entered.await(2,TimeUnit.SECONDS)); dispatcher.dispatch(43,other::countDown);
            try { assertTrue(other.await(2,TimeUnit.SECONDS)); } finally { waiting.countDown(); }
            Map<Long,List<Integer>> seen=new ConcurrentHashMap<>(); CountDownLatch done=new CountDownLatch(10000);
            for (int i=0; i<10000; i++) {
                long chat=i%100; int sequence=i;
                dispatcher.dispatch(chat,()-> { seen.computeIfAbsent(chat,key->new ArrayList<>()).add(sequence); done.countDown(); });
            }
            assertTrue(done.await(10,TimeUnit.SECONDS)); assertEquals(100,seen.size());
            for (var events:seen.values()) {
                assertEquals(100,events.size());
                for (int i=1; i<events.size(); i++) assertTrue(events.get(i)>events.get(i-1));
            }
        }
    }
}
