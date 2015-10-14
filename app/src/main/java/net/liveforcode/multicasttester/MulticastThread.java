package net.liveforcode.multicasttester;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.concurrent.atomic.AtomicBoolean;

public class MulticastThread extends Thread {

    final AtomicBoolean running = new AtomicBoolean(true);
    final MainActivity activity;
    final String multicastIP;
    final int multicastPort;
    protected MulticastSocket multicastSocket;
    private InetAddress inetAddress;
    private NetworkInterface networkInterface;

    public MulticastThread(String threadName, MainActivity activity, String multicastIP, int multicastPort) {
        super(threadName);
        this.activity = activity;
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
    }

    @Override
    public void run() {
        try {
            WifiManager wifiManager = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int wifiIPInt = wifiInfo.getIpAddress();
            byte[] wifiIPByte = new byte[]{
                    (byte) (wifiIPInt & 0xff),
                    (byte) (wifiIPInt >> 8 & 0xff),
                    (byte) (wifiIPInt >> 16 & 0xff),
                    (byte) (wifiIPInt >> 24 & 0xff)};
            this.inetAddress = InetAddress.getByAddress(wifiIPByte);
            this.networkInterface = NetworkInterface.getByInetAddress(inetAddress);

            this.multicastSocket = new MulticastSocket(multicastPort);
            multicastSocket.setNetworkInterface(networkInterface);
            multicastSocket.joinGroup(InetAddress.getByName(multicastIP));
            multicastSocket.setSoTimeout(100);
            multicastSocket.setTimeToLive(2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String getLocalIP()
    {
        return this.inetAddress.getHostAddress();
    }

    void outputErrorToConsole(final String errorMessage) {
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                activity.outputErrorToConsole(errorMessage);
            }
        });
    }

    public void stopRunning() {
        this.running.set(false);
    }
}
