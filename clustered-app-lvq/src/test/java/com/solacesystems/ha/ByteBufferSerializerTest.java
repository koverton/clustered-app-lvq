package com.solacesystems.ha;

import com.solacesystems.ha.ByteBufferSerializer;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ByteBufferSerializerTest {

    @Test
    public void testSerializeIntRoundtrip() {
        int input = 5;
        ByteBuffer dest = ByteBuffer.allocate(4);
        ByteBufferSerializer.SerializeInt(dest, input);
        dest.flip();
        int output = ByteBufferSerializer.DeserializeInt(dest);
        assertEquals(input, output);
    }

    @Test
    public void testMultipleSerializeIntRoundtrip() {
        ByteBuffer dest = ByteBuffer.allocate(4);
        for(int input = 0; input < 10; input++) {
            dest.clear();
            ByteBufferSerializer.SerializeInt(dest, input);
            dest.flip();
            int output = ByteBufferSerializer.DeserializeInt(dest);
            assertEquals(input, output);
        }
    }


    @Test
    public void testSerializeDoubleRoundtrip() {
        double input = 5.55;
        ByteBuffer dest = ByteBuffer.allocate(8);
        ByteBufferSerializer.SerializeDouble(dest, input);
        dest.flip();
        double output = ByteBufferSerializer.DeserializeDouble(dest);
        assertEquals(input, output, 0.001);
    }

    @Test
    public void testMultipleSerializeDoubleRoundtrip() {
        ByteBuffer dest = ByteBuffer.allocate(8);
        for(double input = 0.5; input < 10; input += 1.0) {
            dest.clear();
            ByteBufferSerializer.SerializeDouble(dest, input);
            dest.flip();
            double output = ByteBufferSerializer.DeserializeDouble(dest);
            assertEquals(input, output, 0.001);
        }
    }

    @Test
    public void testSerializeStringRoundtrip() throws UnsupportedEncodingException {
        String input = "five";
        ByteBuffer dest = ByteBuffer.allocate(10);
        ByteBufferSerializer.SerializeString(dest, input);
        dest.flip();
        String output = ByteBufferSerializer.DeserializeString(dest);
        assertEquals(input, output);
    }

    @Test
    public void testMultipleSerializeStringRoundtrip() throws UnsupportedEncodingException {
        ByteBuffer dest = ByteBuffer.allocate(10);
        for(Integer i = 0; i < 10; i++) {
            String input = i.toString();
            dest.clear();
            ByteBufferSerializer.SerializeString(dest, input);
            dest.flip();
            String output = ByteBufferSerializer.DeserializeString(dest);
            assertEquals(input, output);
        }
    }

    @Test
    public void testSerializeBoolRoundtrip() {
        boolean input = true;
        ByteBuffer dest = ByteBuffer.allocate(1);
        dest.clear();
        ByteBufferSerializer.SerializeBool(dest, input);
        dest.flip();
        boolean output = ByteBufferSerializer.DeserializeBool(dest);
        assertEquals(input, output);
    }

    @Test
    public void testMultipleSerializeBoolRoundtrip() {
        ByteBuffer dest = ByteBuffer.allocate(1);
        boolean input = true;
        for(int i = 0; i < 10; i++) {
            dest.clear();
            ByteBufferSerializer.SerializeBool(dest, input);
            dest.flip();
            boolean output = ByteBufferSerializer.DeserializeBool(dest);
            assertEquals(input, output);
            input = !input;
        }
    }
}
