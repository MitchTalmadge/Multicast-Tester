package net.liveforcode.multicasttester;

import android.content.Context;
import android.net.Network;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.logging.Logger;

public class MulticastSenderThread extends Thread {

    private MainActivity activity;
    private String messageToSend;
    private MulticastSocket multicastSocket;
    private final String multicastIP;
    private final int multicastPort;

    public MulticastSenderThread(MainActivity activity, String messageToSend, String multicastIP, int multicastPort) {
        super("MulticastSenderThread");
        this.activity = activity;
        this.messageToSend = messageToSend;
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
    }

    @Override
    public void run() {
        try {

            WifiManager wifiManager = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int wifiIPInt = wifiInfo.getIpAddress();
            byte[] wifiIPByte = new byte[] {
                    (byte) (wifiIPInt & 0xff),
                    (byte) (wifiIPInt >> 8 & 0xff),
                    (byte) (wifiIPInt >> 16 & 0xff),
                    (byte) (wifiIPInt >> 24 & 0xff)};
            InetAddress inetAddress = InetAddress.getByAddress(wifiIPByte);
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);

            this.multicastSocket = new MulticastSocket(multicastPort);
            multicastSocket.setNetworkInterface(networkInterface);
            multicastSocket.joinGroup(InetAddress.getByName(multicastIP));
            multicastSocket.setTimeToLive(2);

            byte[] bytesToSend = messageToSend.getBytes();
            Logger.getLogger("multicast-tester").info("Sending: " + messageToSend);
            multicastSocket.send(new DatagramPacket(bytesToSend, bytesToSend.length, InetAddress.getByName(multicastIP), multicastPort));
            Logger.getLogger("multicast-tester").info("Sent!");
            multicastSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
