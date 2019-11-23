package com.mitchtalmadge.multicasttester.stream.rtp;

import android.content.Context;
import android.media.MediaCodec;
import android.util.Log;
import android.util.Pair;

import com.pedro.rtsp.rtp.packets.H264Packet;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MulticastRtpStreamHandler extends Thread {

    private String ip;
    private int port;

    private MulticastDispatchThread multicastDispatchThread;
    private boolean running = true;

    private byte[] sps;
    private byte[] pps;
    private ConcurrentLinkedQueue<Pair<ByteBuffer, MediaCodec.BufferInfo>> h264BufferQueue = new ConcurrentLinkedQueue<>();

    public void connect(String ip, String port, Context context) {
        this.ip = ip;
        this.port = Integer.parseInt(port);

        multicastDispatchThread = new MulticastDispatchThread(ip, this.port, context);
        multicastDispatchThread.start();

        running = true;
        start();
    }

    public void disconnect() {
        multicastDispatchThread.stopGracefully();
        try {
            multicastDispatchThread.join();
        } catch (InterruptedException ignored) {
        }

        try {
            running = false;
            h264BufferQueue.clear();
            join();
        } catch (InterruptedException ignored) {
        }
    }

    public void setSpsPps(ByteBuffer sps, ByteBuffer pps) {
        Log.v(getClass().getName(), "SPS and PPS set.");

        this.sps = new byte[sps.remaining()];
        sps.get(this.sps);
        sps.rewind();

        this.pps = new byte[pps.remaining()];
        pps.get(this.pps);
        pps.rewind();
    }

    public void writeH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo bufferInfo) {
        h264Buffer.rewind();
        ByteBuffer copy = ByteBuffer.allocate(h264Buffer.remaining());
        copy.put(h264Buffer);
        h264BufferQueue.add(Pair.create(copy, bufferInfo));
    }

    @Override
    public void run() {
        while (running) {
            if (sps == null || pps == null)
                continue;

            if (h264BufferQueue.isEmpty())
                continue;

            //Log.v(getClass().getName(), "Creating H264 packet.");

            Pair<ByteBuffer, MediaCodec.BufferInfo> h264BufferPair = h264BufferQueue.remove();
            ByteBuffer h264Buffer = h264BufferPair.first;
            MediaCodec.BufferInfo bufferInfo = h264BufferPair.second;

            H264Packet h264Packet = new H264Packet(sps, pps, rtpFrame -> multicastDispatchThread.addToQueue(rtpFrame));
            h264Packet.setPorts(this.port, this.port + 1);
            h264Packet.createAndSendPacket(h264Buffer, bufferInfo);
        }
    }
}
