package com.dataart.demo;

import com.xuggle.xuggler.*;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

/**
 * Created by newbie on 03.11.16.
 */
public class VideoSlicer implements Video {
    private static final int DEFAULT_STREAM_ID = -1;

    private IContainer container = IContainer.make();
    private IPacket packet = IPacket.make();
    private IStreamCoder encoder;
    private IVideoPicture frame;
    private IConverter convertor;

    private int videoStreamID = DEFAULT_STREAM_ID;

    // all time stamps are in microseconds
    private long startTime = 0;
    private long finalTime = 0;
    private long timeStep = 500*1000;

    private boolean isCaptured = false;
    private boolean isStopDecode = false;

    private int previousDecodedSz = 0;
    
    public VideoSlicer(String videoFileName) {
        // verifying name of video file
        if (container.open(videoFileName, IContainer.Type.READ, null) < 0) {
            throw new IllegalArgumentException("VideoSlicer error: unable to open file \"" + videoFileName + "\"");
        }

        finalTime = container.getDuration();
        int streamsNumber = container.getNumStreams();

        // searching for video stream
        for (int i = 0; i < streamsNumber; ++i) {
            IStreamCoder currentEncoder = container.getStream(i).getStreamCoder();

            if (currentEncoder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
                videoStreamID = i;
                encoder = currentEncoder;

                break;
            }
        }

        if (videoStreamID == DEFAULT_STREAM_ID) {
            throw new RuntimeException("VideoSlicer error: video stream isn't found in file \"" + videoFileName + "\"");
        }

        // opening video encoder
        if (encoder.open(null, null) < 0) {
            throw new RuntimeException("VideoSlicer error: unable to open video encoder for file \"" +
                                       videoFileName + "\"");
        }

        frame = IVideoPicture.make(encoder.getPixelType(), encoder.getWidth(), encoder.getHeight());
        convertor = ConverterFactory.createConverter("XUGGLER-BGR-24", frame);

        isStopDecode = false;

        previousDecodedSz = 0;
    }

    public void close() {
        if (encoder.isOpen()) {
            encoder.close();
        }

        if (container.isOpened()) {
            container.close();
        }
    }

    private int decodePacket(String frameFileName, int previousDecodedSz) throws IOException {
        int offset = previousDecodedSz;

        while (offset < packet.getSize()) {
            int decodedSz = encoder.decodeVideo(frame, packet, offset);

            if (decodedSz < 0) {
                throw new RuntimeException("VideoSlicer error: unexpected error in video stream decoding");
            }

            offset += decodedSz;

            if (frame.isComplete()) {
                long currentTime = frame.getTimeStamp();

                if (currentTime > startTime) {
                    ImageIO.write(convertor.toImage(frame), "JPG", new File(frameFileName));
                    startTime += timeStep;

                    isCaptured = true;
                }

                if (currentTime >= finalTime) {
                    isStopDecode = true;
                }

                break;
            }
        }

        return offset;
    }

    @Override
    public boolean capture(String frameFileName) throws IOException {
        if (isStopDecode) {
            return false;
        }

        isCaptured = false;

        // continue decoding previous packet that had been read
        if (previousDecodedSz > 0) {
            previousDecodedSz = decodePacket(frameFileName, previousDecodedSz);
        }

        // reading and processing packets until
        while (!isCaptured) {
            if (container.readNextPacket(packet) < 0) {
                isStopDecode = true;
                return false;
            }

            if (packet.getStreamIndex() == videoStreamID) {
                previousDecodedSz = decodePacket(frameFileName, 0);
            }
        }

        return true;
    }

    public static void main(String[] args) {
        String videoSrc = "../files/video/mask-man-720x1280.mov";
        String imgFolder = "../files/pics";

        Video slicer = new VideoSlicer(videoSrc);

        int fileCounter = 0;

        try {
            while (slicer.capture(imgFolder + File.separator + String.format("%03d.jpg", fileCounter))) {
                ++fileCounter;
            }
        } catch(IOException exc) {
            exc.printStackTrace();
        } finally {
            ((VideoSlicer)slicer).close();
        }
    }
}
