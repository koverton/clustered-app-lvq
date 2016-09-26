package com.solacesystems.ha;

import com.solacesystems.ha.SolaceConnector;
import com.solacesystems.jcsmp.*;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;

public class SolaceConnectorTest {
    private String queueName     = "sample_queue";
    private String topicName     = "unit/test/topic";
    private String payloadString = "hello world";

    @Test
    @Ignore
    public void testConnection() throws Exception {
        ByteBuffer payload = ByteBuffer.allocate(1024);
        payload.put(payloadString.getBytes());

        SolaceConnector conn = new SolaceConnector();
        conn.ConnectSession("192.168.56.102", "poc_vpn", "test", "test", "foo",
                new SessionEventHandler() {
                    public void handleEvent(SessionEventArgs sessionEventArgs) {
                        System.out.println("Session: " + sessionEventArgs);
                    }
                });
        conn.SendOutput(topicName, payload);
        Browser browser = conn.BrowseQueue(queueName);
        Thread.sleep(100);
        BytesXMLMessage roMsg = browser.getNext();
        if (roMsg != null) {
            System.out.println("Browse Msg: " + roMsg);
        }
        FlowReceiver con = conn.BindQueue(queueName,
                new XMLMessageListener() {
                    public void onReceive(BytesXMLMessage flowMsg) {
                        System.out.println("Msg: " + flowMsg);
                        flowMsg.ackMessage();
                    }
                    public void onException(JCSMPException e) {
                        e.printStackTrace();
                    }
                },
                new FlowEventHandler() {
                    public void handleEvent(Object o, FlowEventArgs flowEventArgs) {
                        System.out.println("Flow: " + flowEventArgs);
                    }
                });
        con.start();
        try { Thread.sleep(1000); } catch(InterruptedException e) {}
    }
}
