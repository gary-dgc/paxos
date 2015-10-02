package paxos.fragmentation;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import paxos.Group;
import paxos.Member;
import paxos.Receiver;
import paxos.UDPMessenger;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static paxos.TestUtils.*;

public class FragmentingGroupIntegrationTest {
    private final Set<FragmentingGroup> groups = new HashSet<FragmentingGroup>();
    private static final byte[] MESSAGE_TO_SEND = createMessageOfLength(64000*3+100);
//    private static final byte[] MESSAGE_TO_SEND = createMessageOfLength(100000);
    private static final int MESSAGES_TO_SEND = 10;
    private static final int GROUP_SIZE = 10;

    @After
    public void tearDown() {
        for (FragmentingGroup group : groups) {
            group.close();
        }
    }

    @Test
    public void testTransmittingLongMessages() throws Exception {
        List<Member> members = createMembersOnLocalhost(GROUP_SIZE);
        Receiver[] receivers = createReceivers(GROUP_SIZE);
        FragmentingGroup[] endpoints = createEndpoints(members, receivers);

        long start = System.currentTimeMillis();

        Thread[] senders = createSenders(endpoints);
        waitTillFinished(senders);

        double delta = (System.currentTimeMillis() - start) / 1000.0; // in seconds
        System.out.println("time taken: " + delta);
        System.out.println("req/s: " + (MESSAGES_TO_SEND * GROUP_SIZE / delta));

        for (Receiver receiver : receivers) {
            CountingReceiver countingReceiver = (CountingReceiver) receiver;
            Assert.assertEquals(MESSAGES_TO_SEND * GROUP_SIZE, countingReceiver.msgCount);
        }
    }


    private FragmentingGroup[] createEndpoints(List<Member> members, Receiver[] receivers) throws IOException, InterruptedException {
        final FragmentingGroup[] endpoints = new FragmentingGroup[members.size()];
        for (int i = 0; i < members.size(); i++) {
            endpoints[i] = new FragmentingGroup(new UDPMessenger(members, members.get(i).getPort()), receivers[i]);
            groups.add(endpoints[i]);
        }
        Thread.sleep(500); // allow some time for leader election
        return endpoints;
    }

    public static class CountingReceiver implements Receiver {
        public long msgCount = 0l;
        public synchronized void receive(Serializable message) {
            msgCount++;
        }
    }

    private Thread[] createSenders(FragmentingGroup[] endpoints) {
        Thread[] senders = new Thread[endpoints.length];
        for (int i = 0; i < endpoints.length; i++) {
            senders[i] = new Sender(i, endpoints[i]);
            senders[i].start();
        }
        return senders;
    }

    private static class Sender extends Thread {

        private final int i;
        private final FragmentingGroup endpoint;
        public Sender(int i, FragmentingGroup endpoint) {
            this.i = i;
            this.endpoint = endpoint;
        }

        @Override
        public void run() {
            for (int j = 0; j < MESSAGES_TO_SEND; j++) {
                try {

                    endpoint.broadcast(MESSAGE_TO_SEND);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }

    }

    private void findDuplicates(List<String> messages) {
        Set<String> messagesFound = new HashSet<String>();
        if (messages.size() != MESSAGES_TO_SEND * GROUP_SIZE) {
            // find duplicate
            for (String msg : messages) {
                if (messagesFound.contains(msg)) {
                    System.out.println("duplicate message " + msg);
                }
                messagesFound.add(msg);
            }
        }
    }

    private Receiver[] createReceivers(int groupSize) {
        Receiver[] receivers = new Receiver[groupSize];
        for (int i = 0; i < groupSize; i++) receivers[i] = new CountingReceiver();
        return receivers;
    }

    static private byte[] createMessageOfLength(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (i%256);
        }
        return bytes;
    }
}