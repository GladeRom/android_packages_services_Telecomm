/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.telecomm;

import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Settings;

import com.google.common.base.Preconditions;

/**
 * Plays the default ringtone. Uses {@link Ringtone} in a separate thread so that this class can be
 * used from the main thread.
 */
class AsyncRingtonePlayer {
    // Message codes used with the ringtone thread.
    static final int EVENT_PLAY = 1;
    static final int EVENT_STOP = 2;

    /** Handler running on the ringtone thread. */
    private Handler mHandler;

    /** The current ringtone. Only used by the ringtone thread. */
    private Ringtone mRingtone;

    /** Plays the ringtone. */
    void play() {
        Log.d(this, "Posting play.");
        postMessage(EVENT_PLAY, true /* shouldCreateHandler */);
    }

    /** Stops playing the ringtone. */
    void stop() {
        Log.d(this, "Posting stop.");
        postMessage(EVENT_STOP, false /* shouldCreateHandler */);
    }

    /**
     * Posts a message to the ringtone-thread handler. Creates the handler if specified by the
     * parameter shouldCreateHandler.
     *
     * @param messageCode The message to post.
     * @param shouldCreateHandler True when a handler should be created to handle this message.
     */
    private void postMessage(int messageCode, boolean shouldCreateHandler) {
        synchronized(this) {
            if (mHandler == null && shouldCreateHandler) {
                mHandler = getNewHandler();
            }

            if (mHandler == null) {
                Log.d(this, "Message %d skipped because there is no handler.", messageCode);
            } else {
                mHandler.sendEmptyMessage(messageCode);
            }
        }
    }

    /**
     * Creates a new ringtone Handler running in its own thread.
     */
    private Handler getNewHandler() {
        Preconditions.checkState(mHandler == null);

        HandlerThread thread = new HandlerThread("ringtone-player");
        thread.start();

        return new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case EVENT_PLAY:
                        handlePlay();
                        break;
                    case EVENT_STOP:
                        handleStop();
                        break;
                }
            }
        };
    }

    /**
     * Starts the actual playback of the ringtone. Executes on ringtone-thread.
     */
    private void handlePlay() {
        ThreadUtil.checkNotOnMainThread();
        Log.i(this, "Play ringtone.");

        if (mRingtone == null) {
            mRingtone = getCurrentRingtone();

            // Cancel everything if there is no ringtone.
            if (mRingtone == null) {
                handleStop();
                return;
            }
        }

        if (mRingtone.isPlaying()) {
            Log.d(this, "Ringtone already playing.");
        } else {
            mRingtone.play();
            Log.d(this, "Ringtone.play() invoked.");
        }
    }

    /**
     * Stops the playback of the ringtone. Executes on the ringtone-thread.
     */
    private void handleStop() {
        ThreadUtil.checkNotOnMainThread();
        Log.i(this, "Stop ringtone.");

        if (mRingtone != null) {
            Log.d(this, "Ringtone.stop() invoked.");
            mRingtone.stop();
            mRingtone = null;
        }

        synchronized(this) {
            if (mHandler.hasMessages(EVENT_PLAY)) {
                Log.v(this, "Keeping alive ringtone thread for pending messages.");
            } else {
                mHandler.removeMessages(EVENT_STOP);
                mHandler.getLooper().quitSafely();
                mHandler = null;
                Log.v(this, "Handler cleared.");
            }
        }
    }

    private Ringtone getCurrentRingtone() {
        // TODO: Needs support for custom ringtones.
        return RingtoneManager.getRingtone(
                TelecommApp.getInstance(), Settings.System.DEFAULT_RINGTONE_URI);
    }
}