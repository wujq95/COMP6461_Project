package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ReceivePacket extends Thread{

    private Connection connection;
    private boolean deBugging;
    private String fileDirectory;
    private SocketAddress routerAddr;
    private long sequenceNum;
    private SlidingWindow slidingWindow;
    private int count;

    public ReceivePacket(Connection connection, boolean deBugging, String fileDirectory, SocketAddress routerAddr, long sequenceNum){
        this.routerAddr = routerAddr;
        this.connection = connection;
        this.deBugging = deBugging;
        this.fileDirectory = fileDirectory;
        this.sequenceNum = sequenceNum;
        count = 0;
    }

    @Override
    public void run() {
        while(true){
            try{
                connection.getChannel().configureBlocking(false);
                Selector selector = Selector.open();
                connection.getChannel().register(selector, OP_READ);
                selector.select(2000);

                Set<SelectionKey> keys = selector.selectedKeys();
                if(keys.isEmpty()){
                    if(connection.isFinished()){
                        count++;
                        if(count>=4){
                            connection.printDebuggingMsg("Connection is closed");
                            break;
                        }
                    }else{
                        connection.printDebuggingMsg("No response after timeout");
                    }
                    continue;
                }
                ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                SocketAddress router = connection.getChannel().receive(buf);
                buf.flip();
                Packet packet = Packet.fromBuffer(buf);
                buf.flip();
                packetHandle(packet);
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * handle packet
     * @param packet
     */
    private void packetHandle(Packet packet){
        if(packet.getType()==3&&packet.getSequenceNumber()==sequenceNum+1){
            connection.printDebuggingMsg("Handshake3: server received ack");
            connection.setConnected(true);
            return;
        }
        if(connection.isConnected()){
            if(packet.getType()==0){
                connection.printDebuggingMsg("Server received the data packet");
                Packet resPacket = new Packet.Builder()
                        .setType(3)
                        .setSequenceNumber(packet.getSequenceNumber())
                        .setPortNumber(packet.getPeerPort())
                        .setPeerAddress(packet.getPeerAddress())
                        .setPayload("".getBytes())
                        .create();
                try {
                    connection.getChannel().send(resPacket.toBuffer(),connection.getRouter());
                    ParseRequest parseRequest = new ParseRequest(deBugging,fileDirectory);
                    String payload = new String(packet.getPayload(), UTF_8).trim();
                    Request request = parseRequest.parseRequest(payload);
                    Response response = parseRequest.createResponse(request);
                    String handledResponse = parseRequest.handleResponse(response);
                    connection.makePackets(handledResponse,packet.getSequenceNumber()+1,new InetSocketAddress(packet.getPeerAddress(),packet.getPeerPort()));
                    slidingWindow = new SlidingWindow(connection);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }else if(packet.getType()==4||packet.getType()==5||packet.getType()==6){
                connection.printDebuggingMsg("Server received "+packet.getSequenceNumber()+" data packet");
                connection.addReceivePackets(packet);
                Packet resPacket = new Packet.Builder()
                        .setType(3)
                        .setSequenceNumber(packet.getSequenceNumber())
                        .setPortNumber(packet.getPeerPort())
                        .setPeerAddress(packet.getPeerAddress())
                        .setPayload("".getBytes())
                        .create();
                connection.printDebuggingMsg("Server sent "+packet.getSequenceNumber()+" ack");
                try {
                    connection.getChannel().send(resPacket.toBuffer(),connection.getRouter());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(packet.getType()==4){
                    connection.setStartNum(packet.getSequenceNumber());
                } else if(packet.getType()==6){
                    connection.setEndNum(packet.getSequenceNumber());
                }
                if(connection.getStartNum()==-1||connection.getEndNum()==-1){
                    return;
                }
                if(connection.receiveAllPackets()){
                    try {
                        ParseRequest parseRequest = new ParseRequest(deBugging,fileDirectory);
                        String payload = "";
                        Iterator<Map.Entry<Long, Packet>> it = connection.getReceivePackets().entrySet().iterator();
                        while(it.hasNext()) {
                            Map.Entry<Long, Packet> entry = it.next();
                            payload += new String(entry.getValue().getPayload(), UTF_8).trim();
                        }
                        Request request = parseRequest.parseRequest(payload);
                        Response response = parseRequest.createResponse(request);
                        String handledResponse = parseRequest.handleResponse(response);
                        connection.makePackets(handledResponse,connection.getEndNum()+1,new InetSocketAddress(packet.getPeerAddress(),packet.getPeerPort()));
                        slidingWindow = new SlidingWindow(connection);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }else if(packet.getType()==7){
                connection.printDebuggingMsg("Server received "+packet.getSequenceNumber()+" ack");
                slidingWindow.getWindow().put(packet.getSequenceNumber(),true);
                slidingWindow.sendNextPacket(packet.getSequenceNumber());
                for(boolean value: slidingWindow.getWindow().values()){
                    if(!value) return;
                }
                connection.setFinished(true);
            }
        }
    }


}
