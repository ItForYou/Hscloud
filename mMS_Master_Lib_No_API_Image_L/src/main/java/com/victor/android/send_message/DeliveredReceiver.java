/*
 * Copyright 2013 Victor Ju
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.victor.android.send_message;

import java.util.Calendar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public class DeliveredReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v("delivery_receiver", "marking message as delivered");
        Uri uri;

        if(Build.VERSION.SDK_INT <= 10 /* GINGERBREAD 2.3.3-2.3.4 */)
    	{
        	Log.v("delivery_receiver", "Ignore delivery receiver under 2.3 version");
        	return;
    	}
        
        try {
            uri = Uri.parse(intent.getStringExtra("message_uri"));

            if (uri.equals("")) {
                uri = null;
            }
        } catch (Exception e) {
            uri = null;
        }

        switch (getResultCode()) {
            case Activity.RESULT_OK:
                // notify user that message was delivered
                Intent delivered = new Intent(Transaction.NOTIFY_OF_DELIVERY);
                delivered.putExtra("result", true);
                delivered.putExtra("message_uri", uri == null ? "" : uri.toString());
                context.sendBroadcast(delivered);

                if (uri != null) {
                    ContentValues values = new ContentValues();
                    values.put("status", "0");
                    values.put("date_sent", Calendar.getInstance().getTimeInMillis());
                    values.put("read", true);
                    context.getContentResolver().update(uri, values, null, null);
                } else {
                    Cursor query = context.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, "date desc");

                    // mark message as delivered in database
                    if (query.moveToFirst()) {
                        String id = query.getString(query.getColumnIndex("_id"));
                        ContentValues values = new ContentValues();
                        values.put("status", "0");
                        values.put("date_sent", Calendar.getInstance().getTimeInMillis());
                        values.put("read", true);
                        context.getContentResolver().update(Uri.parse("content://sms/sent"), values, "_id=" + id, null);
                    }

                    query.close();
                }

                break;
            case Activity.RESULT_CANCELED:
                // notify user that message failed to be delivered
                Intent notDelivered = new Intent(Transaction.NOTIFY_OF_DELIVERY);
                notDelivered.putExtra("result", false);
                notDelivered.putExtra("message_uri", uri == null ? "" : uri.toString());
                context.sendBroadcast(notDelivered);

                if (uri != null) {
                    ContentValues values = new ContentValues();
                    values.put("status", "64");
                    values.put("date_sent", Calendar.getInstance().getTimeInMillis());
                    values.put("read", true);
                    values.put("error_code", getResultCode());
                    context.getContentResolver().update(uri, values, null, null);
                } else {
                    Cursor query2 = context.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, "date desc");

                    // mark failed in database
                    if (query2.moveToFirst()) {
                        String id = query2.getString(query2.getColumnIndex("_id"));
                        ContentValues values = new ContentValues();
                        values.put("status", "64");
                        values.put("read", true);
                        values.put("error_code", getResultCode());
                        context.getContentResolver().update(Uri.parse("content://sms/sent"), values, "_id=" + id, null);
                    }

                    query2.close();
                }
                break;
        }

        context.sendBroadcast(new Intent("com.victor.android.send_message.REFRESH"));
    }
}
