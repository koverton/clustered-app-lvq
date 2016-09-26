package com.solacesystems.ha;

import com.solacesystems.jcsmp.*;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;

public class SolaceConnector implements JCSMPStreamingPublishEventHandler {
    final static Logger log = Logger.getLogger(SolaceConnector.class);

    public SolaceConnector() {
        outMessage = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
        outMessage.setDeliveryMode(DeliveryMode.PERSISTENT);
    }

    public void ConnectSession(String host, String vpn, String user, String password, String clientName,
                               SessionEventHandler sessionEventHandler) throws JCSMPException {
        if (log.isDebugEnabled())
            log.debug(String.format("SolaceConnector::ConnectSession(host:%s, vpn:%s, user:%s, xxxxx, sessionHandler)",
                host, vpn, user));
        JCSMPProperties props = new JCSMPProperties();
        props.setProperty(JCSMPProperties.HOST, host);
        props.setProperty(JCSMPProperties.VPN_NAME, vpn);
        props.setProperty(JCSMPProperties.USERNAME, user);
        props.setProperty(JCSMPProperties.CLIENT_NAME, clientName);
        props.setProperty(JCSMPProperties.GENERATE_RCV_TIMESTAMPS, true);
        props.setProperty(JCSMPProperties.GENERATE_SEND_TIMESTAMPS, true);
        props.setProperty(JCSMPProperties.PASSWORD, password);
        props.setProperty(JCSMPProperties.MESSAGE_ACK_MODE, JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);
        JCSMPChannelProperties ccp = (JCSMPChannelProperties)props.getProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES);
        ccp.setKeepAliveLimit(3);
        ccp.setKeepAliveIntervalInMillis(1000);
        ccp.setReconnectRetries(40);
        ccp.setReconnectRetryWaitInMillis(3000);
        ccp.setConnectRetries(8);
        ccp.setConnectRetriesPerHost(5);

        session = JCSMPFactory.onlyInstance().createSession(props, null, sessionEventHandler);
        session.connect();

        producer = session.getMessageProducer(this);
    }

    public FlowReceiver BindQueue(String name, XMLMessageListener messageListener, FlowEventHandler flowEventHandler) throws JCSMPException {
        if (log.isDebugEnabled())
            log.debug(String.format("SolaceConnector::BindQueue(queue:%s, msgHandler, sessionHandler)", name));
        Queue queue = JCSMPFactory.onlyInstance().createQueue(name);
        ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
        flowProps.setEndpoint(queue);
        flowProps.setStartState(false);
        flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);
        flowProps.setActiveFlowIndication(true);
        return session.createFlow(messageListener, flowProps, null, flowEventHandler);
    }

    public Browser BrowseQueue(String queue) throws JCSMPException {
        if (log.isDebugEnabled())
            log.debug(String.format("SolaceConnector::BrowseQueue(queue:%s)", queue));
        BrowserProperties props = new BrowserProperties();
        props.setEndpoint(JCSMPFactory.onlyInstance().createQueue(queue));
        props.setTransportWindowSize(1);
        props.setWaitTimeout(100);
        return session.createBrowser(props);
    }

    public void SendOutput(String topic, ByteBuffer payload) throws JCSMPException {
        if (log.isDebugEnabled())
            log.debug(String.format("SolaceConnector::SendOutput(topic:%s)", topic));
        BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
        msg.setDeliveryMode(DeliveryMode.PERSISTENT);
        msg.writeAttachment(payload.array());
        producer.send(msg, JCSMPFactory.onlyInstance().createTopic(topic));
    }

    public void SendText(String topic, String payload) throws JCSMPException {
        if (log.isDebugEnabled())
            log.debug(String.format("SolaceConnector::SendText(topic:%s, payload:%s)", topic, payload));
        BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
        msg.setDeliveryMode(DeliveryMode.PERSISTENT);
        msg.writeAttachment(payload.getBytes());
        producer.send(msg, JCSMPFactory.onlyInstance().createTopic(topic));
    }

    /** JCSMPStreamingPublishEventHandler **/

    public void handleError(String messageID, JCSMPException e, long timestamp) {
        log.error("Error reading a response for published message-ID: " + messageID, e);
    }

    public void responseReceived(String messageID) {
        // TBD: Streaming Publisher handling (if necessary)
        if (log.isDebugEnabled())
            log.debug("Streaming publisher event message-ID: " + messageID);
    }


    private JCSMPSession session;
    private BytesXMLMessage outMessage;
    private XMLMessageProducer producer;
}
