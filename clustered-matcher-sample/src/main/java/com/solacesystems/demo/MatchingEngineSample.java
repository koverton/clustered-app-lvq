package com.solacesystems.demo;

import com.solacesystems.ha.*;
import com.solacesystems.jcsmp.JCSMPException;
import org.apache.log4j.Logger;

public class MatchingEngineSample implements ClusterEventListener<ClientOrder, AppState> {
    final static Logger log = Logger.getLogger(MatchingEngineSample.class);
    public static void main(String[] args) {
        if (args.length < 9) {
            System.out.println("USAGE: <IP> <APP-ID> <APP-INST-#> <SOL-VPN> <SOL-USER> <SOL-PASS> <QUEUE> <LVQ> <OUT-TOPIC>\n\n\n");
            return;
        }
        String host  = args[0];
        String appId = args[1];
        int instance = Integer.parseInt(args[2]);
        String vpn   = args[3];
        String user  = args[4];
        String pass  = args[5];
        String queue = args[6];
        String lvq   = args[7];
        String topic = args[8];

        new MatchingEngineSample(appId, instance, topic)
                .Run(host, vpn, user, pass, queue, lvq);
    }

    public MatchingEngineSample(String appId, int instance, String outTopic) {
        _appId = appId;
        _instance = instance;
        _outTopic = outTopic;

        _model = new ClusterModel<ClientOrder, AppState>(this);
        _connector = new ClusterConnector<ClientOrder, AppState>(_model, new SampleSerializer());
    }

    public void Run(String host, String vpn, String user, String pass, String queue, String lvq) {
        if (log.isDebugEnabled())
            log.debug(String.format("SampleCusteredApp::Run(host:%s, vpn:%s, user:%s, pass:xxx, queue:%s, lvq:%s)",
                    host, vpn, user, queue, lvq));
        if (_connector.Connect(host, vpn, user, pass, _appId + "_inst" + _instance)) {
            try {
                _connector.BindQueues(queue, lvq);

                boolean running = true;
                while (running) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        running = false;
                    }
                }
            } catch (JCSMPException ex) {
                System.out.println("Failed to bind to queue {" + queue + "} and LVQ {" + lvq + "}");
                ex.printStackTrace();
            }
        }
        else {
            System.out.println("Failed to connect; exiting.\n");
        }
    }

    /***                               **
     ** ClusterEventListener interface **
     **                                ***/

    public AppState UpdateApplicationState(ClientOrder input) {
        if (log.isDebugEnabled())
            log.debug(String.format("MatchingEngineSample::UpdateApplicationState(%s)", input));
        // IMPORTANT: State change while we're up-to-date, so every input
        // represents real state changes we need to represent
        AppState output = new AppState(input.getInstrument());
        output.setSequenceId(input.getSequenceId());
        // Notify
        if (log.isInfoEnabled())
            log.info(String.format(
                    "%s:%d STATE:  HA = [%s] SEQ = [%s] IN = [%s] OUT = [%s]",
                    _appId, _instance,
                    _model.GetHAStatus(),
                    _model.GetSequenceStatus(),
                    (input==null ? "(null)" : input.getSequenceId()),
                    (input==null ? "(null)" : output.getSequenceId())));
        // I always send, let the connector worry about if I'm active or not
        _connector.SendOutput(_outTopic, output);
        sendMonitorUpdate(); // HACK!
        return output;
    }

    public void OnHAStateChange(HAState oldState, HAState newState) {
        if (log.isInfoEnabled())
            log.info(String.format("HA Change: %s => %s", oldState, newState));
        sendMonitorUpdate(); // HACK!
    }

    public void OnSeqStateChange(SeqState oldState, SeqState newState) {
        if (log.isInfoEnabled())
            log.info(String.format("Sequence Change: %s => %s", oldState, newState));
        sendMonitorUpdate(); // HACK!
    }

    public void OnInitialStateMessage(AppState initialState) {
        if (log.isInfoEnabled())
            log.info(String.format("Initializing to state: %s", initialState));
        sendMonitorUpdate(); // HACK!
    }

    public void OnApplicationMessage(ClientOrder input) {
        // Meh. Who cares; these could be just replaying from before our current state
        sendMonitorUpdate(); // HACK!
    }

    /***                 **
     ** Helper functions **
     **                  ***/

    /// HACK: this is just here for the extra message published to the web-monitor
    private void sendMonitorUpdate() {
        if (_connector != null) {
            _connector.SendText("monitor/state", toJSONString());
        }
    }
    private int orderedSeqId(Ordered o) {
        if (o == null) return -1;
        return o.getSequenceId();
    }

    private String toJSONString() {
        Ordered output = _model.GetLastOutput();
        Ordered input  = _model.GetLastInput();
        return String.format(
                "{ \"Instance\":%d, \"HAState\":\"%s\", \"SeqState\":\"%s\", \"LastInput\":%d, \"LastOutput\":%d }",
                _instance, _model.GetHAStatus(), _model.GetSequenceStatus(), orderedSeqId(input), orderedSeqId(output)
        );
    }

    private final ClusterModel<ClientOrder,AppState> _model;
    private final ClusterConnector<ClientOrder,AppState> _connector;

    private final String _appId;
    private final int    _instance;
    private final String _outTopic;
}
