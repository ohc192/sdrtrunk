/*******************************************************************************
 * sdr-trunk
 * Copyright (C) 2014-2018 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by  the Free Software Foundation, either version 3 of the License, or  (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful,  but WITHOUT ANY WARRANTY; without even the implied
 * warranty of  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License  along with this program.
 * If not, see <http://www.gnu.org/licenses/>
 *
 ******************************************************************************/
package io.github.dsheirer.source.tuner.channel;

import io.github.dsheirer.dsp.filter.FilterFactory;
import io.github.dsheirer.dsp.filter.Window;
import io.github.dsheirer.dsp.filter.cic.ComplexPrimeCICDecimate;
import io.github.dsheirer.dsp.mixer.IOscillator;
import io.github.dsheirer.dsp.mixer.Oscillator;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.buffer.OverflowableReusableBufferTransferQueue;
import io.github.dsheirer.sample.buffer.ReusableComplexBuffer;
import io.github.dsheirer.sample.buffer.ReusableComplexBufferQueue;
import io.github.dsheirer.sample.complex.Complex;
import io.github.dsheirer.source.tuner.Tuner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CICTunerChannelSource
{
    private final static Logger mLog = LoggerFactory.getLogger(CICTunerChannelSource.class);

    //Maximum number of filled buffers for the blocking queue
    private static final int BUFFER_MAX_CAPACITY = 300;

    //Threshold for resetting buffer overflow condition
    private static final int BUFFER_OVERFLOW_RESET_THRESHOLD = 100;

    private static double CHANNEL_RATE = 48000.0;
    private static int CHANNEL_PASS_FREQUENCY = 12000;

    private OverflowableReusableBufferTransferQueue<ReusableComplexBuffer> mBuffer;
    private ReusableComplexBufferQueue mReusableComplexBufferQueue = new ReusableComplexBufferQueue("CICTunerChannelSource");
    private IOscillator mFrequencyCorrectionMixer;
    private ComplexPrimeCICDecimate mDecimationFilter;
    private Listener<ReusableComplexBuffer> mListener;
    private List<ReusableComplexBuffer> mSampleBuffers = new ArrayList<>();
    private long mTunerFrequency = 0;
    private double mTunerSampleRate;
    private long mChannelFrequencyCorrection = 0;

    /**
     * Implements a heterodyne/decimate Digital Drop Channel (DDC) to decimate the IQ output from a tuner down to a
     * fixed 48 kHz IQ channel rate.
     *
     * Note: this class can only be used once (started and stopped) and a new tuner channel source must be requested
     * from the tuner once this object has been stopped.  This is because channels are managed dynamically and center
     * tuned frequency may have changed since this source was obtained and thus the tuner might no longer be able to
     * source this channel once it has been stopped.
     *
     * @param tunerChannel specifying the center frequency for the DDC
     */
    public CICTunerChannelSource(Tuner tuner, TunerChannel tunerChannel)
    {
        mBuffer = new OverflowableReusableBufferTransferQueue<>(BUFFER_MAX_CAPACITY, BUFFER_OVERFLOW_RESET_THRESHOLD);
//        mBuffer.setSourceOverflowListener(this);

        //Setup the frequency translator to the current source frequency
        mTunerFrequency = tuner.getTunerController().getFrequency();
        long frequencyOffset = mTunerFrequency - tunerChannel.getFrequency();
        mFrequencyCorrectionMixer = new Oscillator(frequencyOffset, tuner.getTunerController().getSampleRate());

        //Setup the decimation filter chain
        setSampleRate(tuner.getTunerController().getSampleRate());
    }

    /**
     * Sets the center frequency for the incoming sample buffers
     * @param frequency in hertz
     */
    protected void setFrequency(long frequency)
    {
        mTunerFrequency = frequency;

        //Reset frequency correction so that consumer components can adjust
        setFrequencyCorrection(0);

        updateMixerFrequencyOffset();
    }

    /**
     * Current frequency correction being applied to the channel sample stream
     * @return correction in hertz
     */
    public long getFrequencyCorrection()
    {
        return mChannelFrequencyCorrection;
    }

    /**
     * Changes the frequency correction value and broadcasts the change to the registered downstream listener.
     * @param correction current frequency correction value.
     */
    protected void setFrequencyCorrection(long correction)
    {
        mChannelFrequencyCorrection = correction;

        updateMixerFrequencyOffset();

//        broadcastConsumerSourceEvent(SourceEvent.frequencyCorrectionChange(mChannelFrequencyCorrection));
    }

    /**
     * Primary input method for receiving complex sample buffers from the wideband source (ie tuner)
     * @param buffer to receive and eventually process
     */
    public void receive(ReusableComplexBuffer buffer)
    {
        mBuffer.offer(buffer);
    }

    public void setListener(Listener<ReusableComplexBuffer> listener)
    {
        /* Save a pointer to the listener so that if we have to change the
         * decimation filter, we can re-add the listener */
        mListener = listener;

        mDecimationFilter.setListener(listener);
    }

    public void removeListener(Listener<ReusableComplexBuffer> listener)
    {
        mDecimationFilter.removeListener();
    }

    /**
     * Updates the sample rate to the requested value and notifies any downstream components of the change
     * @param sampleRate to set
     */
    protected void setSampleRate(double sampleRate)
    {
        if(mTunerSampleRate != sampleRate)
        {
            mFrequencyCorrectionMixer.setSampleRate(sampleRate);

            /* Get new decimation filter */
            mDecimationFilter = FilterFactory.getDecimationFilter((int)sampleRate, (int)CHANNEL_RATE, 1,
                CHANNEL_PASS_FREQUENCY, 60, Window.WindowType.HAMMING);

            /* re-add the original output listener */
            mDecimationFilter.setListener(mListener);

            mTunerSampleRate = sampleRate;

//            broadcastConsumerSourceEvent(SourceEvent.channelSampleRateChange(getSampleRate()));
        }
    }

    /**
     * Calculates the local mixer frequency offset from the tuned frequency,
     * channel's requested frequency, and channel frequency correction.
     */
    private void updateMixerFrequencyOffset()
    {
//        long offset = mTunerFrequency - getTunerChannel().getFrequency() - mChannelFrequencyCorrection;
//        mFrequencyCorrectionMixer.setFrequency(offset);
    }

    public double getSampleRate()
    {
        return CHANNEL_RATE;
    }

    protected void processSamples()
    {
        mBuffer.drainTo(mSampleBuffers, 20);

        for(ReusableComplexBuffer buffer : mSampleBuffers)
        {
            float[] samples = buffer.getSamples();

            ReusableComplexBuffer reusableComplexBuffer = mReusableComplexBufferQueue.getBuffer(samples.length);
            float[] translated = reusableComplexBuffer.getSamples();

            /* Perform frequency translation */
            for(int x = 0; x < samples.length; x += 2)
            {
                mFrequencyCorrectionMixer.rotate();

                translated[x] = Complex.multiplyInphase(
                    samples[x], samples[x + 1], mFrequencyCorrectionMixer.inphase(), mFrequencyCorrectionMixer.quadrature());

                translated[x + 1] = Complex.multiplyQuadrature(
                    samples[x], samples[x + 1], mFrequencyCorrectionMixer.inphase(), mFrequencyCorrectionMixer.quadrature());
            }

            final ComplexPrimeCICDecimate filter = mDecimationFilter;

            if(filter != null)
            {
                filter.receive(reusableComplexBuffer);
            }

            buffer.decrementUserCount();
        }

        mSampleBuffers.clear();
    }
}