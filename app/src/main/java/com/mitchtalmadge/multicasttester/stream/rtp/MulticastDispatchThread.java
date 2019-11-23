package com.mitchtalmadge.multicasttester.stream.rtp;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.pedro.rtsp.rtsp.RtpFrame;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MulticastDispatchThread extends Thread {

    private final String ip;
    private final int port;
    private Context context;

    private boolean running = true;

    private MulticastSocket multicastSocket;
    private ConcurrentLinkedQueue<RtpFrame> queue = new ConcurrentLinkedQueue<>();

    public MulticastDispatchThread(String ip, int port, Context context) {
        super("MulticastDispatchThread");
        this.ip = ip;
        this.port = port;
        this.context = context;
    }

    @Override
    public void run() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = Objects.requireNonNull(wifiManager).getConnectionInfo();

            int wifiIPInt = wifiInfo.getIpAddress();
            byte[] wifiIPBytes =
                    ByteBuffer
                            .allocate(4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(wifiIPInt)
                            .array();

            InetAddress inetAddress = InetAddress.getByAddress(wifiIPBytes);
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);

            multicastSocket = new MulticastSocket(port);
            multicastSocket.setNetworkInterface(networkInterface);
            multicastSocket.joinGroup(InetAddress.getByName(ip));
            multicastSocket.setSoTimeout(100);
            multicastSocket.setTimeToLive(2);

            while (running) {
                if (queue.isEmpty())
                    continue;

                RtpFrame rtpFrame = queue.remove();

                multicastSocket.send(new DatagramPacket(rtpFrame.getBuffer(), rtpFrame.getLength(), InetAddress.getByName(ip), port));
                //Log.v(getClass().getName(), "Wrote " + rtpFrame.getLength() + " bytes to socket");
            }

            multicastSocket.close();
        } catch (BindException e) {
            Log.e(getClass().getName(), "Failed to bind to multicast port for streaming.", e);
        } catch (IOException e) {
            Log.e(getClass().getName(), "Failed to join multicast group for streaming.", e);
        }
    }

    public void addToQueue(RtpFrame rtpFrame) {
        queue.add(rtpFrame);
    }

    public void stopGracefully() {
        running = false;
    }

}
