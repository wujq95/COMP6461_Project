package client;

import java.io.IOException;

public class SendPacket extends Thread{

    private SlidingWindow slidingWindow;
    private Packet packet;
    private Connection connection;
    private boolean notFirstTime;

    /**
     * constructor method
     * @param connection
     * @param slidingWindow
     * @param packet
     */
    public SendPacket(Connection connection, SlidingWindow slidingWindow, Packet packet){
        this.connection = connection;
        this.slidingWindow = slidingWindow;
        this.packet = packet;
    }

    @Override
    public void run() {
        while(!slidingWindow.getWindow().get(packet.getSequenceNumber())){
            try {
                if(notFirstTime){
                    System.out.println("The packet with sequence number "+ packet.getSequenceNumber()+" is time out");
                }
                notFirstTime = true;
                connection.getChannel().send(packet.toBuffer(), connection.getRouterAddr());
                System.out.println("A data packet sent to the sever. The sequence number is "+packet.getSequenceNumber());
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e){
                e.getStackTrace();
            }
        }
    }
}
