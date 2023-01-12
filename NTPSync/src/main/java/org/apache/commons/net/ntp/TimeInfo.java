package org.apache.commons.net.ntp;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper class to network time packet messages (NTP, etc) that computes
 * related timing info and stats.
 *
 * @author Jason Mathews, MITRE Corp
 *
 * @version $Revision: 1299238 $
 */
public class TimeInfo {

    private final NtpV3Packet _message;
    private List<String> _comments;
    private Double _delay; // nanoseconds
    private Double _offset; // nanoseconds

    /**
     * time at which time message packet was received by local machine
     */
    private final TimeStamp _returnTime;

    /**
     * flag indicating that the TimeInfo details was processed and delay/offset were computed
     */
    private boolean _detailsComputed;

    /**
     * Create TimeInfo object with raw packet message and destination time received.
     *
     * @param message NTP message packet
     * @param returnTime  destination receive time
     * @throws IllegalArgumentException if message is null
     */
    public TimeInfo(NtpV3Packet message, TimeStamp returnTime) {
        this(message, returnTime, null, true);
    }

    /**
     * Create TimeInfo object with raw packet message and destination time received.
     *
     * @param message NTP message packet
     * @param returnTime  destination receive time
     * @param comments List of errors/warnings identified during processing
     * @throws IllegalArgumentException if message is null
     */
    public TimeInfo(NtpV3Packet message, TimeStamp returnTime, List<String> comments)
    {
            this(message, returnTime, comments, true);
    }

    /**
     * Create TimeInfo object with raw packet message and destination time received.
     * Auto-computes details if computeDetails flag set otherwise this is delayed
     * until computeDetails() is called. Delayed computation is for fast
     * intialization when sub-millisecond timing is needed.
     *
     * @param msgPacket NTP message packet
     * @param returnTime  destination receive time
     * @param doComputeDetails  flag to pre-compute delay/offset values
     * @throws IllegalArgumentException if message is null
     */
    public TimeInfo(NtpV3Packet msgPacket, TimeStamp returnTime, boolean doComputeDetails)
    {
            this(msgPacket, returnTime, null, doComputeDetails);
    }

    /**
     * Create TimeInfo object with raw packet message and destination time received.
     * Auto-computes details if computeDetails flag set otherwise this is delayed
     * until computeDetails() is called. Delayed computation is for fast
     * intialization when sub-millisecond timing is needed.
     *
     * @param message NTP message packet
     * @param returnTime  destination receive time
     * @param comments  list of comments used to store errors/warnings with message
     * @param doComputeDetails  flag to pre-compute delay/offset values
     * @throws IllegalArgumentException if message is null
     */
    public TimeInfo(NtpV3Packet message, TimeStamp returnTime, List<String> comments,
                   boolean doComputeDetails)
    {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
        this._returnTime = returnTime;
        this._message = message;
        this._comments = comments;
        if (doComputeDetails) {
            computeDetails();
        }
    }

    /**
     * Add comment (error/warning) to list of comments associated
     * with processing of NTP parameters. If comment list not create
     * then one will be created.
     *
     * @param comment
     */
    public void addComment(String comment)
    {
        if (_comments == null) {
            _comments = new ArrayList<String>();
        }
        _comments.add(comment);
    }

    /**
     * Compute and validate details of the NTP message packet. Computed
     * fields include the offset and delay.
     */
    public void computeDetails()
    {
        if (_detailsComputed) {
            return; // details already computed - do nothing
        }
        _detailsComputed = true;
        if (_comments == null) {
            _comments = new ArrayList<String>();
        }

        // Originate Time is local time received by server (t1)
        TimeStamp origNtpTime = _message.getOriginateTimeStamp();
        double origTime = origNtpTime.getNanos();

        // Receive Time is time request received by server (t2)
        TimeStamp rcvNtpTime = _message.getReceiveTimeStamp();
        double rcvTime = rcvNtpTime.getNanos();

        // Transmit time is time reply sent by server (t3)
        TimeStamp xmitNtpTime = _message.getTransmitTimeStamp();
        double xmitTime = xmitNtpTime.getNanos();

        // Destination Time is local time of transmission (t4)
        double destTime = _returnTime.getNanos();

        /*
         * Round-trip network delay and local clock offset (or time drift) is calculated
         * according to this standard NTP equation:
         *
         * LocalClockOffset = ((ReceiveTimestamp - OriginateTimestamp) +
         *                     (TransmitTimestamp - DestinationTimestamp)) / 2
         *
         * equations from RFC-1305 (NTPv3)
         *      roundtrip delay = (t4 - t1) - (t3 - t2)
         *      local clock offset = ((t2 - t1) + (t3 - t4)) / 2
         *
         * It takes into account network delays and assumes that they are symmetrical.
         *
         * Note the typo in SNTP RFCs 1769/2030 which state that the delay
         * is (T4 - T1) - (T2 - T3) with the "T2" and "T3" switched.
         */
        if (origNtpTime.ntpValue() == 0)
        {
            // without originate time cannot determine when packet went out
            // might be via a broadcast NTP packet...
            if (xmitNtpTime.ntpValue() != 0)
            {
                _offset = Double.valueOf(xmitTime - destTime);
                _comments.add("Error: zero orig time -- cannot compute delay");
            } else {
                _comments.add("Error: zero orig time -- cannot compute delay/offset");
            }
        } else if (rcvNtpTime.ntpValue() == 0 || xmitNtpTime.ntpValue() == 0) {
            _comments.add("Warning: zero rcvNtpTime or xmitNtpTime");
            // assert destTime >= origTime since network delay cannot be negative
            if (origTime > destTime) {
                _comments.add("Error: OrigTime > DestRcvTime");
            } else {
                // without receive or xmit time cannot figure out processing time
                // so delay is simply the network travel time
                _delay = Double.valueOf(destTime - origTime);
            }
            // TODO: is offset still valid if rcvNtpTime=0 || xmitNtpTime=0 ???
            // Could always hash origNtpTime (sendTime) but if host doesn't set it
            // then it's an malformed ntp host anyway and we don't care?
            // If server is in broadcast mode then we never send out a query in first place...
            if (rcvNtpTime.ntpValue() != 0)
            {
                // xmitTime is 0 just use rcv time
                _offset = Double.valueOf(rcvTime - origTime);
            } else if (xmitNtpTime.ntpValue() != 0)
            {
                // rcvTime is 0 just use xmitTime time
                _offset = Double.valueOf(xmitTime - destTime);
            }
        } else
        {
            double delayValue = destTime - origTime;
            // assert xmitTime >= rcvTime: difference typically < 1ms
            if (xmitTime < rcvTime)
            {
                 // server cannot send out a packet before receiving it...
                _comments.add("Error: xmitTime < rcvTime"); // time-travel not allowed
            } else
            {
                 // subtract processing time from round-trip network delay
                 double delta = xmitTime - rcvTime;
                 delayValue -= delta; // delay = (t4 - t1) - (t3 - t2)
            }
            _delay = Double.valueOf(delayValue);
            if (origTime > destTime) {
                _comments.add("Error: OrigTime > DestRcvTime");
            }

            _offset = Double.valueOf(((rcvTime - origTime) + (xmitTime - destTime)) / 2);
        }
    }

    /**
     * Return list of comments (if any) during processing of NTP packet.
     *
     * @return List or null if not yet computed
     */
    public List<String> getComments()
    {
        return _comments;
    }

    /**
     * Get round-trip network delay in milliseconds.
     * If null then could not compute the delay.
     *
     * @return Long or null if delay not available.
     */
    public Long getDelayMs()
    {
        if (_delay == null) {
            return null;
        }
        return TimeUnit.MILLISECONDS.convert(Math.round(_delay), TimeUnit.NANOSECONDS);
    }

    /**
     * Get clock offset needed to adjust local clock to match remote clock in milliseconds.
     * If null then could not compute the offset.
     *
     * @return Long or null if offset not available.
     */
    public Long getOffsetMs()
    {
        if (_offset == null) {
            return null;
        }
        return TimeUnit.MILLISECONDS.convert(Math.round(_offset), TimeUnit.NANOSECONDS);
    }

    /**
     * Get round-trip network delay in nanoseconds.
     * If null then could not compute the delay.
     *
     * @return Double or null if delay not available.
     */
    public Double getDelayNs()
    {
        return _delay;
    }

    /**
     * Get clock offset needed to adjust local clock to match remote clock in nanoseconds.
     * If null then could not compute the offset.
     *
     * @return Double or null if offset not available.
     */
    public Double getOffsetNs()
    {
        return _offset;
    }

    /**
     * Returns NTP message packet.
     *
     * @return NTP message packet.
     */
    public NtpV3Packet getMessage()
    {
        return _message;
    }

    /**
     * Returns time at which time message packet was received by local machine.
     *
     * @return packet return time.
     */
    public TimeStamp getReturnTime()
    {
        return _returnTime;
    }

}
