/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.transaction;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.mms.util.SendingProgressTokenManager;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.GenericPdu;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.ReadRecInd;
import com.google.android.mms.pdu_alt.SendReq;
import com.google.android.mms.util_alt.SqliteWrapper;

public class MmsMessageSender implements MessageSender {
    private static final String TAG = "MmsMessageSender";

    private final Context mContext;
    private final Uri mMessageUri;
    private final long mMessageSize;

    // Default preference values
    private static final boolean DEFAULT_DELIVERY_REPORT_MODE  = false;
    private static final boolean DEFAULT_READ_REPORT_MODE      = false;
    private static final long    DEFAULT_EXPIRY_TIME     = 7 * 24 * 60 * 60;
    private static final int     DEFAULT_PRIORITY        = PduHeaders.PRIORITY_NORMAL;
    private static final String  DEFAULT_MESSAGE_CLASS   = PduHeaders.MESSAGE_CLASS_PERSONAL_STR;

    private static final String DELIVERY_REPORT_PREFERENCE = "delivery_reports";
    private static final String READ_REPORT_PREFERENCE = "read_reports";
    
    /**
     * The {@code content://} style URL for this table.
     */
    public static final Uri CONTENT_URI = Uri.parse("content://mms-sms/");
    
    /**
     * The {@code content://} style URL for this table.
     */
    public static final Uri OUTBOX_CONTENT_URI = Uri.parse("content://mms/outbox");
    
    public static final Uri PENDING_CONTENT_URI = Uri.withAppendedPath(CONTENT_URI, "pending");

    /**
     * The type of transport protocol (MMS or SMS).
     * <P>Type: INTEGER</P>
     */
    public static final String PROTO_TYPE = "proto_type";

    /**
     * The ID of the message to be sent or downloaded.
     * <P>Type: INTEGER (long)</P>
     */
    public static final String MSG_ID = "msg_id";

    /**
     * The type of the message to be sent or downloaded.
     * This field is only valid for MM. For SM, its value is always set to 0.
     * <P>Type: INTEGER</P>
     */
    public static final String MSG_TYPE = "msg_type";

    /**
     * The type of the error code.
     * <P>Type: INTEGER</P>
     */
    public static final String ERROR_TYPE = "err_type";

    /**
     * The error code of sending/retrieving process.
     * <P>Type: INTEGER</P>
     */
    public static final String ERROR_CODE = "err_code";

    /**
     * How many times we tried to send or download the message.
     * <P>Type: INTEGER</P>
     */
    public static final String RETRY_INDEX = "retry_index";

    /**
     * The time to do next retry.
     * <P>Type: INTEGER (long)</P>
     */
    public static final String DUE_TIME = "due_time";

    /**
     * The time we last tried to send or download the message.
     * <P>Type: INTEGER (long)</P>
     */
    public static final String LAST_TRY = "last_try";

    public MmsMessageSender(Context context, Uri location, long messageSize) {
        mContext = context;
        mMessageUri = location;
        mMessageSize = messageSize;

        if (mMessageUri == null) {
            throw new IllegalArgumentException("Null message URI.");
        }
    }

    public boolean sendMessage(long token) throws Throwable {
        // Load the MMS from the message uri_path
        PduPersister p = PduPersister.getPduPersister(mContext);
        GenericPdu pdu = p.load(mMessageUri);

        if (pdu.getMessageType() != PduHeaders.MESSAGE_TYPE_SEND_REQ) {
            throw new MmsException("Invalid message: " + pdu.getMessageType());
        }

        SendReq sendReq = (SendReq) pdu;

        // Update headers.
        updatePreferencesHeaders(sendReq);

        // MessageClass.
        sendReq.setMessageClass(DEFAULT_MESSAGE_CLASS.getBytes());

        // Update the 'date' field of the message before sending it.
        sendReq.setDate(System.currentTimeMillis() / 1000L);

        sendReq.setMessageSize(mMessageSize);

        p.updateHeaders(mMessageUri, sendReq);

        long messageId = ContentUris.parseId(mMessageUri);

        // Move the message into MMS Outbox.
        if (!mMessageUri.toString().startsWith(Uri.parse("content://mms/drafts").toString())) {
            try {
                // If the message is already in the outbox (most likely because we created a "primed"
                // message in the outbox when the user hit send), then we have to manually put an
                // entry in the pending_msgs table which is where TransacationService looks for
                // messages to send. Normally, the entry in pending_msgs is created by the trigger:
                // insert_mms_pending_on_update, when a message is moved from drafts to the outbox.
                ContentValues values = new ContentValues(7);

                values.put(PROTO_TYPE, 1);
                values.put(MSG_ID, messageId);
                values.put(MSG_TYPE, pdu.getMessageType());
                values.put(ERROR_TYPE, 0);
                values.put(ERROR_CODE, 0);
                values.put(RETRY_INDEX, 0);
                values.put(DUE_TIME, 0);

                Uri uri = SqliteWrapper.insert(mContext, mContext.getContentResolver(),
                		PENDING_CONTENT_URI, values);
            } catch (Throwable e) {
                p.move(mMessageUri, OUTBOX_CONTENT_URI);
            }
        } else {
            p.move(mMessageUri, OUTBOX_CONTENT_URI);
        }

        // Start MMS transaction service
        try {
            SendingProgressTokenManager.put(messageId, token);
            mContext.startService(new Intent(mContext, TransactionService.class));
        } catch (Exception e) {
            throw new Exception("transaction service not registered in manifest");
        }

        return true;
    }

    // Update the headers which are stored in SharedPreferences.
    private void updatePreferencesHeaders(SendReq sendReq) throws MmsException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        // Expiry.
        sendReq.setExpiry(DEFAULT_EXPIRY_TIME);

        // Priority.
        sendReq.setPriority(DEFAULT_PRIORITY);

        // Delivery report.
        boolean dr = prefs.getBoolean(DELIVERY_REPORT_PREFERENCE,
                        DEFAULT_DELIVERY_REPORT_MODE);
        sendReq.setDeliveryReport(dr?PduHeaders.VALUE_YES:PduHeaders.VALUE_NO);

        // Read report.
        boolean rr = prefs.getBoolean(READ_REPORT_PREFERENCE,
                // default to delivery report value if read report not available
                prefs.getBoolean(DELIVERY_REPORT_PREFERENCE,
                        DEFAULT_READ_REPORT_MODE));
        sendReq.setReadReport(rr?PduHeaders.VALUE_YES:PduHeaders.VALUE_NO);
    }

    public static void sendReadRec(Context context, String to, String messageId, int status) {
        EncodedStringValue[] sender = new EncodedStringValue[1];
        sender[0] = new EncodedStringValue(to);

        try {
            final ReadRecInd readRec = new ReadRecInd(
                    new EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR.getBytes()),
                    messageId.getBytes(),
                    PduHeaders.CURRENT_MMS_VERSION,
                    status,
                    sender);

            readRec.setDate(System.currentTimeMillis() / 1000);

            boolean group;

            try {
                group = com.victor.android.send_message.Transaction.mSettings.getGroup();
            } catch (Exception e) {
                group = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("group_message", true);
            }

            PduPersister.getPduPersister(context).persist(readRec, Uri.parse("content://mms/outbox"), true,
                    group, null);
            context.startService(new Intent(context, TransactionService.class));
        } catch (InvalidHeaderValueException e) {
            Log.e(TAG, "Invalide header value", e);
        } catch (MmsException e) {
            Log.e(TAG, "Persist message failed", e);
        }
    }
}
