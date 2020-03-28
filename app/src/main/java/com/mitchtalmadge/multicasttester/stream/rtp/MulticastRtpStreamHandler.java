package com.mitchtalmadge.multicasttester.stream.rtp;

import android.content.Context;
import android.media.MediaCodec;
import android.util.Log;
import android.util.Pair;

import com.pedro.rtsp.rtp.packets.H264Packet;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MulticastRtpStreamHandler {

    private String ip;
    private int port;

    private RtpStreamDispatchThread rtpStreamDispatchThread;
    private MulticastDispatchThread multicastDispatchThread;

    private byte[] sps;
    private byte[] pps;

    public void connect(String ip, String port, Context context) {
        this.ip = ip;
        this.port = Integer.parseInt(port);

        disconnect();

        multicastDispatchThread = new MulticastDispatchThread(ip, this.port, context);
        multicastDispatchThread.start();

        rtpStreamDispatchThread = new RtpStreamDispatchThread();
        rtpStreamDispatchThread.start();
    }

    public void disconnect() {
        if (rtpStreamDispatchThread != null)
            rtpStreamDispatchThread.stopGracefully();

        if (multicastDispatchThread != null)
            multicastDispatchThread.stopGracefully();
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
        rtpStreamDispatchThread.addToQueue(copy, bufferInfo);
    }

    private class RtpStreamDispatchThread extends Thread {

        private boolean running = true;

        private H264Packet h264Packet;
        private ConcurrentLinkedQueue<Pair<ByteBuffer, MediaCodec.BufferInfo>> h264BufferQueue = new ConcurrentLinkedQueue<>();

        @Override
        public void run() {
            while (running) {
                if (sps == null || pps == null)
                    continue;

                if (h264Packet == null) {
                    h264Packet = new H264Packet(sps, pps, rtpFrame -> multicastDispatchThread.addToQueue(rtpFrame));
                    h264Packet.setPorts(port, port + 1);
                }

                if (h264BufferQueue.isEmpty())
                    continue;

                //Log.v(getClass().getName(), "Creating H264 packet.");

                Pair<ByteBuffer, MediaCodec.BufferInfo> h264BufferPair = h264BufferQueue.remove();
                ByteBuffer h264Buffer = h264BufferPair.first;
                MediaCodec.BufferInfo bufferInfo = h264BufferPair.second;

                h264Packet.createAndSendPacket(h264Buffer, bufferInfo);
            }
        }

        void addToQueue(ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
            h264BufferQueue.add(Pair.create(data, bufferInfo));
        }

        void stopGracefully() {
            running = false;
            h264BufferQueue.clear();
            try {
                join();
            } catch (InterruptedException ignored) {
            }
        }

    }

}
