package net.liveforcode.multicasttester;

import android.os.Handler;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;

public class MulticastListenerThread extends MulticastThread {

    private TextView consoleView;
    private final Handler handler;
    private DatagramPacket packet;

    public MulticastListenerThread(MainActivity activity, String multicastIP, int multicastPort, TextView consoleView) {
        super("MulticastListenerThread", activity, multicastIP, multicastPort);
        this.consoleView = consoleView;
        this.handler = new Handler();
    }


    @Override
    public void run() {
        super.run();

        this.packet = new DatagramPacket(new byte[512], 512);

        while (running.get()) {
            packet.setData(new byte[1024]);

            try {
                multicastSocket.receive(packet);
            } catch (IOException ignored) {
                continue;
            }


            final String data = new String(packet.getData()).trim();

            activity.log("Received! "+data);

            final String consoleMessage = "[" + ((getLocalIP().equals(packet.getAddress().getHostAddress())) ? "You" : packet.getAddress().getHostAddress()) + "] " + data + "\n";

            this.handler.post(new Runnable() {
                @Override
                public void run() {
                    consoleView.append(consoleMessage);
                }
            });
        }
        this.multicastSocket.close();
    }
}
