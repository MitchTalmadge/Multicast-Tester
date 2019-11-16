package com.mitchtalmadge.multicasttester;

import android.os.Handler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

public class MulticastSenderThread extends MulticastThread {

    private String messageToSend;

    public MulticastSenderThread(ConsoleFragment activity, String multicastIP, int multicastPort, String messageToSend) {
        super("MulticastSenderThread", activity, multicastIP, multicastPort, new Handler());
        this.messageToSend = messageToSend;
    }

    @Override
    public void run() {
        super.run();
        try {
            byte[] bytesToSend = messageToSend.getBytes();
            multicastSocket.send(new DatagramPacket(bytesToSend, bytesToSend.length, InetAddress.getByName(multicastIP), multicastPort));
            multicastSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
