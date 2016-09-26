/**
 * <p><code>com.solacesystems.ha</code> provides a sample implementation of application HA clustering
 * for the Solace Unified Messaging Platform via the following classes:<br>
 * <ul>
 *     <li>{@link com.solacesystems.ha.ClusterConnector}: Responsible for connecting to the
 *     Solace message-bus, binding to persistent queues, and handling state for Sequence and
 *     Active Flow for the queues.<br></li>
 *     <li>{@link com.solacesystems.ha.ClusterModel}: Stores all the state for managing application
 *     availability and failover, and updates the application for each state change, deferring to the
 *     application for its internal state transitions.<br></li>
 *     <li>{@link com.solacesystems.ha.ClusterEventListener}: Interface to be implemented by the
 *     application; the model uses this interface to notify the application for all sate changes.</li>
 * </ul>
 * </p>
 * <p>The application is expected to read a
 * stream of input events of one particular data type. For each of these inputs, it is
 * expected to produce a matching output event representing the new state based on that
 * input event. Several instances of this application act as a cluster, backing up
 * whichever instance member is elected as the {@link com.solacesystems.ha.HAState#ACTIVE ACTIVE}
 * instance by the Solace platform.
 * </p>
 * <p>It is a Hot/Hot architecture where each member
 * consumes the same stream of messages, updating internal state according to the same
 * logic to keep in sync. In the event that the {@link com.solacesystems.ha.HAState#ACTIVE ACTIVE}
 * instance fails, Solace immediately
 * chooses a backup instance to takeover sending a notification to that instance.
 * </p>
 * <p>The input and output events must have a sequence number which can be used to map each
 * input event to it's corresponding output event. This is used in recovery to determine
 * whether the backup instance's state is up-to-date with the input stream.
 * </p>
 * <p>The important classes from this framework are genericized to account for that,
 * with generic type parameters for each data type:
 * <ul>
 *   <li><code>InputType</code>: input message type; must extend {@link com.solacesystems.ha.Ordered}
 *   to ensure a sequence number is present</li>
 *   <li><code>OutputType</code>: output message type; must also extend {@link com.solacesystems.ha.Ordered}
 *   to ensure a sequence number is present</li>
 * </ul>
 * </p>
 * <p>
 * An application built on this clustering framework would provision the following on a
 * Solace Messaging Appliance:
 * <ul>
 *     <li>An input queue to persist inbound messages to the application instance;
 *     these messages are expected to all be of the same message type</li>
 *     <li>An output Last Value Queue that will be mapped to a subscription of all output
 *     from the entire cluster and is also expected to always contain objects of the same
 *     message type</li>
 * </ul>
 * This implementation provides a generic type signature for application-specific
 * <code>InputType</code> and <code>OutputType</code> objects.
 * These objects must extend Ordered to ensure a sequence number is present.
 * </p>
 * <p>The clustering behavior is provided by the {@link com.solacesystems.ha.ClusterConnector} class. It has a
 * {@link com.solacesystems.ha.ClusterModel} instance in which it tracks clustering state.</p>
 *
 * <p>The {@link com.solacesystems.ha.ClusterModel} tracks the HA state model and sequence status and provides event
 * notifications for state changes via the {@link com.solacesystems.ha.ClusterEventListener} interface.
 * The {@link com.solacesystems.ha.ClusterEventListener} instance is passed to the
 * {@link com.solacesystems.ha.ClusterModel} at construction time, so there is only ever one listener.</p>
 *
 * <p>In implementation of the {@link com.solacesystems.ha.ClusteredAppSerializer} must be provided to marshal the
 * application <code>InputType</code> and <code>OutputType</code> data types.</p>
 *
 * <p>An application built on this library implements the {@link com.solacesystems.ha.ClusterEventListener} interface
 * and subscribes to events on an instance of a {@link com.solacesystems.ha.ClusterModel}.
 * Instances of the {@link com.solacesystems.ha.ClusterModel}
 * and {@link com.solacesystems.ha.ClusteredAppSerializer} are passed into the constructor of the
 * {@link com.solacesystems.ha.ClusterConnector}
 * which is used to connect to the Solace Message Bus, bind to the input/output queues,
 * pass input messages and important state changes to the application via the
 * {@link com.solacesystems.ha.ClusterEventListener} interface.</p>
 *
 * <p>Here is a sample application:
<pre>
<code>
package com.solacesystems.demo;
import com.solacesystems.ha.*;

public class SampleApp implements {@link com.solacesystems.ha.ClusterEventListener}<OrderUpdate, OrderStatus> {
    private final {@link com.solacesystems.ha.ClusterModel}<OrderUpdate,OrderStatus> _model;
    private final {@link com.solacesystems.ha.ClusterConnector}<OrderUpdate,OrderStatus> _connector;
    private final String _outputTopic;

    public SampleApp(String outputTopic) {
        _outputTopic = outputTopic;
        _model = new {@link com.solacesystems.ha.ClusterModel#ClusterModel ClusterModel}<OrderUpdate, OrderStatus>(this);
        _connector = new {@link com.solacesystems.ha.ClusterConnector#ClusterConnector ClusterConnector}<OrderUpdate, OrderStatus>(_model, new SampleSerializer());
    }

    public void Run(String host, String vpn, String user, String pass, String queue, String lvq) {
        if (_connector.{@link com.solacesystems.ha.ClusterConnector#Connect Connect}(host, vpn, user, pass, "sample_inst1")) {
            try {
                _connector.{@link com.solacesystems.ha.ClusterConnector#BindQueues BindQueues}(queue, lvq);
                while (true) {
                    Thread.sleep(1000);
                }
            } catch (JCSMPException ex) {
                ex.printStackTrace();
            }
        }
    }

    // Called when a new input message arrives; this function
    // calculates the new state based upon this input and returns
    // it back to the ClusterModel while also outputting the
    // new state to consumers.
    public OrderStatus {@link com.solacesystems.ha.ClusterEventListener#UpdateApplicationState UpdateApplicationState}(OrderUpdate input) {
        // IMPORTANT: State change while we're up-to-date, so every
        // input represents real state changes we need to represent
        OrderStatus output = updateOrder(input);
        output.setSequenceId(input.getSequenceId());
        // Send the result out to the rest of the world; if this instance is
        // not ACTIVE, the connector will not actually send output to the msgbus
        _connector.SendOutput(_outputTopic, output);
        return output;
    }

    // Called when state changes from {@link com.solacesystems.ha.HAState#BACKUP BACKUP} to {@link com.solacesystems.ha.HAState#ACTIVE ACTIVE} or vice versa
    public void {@link com.solacesystems.ha.ClusterEventListener#OnHAStateChange OnHAStateChange}({@link com.solacesystems.ha.HAState} oldState, {@link com.solacesystems.ha.HAState} newState) {}

    // Called when state changes from {@link com.solacesystems.ha.SeqState#RECOVERING RECOVERING} to {@link com.solacesystems.ha.SeqState#UPTODATE UPTODATE}
    public void {@link com.solacesystems.ha.ClusterEventListener#OnSeqStateChange OnSeqStateChange}({@link com.solacesystems.ha.SeqState} oldState, {@link com.solacesystems.ha.SeqState} newState) {}

    // Called when the last output was read from the LVQ in recovery mode
    public void {@link com.solacesystems.ha.ClusterEventListener#OnInitialStateMessage OnInitialStateMessage}(OrderStatus initialState) {}

    // Called when a new application message is stored in the ClusterModel
    public void {@link com.solacesystems.ha.ClusterEventListener#OnApplicationMessage OnApplicationMessage}(OrderUpdate input) {}
 }
 </code>
 </pre>
 * </p>
 */
package com.solacesystems.ha;