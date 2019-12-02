package com.victor.mms;

import android.content.Context;
import android.os.AsyncTask;

import com.victor.mms.Utils.messageBody;

public class SendAsyncTask extends AsyncTask<ISendAsyncTask, Integer, Void> {
	private Context 			mContext 			= null;
	private messageBody         messageBody         = null;

    public SendAsyncTask(Context context, messageBody messageBody) {
		this.mContext = context;
		this.messageBody = messageBody;
	}
    
    public messageBody getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(messageBody messageBody) {
        this.messageBody = messageBody;
    }
    
    public void setProgressMaxCnt(int maxCnt) {
    }
    
	public void setProgressCnt(int cnt) {
		publishProgress(cnt);
	}
	@Override
	protected Void doInBackground(ISendAsyncTask... params) {
		if( params[0]!=null ) {
			params[0].doAction(this);
		}
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
		//Toast.makeText(mContext, "Finish Sending Messages!", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onPreExecute() {
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
	}

    public interface OnCompleteListener {
        void onComplete();
    }
	
}
