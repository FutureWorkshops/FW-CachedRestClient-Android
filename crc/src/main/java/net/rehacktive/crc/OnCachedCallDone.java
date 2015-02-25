package net.rehacktive.crc;

import net.rehacktive.crc.internal.CacheElement;

public abstract class OnCachedCallDone {
	
	public abstract void onSuccess(CacheElement ret);

    public abstract void onError();

}
