package net.liveforcode.multicasttester;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.concurrent.atomic.AtomicBoolean;

public class MulticastListenerThread extends Thread {

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Handler handler;
    private MainActivity activity;
    private TextView consoleView;
    private MulticastSocket multicastSocket;
    private String multicastIP;
    private int multicastPort;
    private DatagramPacket packet;

    public MulticastListenerThread(MainActivity activity, TextView consoleView, String multicastIP, int multicastPort) {
        super("MulticastListenerThread");
        this.activity = activity;
        this.consoleView = consoleView;
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
        this.handler = new Handler();
    }


    @Override
    public void run() {
        try {
            this.packet = new DatagramPacket(new byte[512], 512);

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
            multicastSocket.setSoTimeout(100);
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (running.get()) {
            packet.setData(new byte[512]);

            try {
                multicastSocket.receive(packet);
            } catch (IOException ignored) {
                continue;
            }

            final String data = new String(packet.getData()).trim();

            this.handler.post(new Runnable() {
                @Override
                public void run() {
                    consoleView.append("[" + packet.getAddress() + "] " + data + "\n");
                }
            });
        }
        this.multicastSocket.close();

    }

    public void stopRunning() {
        this.running.set(false);
    }
}
