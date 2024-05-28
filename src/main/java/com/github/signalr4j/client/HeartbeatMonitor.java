/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
See License.txt in the project root for license information.
*/

package com.github.signalr4j.client;

import java.util.Calendar;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Heartbeat Monitor to detect slow or timed out connections
 */
public class HeartbeatMonitor {
    private Runnable mOnWarning;

    private Runnable mOnTimeout;

    private KeepAliveData mKeepAliveData;

    private ScheduledThreadPoolExecutor mExecutor;

    private volatile boolean mTimedOut = false;

    private volatile boolean mHasBeenWarned = false;

    private volatile boolean mStopped = true;

    private Object mSync = new Object();

    /**
     * Starts the monitor
     * 
     * @param keepAliveData
     *            Data for keep-alive timings
     * @param connection
     *            Connection to monitor
     */
    public void start(KeepAliveData keepAliveData, final ConnectionBase connection) {
        if (keepAliveData == null) {
            throw new IllegalArgumentException("keepAliveData cannot be null");
        }

        if (mKeepAliveData != null) {
            stop();
        }

        synchronized (mSync) {
            mKeepAliveData = keepAliveData;

            mTimedOut = false;
            mHasBeenWarned = false;
            mStopped = false;

            long interval = mKeepAliveData.getCheckInterval();

            mExecutor = new ScheduledThreadPoolExecutor(1);
            mExecutor.scheduleAtFixedRate(new Runnable() {

                @Override
                public void run() {
                    Runnable mOnRun = null;
                    synchronized (mSync) {
                        if (!mStopped) {
                            if (connection.getState() == ConnectionState.Connected) {
                                long lastKeepAlive = mKeepAliveData.getLastKeepAlive();
                                long timeElapsed = Calendar.getInstance().getTimeInMillis() - lastKeepAlive;

                                if (timeElapsed >= mKeepAliveData.getTimeout()) {
                                    if (!mTimedOut) {
                                        // Connection has been lost
                                        mTimedOut = true;
                                        mOnRun = mOnTimeout;
                                    }
                                } else if (timeElapsed >= mKeepAliveData.getTimeoutWarning()) {
                                    if (!mHasBeenWarned) {
                                        // Inform user and set HasBeenWarned to
                                        // true
                                        mHasBeenWarned = true;
                                        mOnRun = mOnWarning;
                                    }
                                } else {
                                    mHasBeenWarned = false;
                                    mTimedOut = false;
                                }
                            }
                        }
                    }
                    if (mOnRun != null)
                      mOnRun.run();
                }
            }, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the heartbeat monitor
     */
    public void stop() {
        if (!mStopped) {
            synchronized (mSync) {
                mStopped = true;
                if (mExecutor != null) {
                    mExecutor.shutdown();
                    mExecutor = null;
                }
            }
        }
    }

    /**
     * Alerts the monitor that a beat was detected
     */
    public void beat() {
        synchronized (mSync) {
            if (mKeepAliveData != null) {
                mKeepAliveData.setLastKeepAlive(Calendar.getInstance().getTimeInMillis());
            }
        }
    }

    /**
     * Returns the "Warning" event handler
     */
    public Runnable getOnWarning() {
        return mOnWarning;
    }

    /**
     * Sets the "Warning" event handler
     */
    public void setOnWarning(Runnable onWarning) {
        mOnWarning = onWarning;
    }

    /**
     * Returns the "Timeout" event handler
     */
    public Runnable getOnTimeout() {
        return mOnTimeout;
    }

    /**
     * Sets the "Timeout" event handler
     */
    public void setOnTimeout(Runnable onTimeout) {
        mOnTimeout = onTimeout;
    }

    /**
     * Returns the Keep Alive data
     */
    public KeepAliveData getKeepAliveData() {
        return mKeepAliveData;
    }

    /**
     * Sets the Keep Alive data
     */
    public void setKeepAliveData(KeepAliveData keepAliveData) {
        mKeepAliveData = keepAliveData;
    }
}
