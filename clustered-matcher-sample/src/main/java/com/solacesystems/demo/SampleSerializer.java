package com.solacesystems.demo;

import com.solacesystems.ha.ByteBufferSerializer;
import com.solacesystems.jcsmp.BytesXMLMessage;

import com.solacesystems.ha.ClusteredAppSerializer;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class SampleSerializer implements ClusteredAppSerializer<ClientOrder, AppState> {
    public ClientOrder DeserializeInput(BytesXMLMessage msg) {
        ByteBuffer data = msg.getAttachmentByteBuffer();
        ClientOrder order = new ClientOrder(ByteBufferSerializer.DeserializeInt(data));
        order.setIsBuy(ByteBufferSerializer.DeserializeBool(data));
        order.setQuantity(ByteBufferSerializer.DeserializeDouble(data));
        order.setPrice(ByteBufferSerializer.DeserializeDouble(data));
        try {
            order.setInstrument(ByteBufferSerializer.DeserializeString(data));
        }
        catch(UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return order;
    }


    public ByteBuffer SerializeInput(ClientOrder o)
    {
        //ByteBufferSerializer.SerializeClientOrder(_inmsgbuf, input);
        _inmsgbuf.clear();
        ByteBufferSerializer.SerializeInt(_inmsgbuf, o.getSequenceId());
        ByteBufferSerializer.SerializeBool(_inmsgbuf, o.isBuy());
        ByteBufferSerializer.SerializeDouble(_inmsgbuf, o.getQuantity());
        ByteBufferSerializer.SerializeDouble(_inmsgbuf, o.getPrice());
        ByteBufferSerializer.SerializeString(_inmsgbuf, o.getInstrument());
        return _inmsgbuf;
    }

    public AppState DeserializeOutput(BytesXMLMessage msg) {
        ByteBuffer data = msg.getAttachmentByteBuffer();
        try {
            int seqId = ByteBufferSerializer.DeserializeInt(data);
            AppState state = new AppState(ByteBufferSerializer.DeserializeString(data));
            state.setSequenceId(seqId);
            return state;
        }
        catch(UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ByteBuffer SerializeOutput(AppState o) {
        //ByteBufferSerializer.SerializeAppState(_outmsgbuf, output);
        _outmsgbuf.clear();
        ByteBufferSerializer.SerializeInt(_outmsgbuf, o.getSequenceId());
        ByteBufferSerializer.SerializeString(_outmsgbuf, o.getInstrument());
        return _outmsgbuf;
    }

    private final ByteBuffer _outmsgbuf = ByteBuffer.allocate(AppState.SERIALIZED_SIZE);
    private final ByteBuffer _inmsgbuf = ByteBuffer.allocate(ClientOrder.SERIALIZED_SIZE);
}
