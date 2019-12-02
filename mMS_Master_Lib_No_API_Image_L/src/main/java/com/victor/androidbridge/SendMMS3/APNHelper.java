package com.victor.androidbridge.SendMMS3;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;

import com.victor.androidbridge.SendMMS3.SendMMS.OperatorName;

public class APNHelper {
	private OperatorName operatorName = OperatorName.SK;
	
	public class APN {
	    public String MMSCenterUrl = "";
	    public String MMSPort = "";
	    public String MMSProxy = ""; 
	    public String MCC = "";
	    public String MNC = "";
	}
 
	public APNHelper(final Context context, OperatorName name) {
		this.operatorName = name;
	}   



	public List<APN> getMMSApns() {
		final List<APN> results = new ArrayList<APN>();
		final APN apn = new APN();

		if (operatorName == OperatorName.SK) {
			apn.MMSCenterUrl = "http://omms.nate.com:9082/oma_mms";
			apn.MMSProxy = "lteoma.nate.com";
			apn.MMSPort = "9093";
			apn.MCC = "450";
			apn.MNC = "05";
		} else if (operatorName == OperatorName.KT) {
			apn.MMSCenterUrl = "http://mmsc.ktfwing.com:9082";
			apn.MMSProxy = "";
			apn.MMSPort = "9093";
			apn.MCC = "450";
			apn.MNC = "08";
		} else {
			apn.MMSCenterUrl = "http://omammsc.uplus.co.kr:9084";
			apn.MMSProxy = "";
			apn.MMSPort = "9084";
			apn.MCC = "450";
			apn.MNC = "06";
		}

		results.add(apn);
		return results;
	}
}

final class Carriers implements BaseColumns {
    /**
     * The content:// style URL for this table
     */
    public static final Uri CONTENT_URI =
        Uri.parse("content://telephony/carriers");

    /**
     * The default sort order for this table
     */
    public static final String DEFAULT_SORT_ORDER = "name ASC";

    public static final String NAME = "name";

    public static final String APN = "apn";

    public static final String PROXY = "proxy";

    public static final String PORT = "port";

    public static final String MMSPROXY = "mmsproxy";

    public static final String MMSPORT = "mmsport";

    public static final String SERVER = "server";

    public static final String USER = "user";

    public static final String PASSWORD = "password";

    public static final String MMSC = "mmsc";

    public static final String MCC = "mcc";

    public static final String MNC = "mnc";

    public static final String NUMERIC = "numeric";

    public static final String AUTH_TYPE = "authtype";

    public static final String TYPE = "type";

    public static final String CURRENT = "current";
}