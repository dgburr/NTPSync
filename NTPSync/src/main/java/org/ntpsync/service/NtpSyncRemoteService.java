/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of NTPSync.
 * 
 * NTPSync is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NTPSync is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NTPSync.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.ntpsync.service;

import org.ntpsync.util.Constants;
import org.ntpsync.util.Log;
import org.ntpsync.util.NtpSyncUtils;
import org.ntpsync.util.PreferenceHelper;
import org.ntpsync.util.Utils;
import org.apache.commons.net.ntp.TimeInfo;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * This class exposes the remote service to the client
 */
public class NtpSyncRemoteService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Constants.TAG, "NtpSyncRemoteService, onCreate()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(Constants.TAG, "NtpSyncRemoteService, onDestroy()");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        return mBinder;
    }

    private final INtpSyncRemoteService.Stub mBinder = new INtpSyncRemoteService.Stub() {
        /**
         * Implementation of getSystemTimeOffset
         */
        @Override
        public int getSystemTimeOffset(String ntpHostname, Bundle output) throws RemoteException {
            Log.d(Constants.TAG, "getSystemTimeOffset called!");

            // get hostname from prefs if not defined
            if (ntpHostname == null) {
                ntpHostname = PreferenceHelper.getNtpServer(NtpSyncRemoteService.this);
            }

            int returnMessage;
            try {
                TimeInfo info = NtpSyncUtils.querySystemTime(ntpHostname);

                output.putLong(INtpSyncRemoteService.KEY_OFFSET, info.getOffsetMs());
                output.putLong(INtpSyncRemoteService.KEY_DELAY, info.getDelayMs());

                returnMessage = INtpSyncRemoteService.RETURN_OKAY;
            } catch (Exception e) {
                returnMessage = INtpSyncRemoteService.RETURN_SERVER_TIMEOUT;
            }

            return returnMessage;
        }

        /**
         * Implementation of getElapsedTimeOffset
         */
        @Override
        public int getElapsedTimeOffset(String ntpHostname, Bundle output) throws RemoteException {
            Log.d(Constants.TAG, "getElapsedTimeOffset called!");

            // get hostname from prefs if not defined
            if (ntpHostname == null) {
                ntpHostname = PreferenceHelper.getNtpServer(NtpSyncRemoteService.this);
            }

            int returnMessage;
            try {
                TimeInfo info = NtpSyncUtils.queryElapsedRealTime(ntpHostname);

                output.putDouble(INtpSyncRemoteService.KEY_OFFSET, info.getOffsetNs());
                output.putDouble(INtpSyncRemoteService.KEY_DELAY, info.getDelayNs());

                returnMessage = INtpSyncRemoteService.RETURN_OKAY;
            } catch (Exception e) {
                returnMessage = INtpSyncRemoteService.RETURN_SERVER_TIMEOUT;
            }

            return returnMessage;
        }

        /**
         * Implementation of setTime
         */
        @Override
        public int setTime(String ntpHostname, Bundle output) throws RemoteException {
            Log.d(Constants.TAG, "setTime called!");

            // get hostname from prefs if not defined
            if (ntpHostname == null) {
                ntpHostname = PreferenceHelper.getNtpServer(NtpSyncRemoteService.this);
            }

            int returnMessage;
            try {
                TimeInfo info = NtpSyncUtils.querySystemTime(ntpHostname);
                long offset = info.getOffsetMs();

                output.putLong(INtpSyncRemoteService.KEY_OFFSET, offset);
                output.putLong(INtpSyncRemoteService.KEY_DELAY, info.getDelayMs());

                returnMessage = Utils.setTime(offset);
            } catch (Exception e) {
                returnMessage = INtpSyncRemoteService.RETURN_SERVER_TIMEOUT;
            }

            return returnMessage;
        }
    };

}
