package com.solacesystems.demo;


import com.solacesystems.ha.SolaceConnector;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;

import java.nio.ByteBuffer;
import java.util.Random;

public class MockOrderGateway {
    public static void main(String[] args)
    {

        if (args.length < 6)
        {
            System.out.println("USAGE: SamplePublisher <HOST> <VPN> <USER> <PASS> <PUB-TOPIC> <STARTID>");
            return;
        }
        new MockOrderGateway(args[0], args[1], args[2], args[3], args[4], args[5])
                .run();
    }

    private MockOrderGateway(String host, String vpn, String username, String password, String topic, String startId) {
        _startOrderId = Integer.parseInt(startId);
        _outTopic = topic;
        _connector = new SolaceConnector();
        try {
            _connector.ConnectSession(host, vpn, username, password, "OGW",
                    new SessionEventHandler() {
                        public void handleEvent(SessionEventArgs event) {
                            System.out.println("Session event: " + event);
                        }
                    });
        }
        catch(JCSMPException ex) {
            ex.printStackTrace();
        }

    }

    private void run()
    {
        boolean running = true;
        int orderId = _startOrderId;
        while (running)
        {
            try {
                Thread.sleep(100);
            }
            catch(InterruptedException e) {
                e.printStackTrace();
                running = false;
            }
            sendNextOrder(orderId++);
        }
    }

    private ClientOrder nextOrder(int oid) {
        ClientOrder order = new ClientOrder(oid);
        order.setIsBuy(_rand.nextBoolean());
        order.setQuantity(_rand.nextDouble() % 1000);
        order.setPrice(_rand.nextDouble() * 50);
        order.setInstrument("MSFT");
        return order;
    }
    private void sendNextOrder(int oid)
    {
        ClientOrder order = nextOrder(oid);
        System.out.println("Sending msg: " + order);
        try {
            _connector.SendOutput(_outTopic, _serializer.SerializeInput(order));
        }
        catch(JCSMPException ex) {
            ex.printStackTrace();
        }
    }

    private final Random _rand = new Random();
    private final int _startOrderId;
    private final String _outTopic;
    private final ByteBuffer _outbuf = ByteBuffer.allocate(ClientOrder.SERIALIZED_SIZE);
    private final SolaceConnector _connector;
    private final SampleSerializer _serializer = new SampleSerializer();
}
