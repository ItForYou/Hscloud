package com.victor.mms;


public interface ISendAsyncTask {
	public void doAction(SendAsyncTask task);
	public void onComplete();
    public void onPostExecute(Void result);
}
