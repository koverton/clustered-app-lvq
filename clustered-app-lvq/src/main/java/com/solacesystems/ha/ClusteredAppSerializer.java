package com.solacesystems.ha;

import com.solacesystems.jcsmp.BytesXMLMessage;

import java.nio.ByteBuffer;

/**
 * Used by the ClusterConnector to serialize/deserialize application messages. Decoupling this from the
 * ClusterConnector and ClusterModel allows different application instances to define their own messages
 * for application input and application state output.
 *
 * @param <InputType> input message type; must extend Ordered to ensure a sequence number is present
 * @param <OutputType> output message type; must also extend Ordered to ensure a sequence number is present
 */
public interface ClusteredAppSerializer<InputType extends Ordered, OutputType extends Ordered> {

    /**
     * Given a Solace message, convert it's payload to an object of type InputType
     * @param msg -- Solace message expected to contain a serialized InputType instance
     * @return -- A populated instance of InputType
     */
    InputType DeserializeInput(BytesXMLMessage msg);

    /**
     * Given an instance of InputType, serialize its contents to a byte-stream and append those bytes to a ByteBuffer
     * @param input -- The object instance to be serialized
     * @return -- the ByteBuffer with the serialized byte-stream
     */
    ByteBuffer SerializeInput(InputType input);

    /**
     * Given a Solace message, convert it's payload to an object of type OutputType
     * @param msg -- Solace message expected to contain a serialized OutputType instance
     * @return -- A populated instance of OutputType
     */
    OutputType DeserializeOutput(BytesXMLMessage msg);

    /**
     * Given an instance of OutputType, serialize its contents to a byte-stream and append those bytes to a ByteBuffer
     * @param output -- The object instance to be serialized
     * @return -- the ByteBuffer with the serialized byte-stream
     */
    ByteBuffer SerializeOutput(OutputType output);
}
