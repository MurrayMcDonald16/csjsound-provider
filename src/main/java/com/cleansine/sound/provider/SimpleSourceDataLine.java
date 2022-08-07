package com.cleansine.sound.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

final class SimpleSourceDataLine extends SimpleDataLine implements SourceDataLine {
    private static final Logger logger = LoggerFactory.getLogger(SimpleSourceDataLine.class);

    SimpleSourceDataLine(DataLine.Info info, AudioFormat format, int bufferSize, SimpleMixer mixer, boolean use24bits) {
        super(info, mixer, format, bufferSize, mixer.getDeviceID(), true, use24bits);
    }

    public int write(byte[] bytes, int offset, int len) {
        synchronized (this) {
            logger.trace("Starting to write " + len + " bytes");
//            try {
//                this.os.write(bytes, offset, len);
//                this.os.flush();
//            } catch (Exception e) {
//                logger.error("Error writing to file", e);
//            }
            flushing = false;
            if (len == 0)
                return 0;
            if (len % getFormat().getFrameSize() != 0)
                throw new IllegalArgumentException("Requesting to write non-integral number of frames (" + len + " bytes, " + "frameBytes = " + getFormat().getFrameSize() + " bytes)");

            if (!isActive() && inIO) {
                setActive(true);
                setStarted(true);
            }
            int written = 0;
            while (!flushing) {
                int writtenInLoop;
                logger.trace("In-loop: trying to write " + len + " bytes");
                synchronized (lockNative) {
                    writtenInLoop = SimpleMixer.nWrite(nativePtr, bytes, offset, len);
                    if (writtenInLoop < 0)
                        // error in native layer
                        break;
                    bytePos += writtenInLoop;
                    if (writtenInLoop > 0)
                        drained = false;
                }
                logger.trace("In-loop: wrote " + writtenInLoop + " bytes");
                len -= writtenInLoop;
                written += writtenInLoop;
                if (inIO && len > 0) {
                    offset += writtenInLoop;
                    synchronized (lock) {
                        try {
                            logger.trace("Waiting in write loop for " + checkTimeMS + "ms");
                            lock.wait(checkTimeMS);
                        } catch (InterruptedException ignored) {
                        }
                    }
                } else {
                    break;
                }
            }
            if (written > 0 && !inIO)
                writtenWhenStopped = true;
            logger.trace("Wrote total " + written + " bytes");
            return written;
        }
    }

}
