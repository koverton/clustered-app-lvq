This is a demonstration of Application HA using the Solace Unified Messaging Platform 
built on these core Solace features:
1. Guaranteed Delivery
2. Persistent Messaging Fanout
3. Exclusive Queues
4. Last Value Queues
5. Queue Browsing

for more details about this architecture and how it works, see the Solace blog (TBD).

In this demo, each instance in the cluster will consume the same sequence of messages 
from a dedicated input queue, while the cluster Leader alone will output results from
the cluster. The outputs will be mapped to a Last Value Queue for the entire cluster 
and be used for recovery/resynchronization by instances at startup time.

1. SETTING UP THE APPLIANCE

One Exclusive Queue per instance must be created with the same topic-subscription for 
inputs applied to each Queue.

One Last Value Queue for the entire cluster must be created with a topic-subscription 
matching all output messages from the cluster.

A Solace CLI script is provided to provision an example message-VPN with the following 
resources for a demo:
  message-VPN:  ha_demo, no auth, msgspool 
  Input Queues: app1_inst1, app1_inst2, app2_inst3 
                subscribed to order/app1/>
  LV Queue:     app1_lvq
                subscribed to trade/app1/>

To run the script on the appliance you must have a file-transfer user configured on 
the appliance, then scp the file up to the appliance via that user:

    scp scripts/ha_demo.cli ftpuser@192.168.56.102:/cliscripts/

As admin user, the uploaded script can then be executed via the CLI:

    solace> source script /cliscripts/ha_demo.cli stop-on-error no-prompt

2. BUILDING THE CODE

There are two maven sub-projects: one with the HA library built on the Solace JCSMP 
client API, the other with the sample consumer code and sample producer to drive a 
load. They do require that the sol-jcsmp library and dependencies are installed to 
the local maven repository. A set of POMs and a script are provided to install them 
in the scripts/maven/ directory, these should be copied into the sol-jcsmp/lib/
directory to be run from there with the jar files.

3. RUNNING THE CODE

The sample app (com.solacesystems.demo.MatchingEngineSample) supports these arguments:
USAGE: <IP> <APP-ID> <APP-INST-#> <SOL-VPN> <SOL-USER> <SOL-PASS> <QUEUE> <LVQ> <OUT-TOPIC>

The driver app (com.solacesystems.demo.MockOrderGateway) supports these arguments:
USAGE: SamplePublisher <HOST> <VPN> <USER> <PASS> <PUB-TOPIC> <STARTID>

Run several instances of the consumer, e.g.:
java -cp clustered-app-lvq.jar:sol-jcsmp.jar:sol-common.jar:commons-lang.jar:commons-logging.jar: -jar clustered-matcher-sample.jar
 ... com.solacesystems.demo.MatchingEngineSample 192.168.56.102 app1 1 ha_demo app1 app1 app1_inst1 app1_lvq trade/app1/new
 ... com.solacesystems.demo.MatchingEngineSample 192.168.56.102 app1 2 ha_demo app1 app1 app1_inst2 app1_lvq trade/app1/new
 ... com.solacesystems.demo.MockOrderGateway 192.168.56.102 ha_demo ogw ogw order/app1/new 1
