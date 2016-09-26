package com.solacesystems.ha;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class ByteBufferSerializer {
    public static ByteBuffer SerializeBool(ByteBuffer buffer, boolean b)
    {
        return SerializeByte(buffer, (byte) (b ? 0x01 : 0x00));
    }
    public static boolean DeserializeBool(ByteBuffer data)
    {
        return (byte) 01 == DeserializeByte(data);
    }


    public static ByteBuffer SerializeByte(ByteBuffer buffer, byte b)
    {
        return buffer.order(ByteOrder.LITTLE_ENDIAN)
                .put(b);
    }
    public static byte DeserializeByte(ByteBuffer data)
    {
        return data
                .order(ByteOrder.LITTLE_ENDIAN)
                .get();
    }

    public static ByteBuffer SerializeInt(ByteBuffer buffer, int i)
    {
        return buffer.order(ByteOrder.LITTLE_ENDIAN)
                .putInt(i);
    }
    public static int DeserializeInt(ByteBuffer data)
    {
        return data
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    public static ByteBuffer SerializeDouble(ByteBuffer buffer, double d)
    {
        return buffer.order(ByteOrder.LITTLE_ENDIAN)
                .putDouble(d);
    }
    public static double DeserializeDouble(ByteBuffer data)
    {
        return data
                .order(ByteOrder.LITTLE_ENDIAN)
                .getDouble();
    }

    public static ByteBuffer SerializeString(ByteBuffer buffer, String s) {
        if (s == null)
            return buffer.order(ByteOrder.LITTLE_ENDIAN).putInt(0);
        byte[] bytes = s.getBytes(Charset.forName("UTF-8"));
        buffer.order(ByteOrder.LITTLE_ENDIAN)
                .putInt(bytes.length)
                .put(bytes);
        return buffer;
    }
    public static String DeserializeString(ByteBuffer data) throws UnsupportedEncodingException {
        int len =  data
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
        if (len == 0)
            return null;
        byte[] sbytes = new byte[len];
        data.get(sbytes);
        return new String(sbytes, "UTF-8");
    }

}
