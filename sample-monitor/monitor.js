//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Monitor Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
var SOLACE_URL  = "http://192.168.56.102/solace/smf"
var SOLACE_VPN  = "ha_demo"
var SOLACE_USER = "monitor"
var SOLACE_PASS = "monitor"

// Initial data
var data =
[
    {
      Instance: 1,
      HAState: 'Init',
      SeqState: 'Disconnected',
      LastInput: '(null)',
      LastOutput: '(null)',
    },
    {
      Instance: 2,
      HAState: 'Init',
      SeqState: 'Disconnected',
      LastInput: '(null)',
      LastOutput: '(null)',
    },
    {
      Instance: 3,
      HAState: 'Init',
      SeqState: 'Disconnected',
      LastInput: '(null)',
      LastOutput: '(null)',
    }
]

//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
// UI Interaction -- initialize UI & Solace
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
function init() {
  updateUI(data[0])
  updateUI(data[1])
}

function getFieldValue(name) {
    var field = document.getElementById(name)
    if (field == null) {
      console.log('could not find document field named ' + name )
      return null
    }
    return field.value
}

function setFieldValue(name, value) {
    var field = document.getElementById(name)
    if (field == null) {
      console.log('could not find document field named ' + name )
    }
    field.value = value
}

function gt0(value) {
  if (value > 0) return value;
  return '-'
}
function updateUI(record) {
    hafld = 'HAStatus' + record.Instance
    setFieldValue(hafld, record.HAState)
    if (record.HAState == 'ACTIVE') 
      document.getElementById(hafld).className = 'active'
    else 
      document.getElementById(hafld).className = 'backup'
    seqfld = 'SeqStatus' + record.Instance
    setFieldValue(seqfld, record.SeqState)
    if (record.SeqState == 'UPTODATE') 
      document.getElementById(seqfld).className = 'uptodate'
    else if (record.SeqState == 'RECOVERING') 
      document.getElementById(seqfld).className = 'recovering'
    else
      document.getElementById(seqfld).className = 'disconnected'
    setFieldValue('LastInput' + record.Instance, gt0(record.LastInput))
    setFieldValue('LastOutput' + record.Instance, gt0(record.LastOutput))
}
function updateRec(record, upd) {
  //console.log(record)
    record.HAState = upd.HAState
    record.SeqState = upd.SeqState
    record.LastInput = upd.LastInput
    record.LastOutput = upd.LastOutput
  //console.log(record)
}

function setVisible(id, isVisible) {
    document.getElementById(id).style.visibility = 
            (isVisible ? 'visible' : 'hidden')
}

//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//       Solace code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

var sess = null // The Solace session instance

// Solace inbound message callback
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
function message_cb(sess, msg, uo, unused) {
  // UPDATE Monitor display!
  var payload = msg.getBinaryAttachment()
  var topic = msg.getDestination().getName()
  //console.log(topic + ' : ' + payload)
  if ( -1 == topic.search('CLIENT_CLIENT_DISCONNECT') ) {
    var upd = JSON.parse(payload)
    var record = data[upd.Instance-1]
    updateRec(record, upd)
    updateUI(record)
  }
  else {
    //#LOG/INFO/CLIENT/{appliance-name}/CLIENT_CLIENT_DISCONNECT/{vpn-name}/{client-name}
    //setFieldValue('lastDisconnect', topic)
    var inst = topic.match('[0-9]$')[0]
    console.log('Got inst #' + inst)
    if (inst <= data.length) {
      var rec = {
        Instance: inst,
        HAState: 'Init',
        SeqState: 'Disconnected',
        LastInput: '(null)',
        LastOutput: '(null)',
      }
      var record = data[inst-1]
      updateRec(record, rec)
      updateUI(record)
    }
  }
}

// Solace session event callback
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
function session_cb(sess, evt, userobj) {
  console.log(evt)
  var topicStr = "monitor/state"
  // Wait until the session is UP before subscribing
  if (evt.sessionEventCode == solace.SessionEventCode.UP_NOTICE) {
    var topic = solace.SolclientFactory.createTopic(topicStr)
    sess.subscribe(topic, true, topicStr, 30000)
    topic = solace.SolclientFactory.createTopic("#LOG/INFO/CLIENT/*/CLIENT_CLIENT_DISCONNECT/>")
    sess.subscribe(topic, true, "monitor", 30000)
  }
  // Reconnect if we've disconnected
  else if (evt.sessionEventCode == solace.SessionEventCode.DISCONNECTED) {
    sess.connect()
  }
}

//  Initialize solace session and connect
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
function initSolaceConn() {
  var factoryProps = new solace.SolclientFactoryProperties()
  factoryProps.logLevel = solace.LogLevel.INFO
  solace.SolclientFactory.init(factoryProps)
  var props = new solace.SessionProperties()
  props.url      = 'http://' + getFieldValue('host')
  props.vpnName  = getFieldValue('vpn')
  props.userName = getFieldValue('user')
  props.password = getFieldValue('pass')
  try {
    sess = solace.SolclientFactory.createSession(props,
            new solace.MessageRxCBInfo(message_cb, data),
            new solace.SessionEventCBInfo(session_cb, data))
    sess.connect()
  }
  catch(error) {
    console.log(error)
  }
}
