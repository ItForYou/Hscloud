package com.victor.android.send_message;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.mms.util_alt.SqliteWrapper;

/**
 * Common methods to be used for data connectivity/sending messages ect
 *
 * @author Victor Ju
 */
public class Utils {
    /**
     * characters to compare against when checking for 160 character sending compatibility
     */
    public static final String GSM_CHARACTERS_REGEX = "^[A-Za-z0-9 \\r\\n@Ł$ĽčéůěňÇŘřĹĺ\u0394_\u03A6\u0393\u039B\u03A9\u03A0\u03A8\u03A3\u0398\u039EĆćßÉ!\"#$%&'()*+,\\-./:;<=>?ĄÄÖŃÜ§żäöńüŕ^{}\\\\\\[~\\]|\u20AC]*$";
    private static final String TAG = "Utils";
    public static final int DEFAULT_SUBSCRIPTION_ID = 1;

    /**
     * Gets the current users phone number
     *
     * @param context is the context of the activity or service
     * @return a string of the phone number on the device
     */
    public static String getMyPhoneNumber(Context context) {
        TelephonyManager mTelephonyMgr;
        mTelephonyMgr = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        return mTelephonyMgr.getLine1Number();
    }

    /**
     * Enable mobile connection for a specific address
     *
     * @param address the address to enable
     * @return true for success, else false
     */
    public static void forceMobileConnectionForAddress(ConnectivityManager mConnMgr, String address) {
        //find the host name to route
        String hostName = extractAddressFromUrl(address);
        if (TextUtils.isEmpty(hostName)) hostName = address;

        //create a route for the specified address
        int hostAddress = lookupHost(hostName);
        mConnMgr.requestRouteToHost(ConnectivityManager.TYPE_MOBILE_MMS, hostAddress);
    }

    /**
     * Function for getting the weird auth token used to send or receive google voice messages
     *
     * @param account is the string of the account name to get the auth token for
     * @param context is the context of the activity or service
     * @return a string of the auth token to be saved for later
     * @throws java.io.IOException
     * @throws android.accounts.OperationCanceledException
     * @throws android.accounts.AuthenticatorException
     */
    public static String getAuthToken(String account, Context context) throws IOException, OperationCanceledException, AuthenticatorException {
        Bundle bundle = AccountManager.get(context).getAuthToken(new Account(account, "com.google"), "grandcentral", true, null, null).getResult();
        return bundle.getString(AccountManager.KEY_AUTHTOKEN);
    }

    /**
     * This method extracts from address the hostname
     *
     * @param url eg. http://some.where.com:8080/sync
     * @return some.where.com
     */
    public static String extractAddressFromUrl(String url) {
        String urlToProcess = null;

        //find protocol
        int protocolEndIndex = url.indexOf("://");
        if (protocolEndIndex > 0) {
            urlToProcess = url.substring(protocolEndIndex + 3);
        } else {
            urlToProcess = url;
        }

        // If we have port number in the address we strip everything
        // after the port number
        int pos = urlToProcess.indexOf(':');
        if (pos >= 0) {
            urlToProcess = urlToProcess.substring(0, pos);
        }

        // If we have resource location in the address then we strip
        // everything after the '/'
        pos = urlToProcess.indexOf('/');
        if (pos >= 0) {
            urlToProcess = urlToProcess.substring(0, pos);
        }

        // If we have ? in the address then we strip
        // everything after the '?'
        pos = urlToProcess.indexOf('?');
        if (pos >= 0) {
            urlToProcess = urlToProcess.substring(0, pos);
        }
        return urlToProcess;
    }

    /**
     * Transform host name in int value used by ConnectivityManager.requestRouteToHost
     * method
     *
     * @param hostname
     * @return -1 if the host doesn't exists, elsewhere its translation
     * to an integer
     */
    public static int lookupHost(String hostname) {
        InetAddress inetAddress;

        try {
            inetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            return -1;
        }

        byte[] addrBytes;
        int addr;
        addrBytes = inetAddress.getAddress();
        addr = ((addrBytes[3] & 0xff) << 24)
                | ((addrBytes[2] & 0xff) << 16)
                | ((addrBytes[1] & 0xff) << 8)
                | (addrBytes[0] & 0xff);

        return addr;
    }

    /**
     * Ensures that the host MMSC is reachable
     *
     * @param context is the context of the activity or service
     * @param url     is the MMSC to check
     * @param proxy   is the proxy of the APN to check
     * @throws java.io.IOException when route cannot be established
     */
    public static void ensureRouteToHost(Context context, String url, String proxy) throws IOException {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        InetAddress inetAddr;
        if (proxy != null && proxy.trim().length() != 0) {
            try {
                inetAddr = InetAddress.getByName(proxy);
            } catch (UnknownHostException e) {
                throw new IOException("Cannot establish route for " + url + " : Unknown proxy " + proxy);
            }
            if (connMgr != null && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                try {
                    Method requestRoute = ConnectivityManager.class.getMethod("requestRouteToHostAddress", Integer.TYPE, InetAddress.class);
                    if (!((Boolean) requestRoute.invoke(connMgr, ConnectivityManager.TYPE_MOBILE_MMS, inetAddr))) {
                        throw new IOException("Cannot establish route to proxy " + inetAddr);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Cannot establishh route to proxy " + inetAddr, e);
                }
            }
        } else {
            Uri uri = Uri.parse(url);
            try {
                inetAddr = InetAddress.getByName(uri.getHost());
            } catch (UnknownHostException e) {
                throw new IOException("Cannot establish route for " + url + ": Unknown host");
            }
            if (connMgr != null && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                try {
                    Method requestRoute = ConnectivityManager.class.getMethod("requestRouteToHostAddress", Integer.TYPE, InetAddress.class);
                    if (!((Boolean) requestRoute.invoke(connMgr, ConnectivityManager.TYPE_MOBILE_MMS, inetAddr))) {
                        throw new IOException("Cannot establish route to proxy " + inetAddr);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Cannot establishh route to proxy " + inetAddr + " for " + url, e);
                }
            }
        }
    }

    /**
     * Checks whether or not mobile data is enabled and returns the result
     *
     * @param context is the context of the activity or service
     * @return true if data is enabled or false if disabled
     */
    public static Boolean isMobileDataEnabled(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        try {
            Class<?> c = Class.forName(cm.getClass().getName());
            Method m = c.getDeclaredMethod("getMobileDataEnabled");
            m.setAccessible(true);
            return (Boolean) m.invoke(cm);
        } catch (Exception e) {
            Log.e(TAG, "exception thrown", e);
            return null;
        }
    }

    /**
     * Checks mobile data enabled based on telephonymanager
     *
     * @param telephonyManager the telephony manager
     */
    public static boolean isDataEnabled(TelephonyManager telephonyManager) {
        try {
            Method getMobileDataEnabledMethod = telephonyManager.getClass().getDeclaredMethod("getDataEnabled");
            if (null != getMobileDataEnabledMethod)
            {
                boolean mobileDataEnabled = (Boolean) getMobileDataEnabledMethod.invoke(telephonyManager);
                return mobileDataEnabled;
            }
            else
                return false;
        } catch (Exception e) {
            Log.e(TAG, "exception thrown", e);
            return false;
        }
    }

    /**
     * Checks mobile data enabled based on telephonymanager and sim card
     *
     * @param telephonyManager the telephony manager
     * @param subId the sim card id
     */
    public static boolean isDataEnabled(TelephonyManager telephonyManager, int subId) {
        try {
            Method getMobileDataEnabledMethod = telephonyManager.getClass().getDeclaredMethod("getDataEnabled");
            if (null != getMobileDataEnabledMethod)
            {
                boolean mobileDataEnabled = (Boolean) getMobileDataEnabledMethod.invoke(telephonyManager, subId);
                return mobileDataEnabled;
            }
            else
                return false;
        } catch (Exception e) {
            Log.e(TAG, "exception thrown", e);
            return isDataEnabled(telephonyManager);
        }
    }

    /**
     * Toggles mobile data
     *
     * @param context is the context of the activity or service
     * @param enabled is whether to enable or disable data
     */
    public static void setMobileDataEnabled(Context context, boolean enabled) {
        String methodName;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                Class conmanClass = Class.forName(conman.getClass().getName());
                Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
                iConnectivityManagerField.setAccessible(true);
                Object iConnectivityManager = iConnectivityManagerField.get(conman);
                Class iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
                Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
                setMobileDataEnabledMethod.setAccessible(true);

                setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);
            } catch (Exception e) {
                Log.e(TAG, "exception thrown", e);
            }
        } else {
            // TODO find a better way to do this on lollipop!
            // This will actually not work due to no permission for android.permission.MODIFY_PHONE_STATE, which
            // is a system level permission and cannot be accessed for third party apps.
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                Class c = Class.forName(tm.getClass().getName());
                Method m = c.getDeclaredMethod("getITelephony");
                m.setAccessible(true);
                Object telephonyService = m.invoke(tm);
                c = Class.forName(telephonyService.getClass().getName());
                m = c.getDeclaredMethod("setDataEnabled", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(telephonyService, enabled);
            } catch (Exception e) {
                Log.e(TAG, "error enabling data on lollipop", e);
            }   
        }

    }

    /**
     * Gets the number of pages in the SMS based on settings and the length of string
     *
     * @param settings is the settings object to check against
     * @param text     is the text from the message object to be sent
     * @return the number of pages required to hold message
     */
    public static int getNumPages(Settings settings, String text) {
        if (settings.getStripUnicode()) {
            text = StripAccents.stripAccents(text);
        }

        int[] data = SmsMessage.calculateLength(text, false);
        return data[0];
    }

    /**
     * Gets the current thread_id or creates a new one for the given recipient
     * @param context is the context of the activity or service
     * @param recipient is the person message is being sent to
     * @return the thread_id to use in the database
     */
    public static long getOrCreateThreadId(Context context, String recipient) {
        Set<String> recipients = new HashSet<String>();

        recipients.add(recipient);
        return getOrCreateThreadId(context, recipients);
    }

    /**
     * Gets the current thread_id or creates a new one for the given recipient
     * @param context is the context of the activity or service
     * @param recipients is the set of people message is being sent to
     * @return the thread_id to use in the database
     */
    public static long getOrCreateThreadId(
            Context context, Set<String> recipients) {
        Uri.Builder uriBuilder = Uri.parse("content://mms-sms/threadID").buildUpon();

        for (String recipient : recipients) {
            if (isEmailAddress(recipient)) {
                recipient = extractAddrSpec(recipient);
            }

            uriBuilder.appendQueryParameter("recipient", recipient);
        }

        Uri uri = uriBuilder.build();
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                uri, new String[]{"_id"}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long id = cursor.getLong(0);
                    cursor.close();
                    return id;
                } else {

                }
            } finally {
                cursor.close();
            }
        }

        Random random = new Random();
        return random.nextLong();
        //throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
    }

    public static boolean doesThreadIdExist(Context context, long threadId) {
        Uri uri = Uri.parse("content://mms-sms/conversations/" + threadId + "/");

        Cursor cursor = context.getContentResolver().query(uri, new String[] {"_id"}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            cursor.close();
            return true;
        } else {
            return false;
        }
    }

    private static boolean isEmailAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return false;
        }

        String s = extractAddrSpec(address);
        Matcher match = EMAIL_ADDRESS_PATTERN.matcher(s);
        return match.matches();
    }

    private static final Pattern EMAIL_ADDRESS_PATTERN
            = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-]{1,256}" +
                    "\\@" +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                    "(" +
                    "\\." +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                    ")+"
    );

    private static final Pattern NAME_ADDR_EMAIL_PATTERN =
            Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*");

    private static String extractAddrSpec(String address) {
        Matcher match = NAME_ADDR_EMAIL_PATTERN.matcher(address);

        if (match.matches()) {
            return match.group(2);
        }
        return address;
    }

    /**
     * Gets the default settings from a shared preferences file associated with your app
     * @param context is the context of the activity or service
     * @return the settings object to send with
     */
    public static Settings getDefaultSendSettings(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Settings sendSettings = new Settings();

        sendSettings.setMmsc(sharedPrefs.getString("mmsc_url", ""));
        sendSettings.setProxy(sharedPrefs.getString("mms_proxy", ""));
        sendSettings.setPort(sharedPrefs.getString("mms_port", ""));
        sendSettings.setAgent(sharedPrefs.getString("mms_agent", ""));
        sendSettings.setUserProfileUrl(sharedPrefs.getString("mms_user_agent_profile_url", ""));
        sendSettings.setUaProfTagName(sharedPrefs.getString("mms_user_agent_tag_name", ""));
        sendSettings.setGroup(sharedPrefs.getBoolean("group_message", true));
        sendSettings.setDeliveryReports(sharedPrefs.getBoolean("delivery_reports", false));
        sendSettings.setSplit(sharedPrefs.getBoolean("split_sms", false));
        sendSettings.setSplitCounter(sharedPrefs.getBoolean("split_counter", false));
        sendSettings.setStripUnicode(sharedPrefs.getBoolean("strip_unicode", false));
        sendSettings.setSignature(sharedPrefs.getString("signature", ""));
        sendSettings.setSendLongAsMms(true);
        sendSettings.setSendLongAsMmsAfter(3);
        sendSettings.setAccount(null);
        sendSettings.setRnrSe(null);

        return sendSettings;
    }

    /**
     * Determines whether or not the user has Android 4.4 KitKat
     * @return true if version code on device is >= kitkat
     */
    public static boolean hasKitKat() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * Determines whether or not the app is the default SMS app on a device
     * @param context
     * @return true if app is default
     */
    @SuppressLint("NewApi")
    public static boolean isDefaultSmsApp(Context context) {
        if (hasKitKat()) {
            return context.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(context));
        }

        return true;
    }

    /**
     * Determins whether or not the app has enabled MMS over WiFi
     * @param context
     * @return true if enabled
     */
    public static boolean isMmsOverWifiEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("mms_over_wifi", false);
    }

    @SuppressLint("NewApi")
    public static int getDefaultSubscriptionId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return SmsManager.getDefaultSmsSubscriptionId();
        } else {
            return DEFAULT_SUBSCRIPTION_ID;
        }
    }
}
