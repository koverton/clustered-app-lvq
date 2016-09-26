package com.solacesystems.demo;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPFactory;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SampleSerializerTest {

    private static BytesXMLMessage wrap(ByteBuffer data) {
        BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
        msg.writeAttachment(data.array());
        return msg;
    }

    @Test
    public void testSerializeAppStateRoundtrip() throws UnsupportedEncodingException {
        AppState stack = new AppState("AAPL");
        ClientOrder sell = new ClientOrder(5);
        sell.setIsBuy(false);
        sell.setQuantity(1.2345);
        sell.setPrice(5.4321);
        sell.setInstrument("AAPL");
        stack.addOrder(sell);

        SampleSerializer serializer = new SampleSerializer();
        ByteBuffer dest = serializer.SerializeOutput(stack);
        dest.flip();

        BytesXMLMessage msg = wrap(dest);

        AppState output = serializer.DeserializeOutput(msg);
        assertEquals(sell.getSequenceId(), output.getSequenceId());
        assertEquals(sell.getInstrument(), output.getInstrument());
    }

    @Test
    public void testSerializeClientOrderRoundtrip() throws UnsupportedEncodingException {
        ClientOrder input = new ClientOrder(5);
        input.setIsBuy(false);
        input.setQuantity(1.2345);
        input.setPrice(5.4321);
        input.setInstrument("AAPL");

        SampleSerializer serializer = new SampleSerializer();
        ByteBuffer dest = serializer.SerializeInput(input);
        dest.flip();

        BytesXMLMessage msg = wrap(dest);

        ClientOrder output = serializer.DeserializeInput(msg);
        assertEquals(input.getSequenceId(), output.getSequenceId());
        assertEquals(input.getQuantity(), output.getQuantity(), 0.00001);
        assertEquals(input.getPrice(), output.getPrice(), 0.00001);
        assertEquals(input.getInstrument(), output.getInstrument());
    }

    @Test
    public void testSerializeEmptyClientOrderRoundtrip() throws UnsupportedEncodingException {
        ClientOrder input = new ClientOrder(5);

        SampleSerializer serializer = new SampleSerializer();
        ByteBuffer dest = serializer.SerializeInput(input);
        dest.flip();

        BytesXMLMessage msg = wrap(dest);

        ClientOrder output = serializer.DeserializeInput(msg);
        assertEquals(input.getSequenceId(), output.getSequenceId());
        assertEquals(input.getQuantity(), output.getQuantity(), 0.00001);
        assertEquals(input.getPrice(), output.getPrice(), 0.00001);
        assertNull(input.getInstrument());
        assertNull(output.getInstrument());
    }

}
