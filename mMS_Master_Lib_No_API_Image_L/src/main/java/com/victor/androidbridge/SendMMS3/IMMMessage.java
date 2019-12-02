package com.victor.androidbridge.SendMMS3;

import com.victor.androidbridge.nokia.MMMessage;

public interface IMMMessage {
	public MMMessage 	getMMMessage();
	public MMMessage	getMMMessage(int index);
	public void			removeAll();
	public void			remove(int index);
	public void			add(MMMessage msg);
	public int          getMMMessageSize();
}
