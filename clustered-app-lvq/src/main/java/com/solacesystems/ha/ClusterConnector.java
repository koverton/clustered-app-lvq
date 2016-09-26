package com.solacesystems.ha;

import com.solacesystems.jcsmp.*;
import org.apache.log4j.Logger;

/**
 * <p>A ClusterConnector is the main actor providing Solace HA Clustering for applications that consume
 * input messages of type InputType from an application queue and produce output messages of type
 * OutputType which the ClusterConnector can retrieve from a Last Value Queue in the event of failures.
 * Both the InputType and OutputType are required to contain a global sequence number as represented by
 * the Ordered interface which they are required to implement. This is used to track each output with
 * respect to it's matching input for synchronization purposes.
 * </p>
 * <p>The ClusterConnector must be provided with a ClusterModel instance which tracks the state of the
 * particular cluster instance and provides event notifications for important clustering events, such as:
 * <ul>
 *     <li>The arrival of an input message</li>
 *     <li>Transition from BACKUP to ACTIVE state</li>
 *     <li>Current state of the consumer application with respect to an ordered stream of inputs</li>
 * </ul>
 * </p>
 * <p>The ClusterConnector must also be provided with a ClusteredAppSerializer instance with
 * implementations for serialization and deserialization of the InputType and OutputType objects.
 * For each input message, the ClusterConnector applies this Serializer to the inbound message
 * to convert it into an instance of the InputType. For each output object, the Serializer is applied
 * to produce a properly serialized output message for delivery over Solace.
 * </p>
 * <p>After establishing a connection, the owning application is expected to bind to an input queue on
 * which messages are consumed and translated into objects of InputType, and a Last Value Queue for
 * recovery purposes.</p>
 *
 * @param <InputType> input message type; must extend Ordered to ensure a sequence number is present
 * @param <OutputType> output message type; must also extend Ordered to ensure a sequence number is present
 */
public class ClusterConnector<InputType extends Ordered, OutputType extends Ordered> {
    final static Logger log = Logger.getLogger(ClusterConnector.class);

    /**
     * Constructor for a Solace connector in application-HA clustering mode.
     *
     * In this mode, the Input object type and Output object types are genericized as they
     * are specific to the particular application. The cluster connector handles clustering
     * events and defers serialization and state of these input/output objects to the
     * ClusterModel and ClusteredAppSerializer generic instances passed into the constructor.
     *
     * @param model simple application model for an instance in the cluster; tracks
     *              the HA state, sequence state and current input/output data. The
     *              cluster model can update interested observers whenever tracked state changes.
     * @param serializer ByteBufferSerializer implementation for the InputType and OutputType specified
     *              to the ClusterConnector.
     */
    public ClusterConnector(ClusterModel<InputType, OutputType> model,
                            ClusteredAppSerializer<InputType, OutputType> serializer) {
        _model = model;
        _serializer = serializer;
        _connector = new SolaceConnector();
    }

    /**
     * <p>Create a Solace connection to the specified Solace Message Router and Messaging-VPN, authenticating
     * as the specified user. The client-name is required to be a globally-unique session ID for tracking.</p>
     *
     * @param host IP or Host:port of the Solace Message Router to connect to
     * @param vpn Solace Message-VPN to connect to
     * @param user The username to authenticate as for the session
     * @param password The password to authenticate with
     * @param clientName Globally-unique connection name (most not collide with another application
     *                   connected to the same Solace Message Router).
     * @return True upon successful connection; false upon failed connection attempt.
     */
    public boolean Connect(String host, String vpn, String user, String password, String clientName) {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::Connect(host:%s, vpn:%s, user:%s, password:xxxxx)",
                host, vpn, user));
        boolean result = false;
        try {
            _connector.ConnectSession(host, vpn, user, password, clientName,
                    new SessionEventHandler() {
                        public void handleEvent(SessionEventArgs args) {
                            onSessionEvent(args);
                        }
                    });
            _model.SetSequenceStatus(SeqState.CONNECTED);
            result = true;
        }
        catch(JCSMPException ex) {
            log.error("Exception trying to connect to Solace", ex);
        }
        return result;
    }

    /**
     * <p>Binds to the named Application Queue to consume input messages and
     * to the named Last Value Queue to be used for last known state in the event of failure.</p>
     * @param appq Application queue to consume input messages from
     * @param lvq Last Value Queue that all outputs are routed to, and which is consumed from
     *            in event of failures to determine last processing state of the cluster
     * @throws JCSMPException
     */
    public void BindQueues(final String appq, final String lvq) throws JCSMPException {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::BindQueues(appq:%s, lvq:%s)", appq, lvq));
        // The order of instantiation matters; lvqflow is used for active-flow indication
        // which triggers recovering state via browser, then starts appflow after
        // recovery completes
        if (_lvqBrowser == null) _lvqBrowser = _connector.BrowseQueue(lvq);
        if (_appflow == null)
            _appflow = _connector.BindQueue(appq,
                new XMLMessageListener() {
                    public void onReceive(BytesXMLMessage msg) {
                        onAppMessage(msg);
                    }
                    public void onException(JCSMPException e) {
                        log.error("Exception trying to bind to application queue " + appq, e);
                        e.printStackTrace();
                    }
                },
                new FlowEventHandler() {
                    public void handleEvent(Object o, FlowEventArgs args) {
                        onAppFlowEvent(args);
                    }
                });
        if (_lvqflow == null)
            _lvqflow = _connector.BindQueue(lvq,
                new XMLMessageListener() {
                    public void onReceive(BytesXMLMessage msg) {
                        log.error("BAD BEHAVIOR!!! Should not consume LVQ ");
                    }
                    public void onException(JCSMPException e) {
                        log.error("Exception trying to bind to LV-queue " + lvq, e);
                        e.printStackTrace();
                    }
                },
                new FlowEventHandler() {
                    public void handleEvent(Object o, FlowEventArgs args) {
                        onLVQFlowEvent(args);
                    }
                });
    }

    /**
     * <p>Send an output text message.</p>
     * <p>HACK: just used to send JSON updates to a separate, standalone web-gui to display internal state</p>
     * @param topic The topic on which to send the output text message
     * @param payload Text payload string to send
     */
    public void SendText(String topic, String payload) {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::SendText(topic:%s, payload:%s)", topic, payload));
        try {
            _connector.SendText(topic, payload);
        }
        catch(JCSMPException ex) {
            log.error("Exception trying to send message on topic " + topic, ex);
            ex.printStackTrace();
        }
    }

    /**
     * Send an output based upon whatever the latest state change from input messages.
     *
     * @param topic The topic on which to send the output message
     * @param output The output object to be serialized and sent
     */
    public void SendOutput(String topic, OutputType output) {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::SendOutput(topic:%s, object)", topic));
        // If we're the active member of the cluster, we are responsible
        // for all output but don't publish until we have new input data
        if (_model.GetHAStatus() == HAState.ACTIVE && _model.GetSequenceStatus() == SeqState.UPTODATE)
        {
            try {
                _connector.SendOutput(topic, _serializer.SerializeOutput(output));
            }
            catch(JCSMPException ex) {
                ex.printStackTrace();
                log.error("Exception trying to send message on topic " + topic, ex);
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////
    //////////            Event Handlers                           /////////
    ////////////////////////////////////////////////////////////////////////

    /**
     * Invoked on the Solace session; this is used to indicate when the
     * connection is UP/Down or reconnecting
     *
     * @param args The Solace session connectivity event
     */
    private void onSessionEvent(SessionEventArgs args) {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::onSessionEvent(args:%s)", args));
        // None of these are needed for our eventing; we can depend on FLOW active/inactive events
        switch(args.getEvent()) {
            case RECONNECTED:
                break;
            case RECONNECTING:
                break;
            case DOWN_ERROR:
                // This event means the Solace API has given up trying to reconnect,
                // so best practice here is to log/alert and exit
                break;
        }
    }

    /**
     * Invoked on the application queue flow object when a flow event occurs
     *
     * @param args the flow object for the application queue
     */
    private void onAppFlowEvent(FlowEventArgs args) {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::onAppFlowEvent(args:%s)", args));
        switch (args.getEvent())
        {
            case FLOW_ACTIVE:
                synchronizeToLastOutput();
                break;
            case FLOW_INACTIVE:
                stopInputFlow();
                break;
            default:
                break;
        }
    }

    /**
     * Invoked on the appflow when an app queue message arrives
     *
     * @param msg new solace message from the application queue
     */
    private void onAppMessage(BytesXMLMessage msg) {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::onAppMessage(msg:%s)", msg));
        processInputMsg(_serializer.DeserializeInput(msg));
        msg.ackMessage();
    }

    /**
     * Invoked on the lvqflow when a flow event occurs; this is used
     * to indicate which instance in the cluster is Active
     *
     * @param args the flow event arguments for the LVQ
     */
    private void onLVQFlowEvent(FlowEventArgs args) {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::onLVQFlowEvent(args:%s)", args));
        switch (args.getEvent())
        {
            case FLOW_ACTIVE:
                becomeActive();
                break;
            case FLOW_INACTIVE:
                becomeBackup();
                break;
            default:
                break;
        }
    }


    ////////////////////////////////////////////////////////////////////////
    //////////          State Transitions                          /////////
    ////////////////////////////////////////////////////////////////////////

    /**
     * Invoked on the LVQBrowser when message is browsed
     *
     * @param lvqState a message from the LVQ read as port of the recovery process
     */
    private void processOutputMsg(OutputType lvqState) throws JCSMPException {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::processOutputMsg(args:%s)", lvqState));
        // Compare the lvq-message sequenceId to our current-state sequenceId
        OutputType curState = _model.GetLastOutput();
        String lvqstr = (lvqState==null) ? "(null)" : lvqState.toString();
        String appstr = (curState==null) ? "(null)" : curState.toString();
        if (log.isInfoEnabled())
            log.info(String.format("LAST OUTPUT ID: %s; CUR OUT ID: %s", lvqstr, appstr));

        if (lvqState != null && (curState == null ||  curState.getSequenceId() < lvqState.getSequenceId()))
        {
            _model.SetLastOutput(lvqState);
            _model.SetSequenceStatus(SeqState.RECOVERING);
        }
        else
        {
            _model.SetSequenceStatus(SeqState.UPTODATE);
        }
    }


    /**
     * Invoked on the appflow when an application message arrives. If
     * the current position in the application sequence is up to dote
     * with the last output, then this function calls the application
     * listener to give it a chance to update its current application
     * state and output something representing that state.
     *
     * @param input new applicadtion input message
     */
    private void processInputMsg(InputType input) {
        if (log.isDebugEnabled())
            log.debug(String.format("ClusterConnector::processInputMsg(args:{0})", input));
        OutputType appState = _model.GetLastOutput();
        if (appState == null || input.getSequenceId() >= appState.getSequenceId()) {
            if (_model.GetSequenceStatus() != SeqState.UPTODATE)
                _model.SetSequenceStatus(SeqState.UPTODATE);
            // Construct a new app state
            _model.UpdateApplicationState(input);
        }
        else {
            _model.SetLastInput(input);
            if (log.isInfoEnabled())
                log.info(
                    String.format(
                            "\tIGNORED MESSAGE %s because it is behind recovered state %d",
                            input.getSequenceId(),
                            appState.getSequenceId()));
        }
    }

    /**
     * Stops the application-queue flow and sets the sequence status to DISCONNECTED
     */
    private void stopInputFlow() {
        if (log.isDebugEnabled())
            log.debug("ClusterConnector::stopInputFlow()");
        _model.SetSequenceStatus(SeqState.DISCONNECTED);
        _appflow.stop();
    }

    /**
     * Invoked on the lvqflow when flow UP event occurs or when flow changes
     * from INACTIVE to ACTIVE This function tries to browse the message on
     * the LVQ to recover the last output state from this application
     */
    private void synchronizeToLastOutput() {
        if (log.isInfoEnabled())
            log.info(String.format("Recovering last state from the LVQ, current sequence state is {0}",
                _model.GetSequenceStatus()));
        _model.SetSequenceStatus(SeqState.RECOVERING);
        try {
            BytesXMLMessage lvqMsg = _lvqBrowser.getNext();
            if (lvqMsg != null)
                processOutputMsg(_serializer.DeserializeOutput(lvqMsg));
            _appflow.start();
        }
        catch(JCSMPException ex) {
            log.error("Exception trying to read last message from LVQ ", ex);
            ex.printStackTrace();
        }
    }

    /**
     * Invoked when the lvqflow flow ACTIVE event occurs indicates we are the Active member
     * responsible for all state outputs.
     */
    private void becomeActive()
    {
        if (log.isDebugEnabled())
            log.debug("ClusterConnector::becomeActive()");
        _model.SetHAStatus(HAState.ACTIVE);
    }

    /**
     * Invoked on the lvqflow when flow INACTIVE event occurs
     */
    private void becomeBackup()
    {
        if (log.isDebugEnabled())
            log.debug("ClusterConnector::becomeBackup()");
        _model.SetHAStatus(HAState.BACKUP);
    }

    private final SolaceConnector _connector;
    private final ClusterModel<InputType,OutputType> _model;
    private final ClusteredAppSerializer<InputType, OutputType> _serializer;

    private Browser _lvqBrowser;
    private FlowReceiver _appflow;
    private FlowReceiver _lvqflow;
}
