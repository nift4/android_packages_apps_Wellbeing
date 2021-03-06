package org.eu.droid_ng.wellbeing;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.widget.Toast;

import java.util.function.Consumer;

// Helper to connect to WellbeingStateHost
public class WellbeingStateClient {
	// Our context
	private final Context context;

	// Don't attempt to unbind from the service unless the client has received some
	// information about the service's state.
	private boolean mShouldUnbind;

	// To invoke the bound service, first make sure that this value
	// is not null.
	private WellbeingStateHost mBoundService;

	// Callback when service is connected
	private Consumer<WellbeingStateHost> callback;

	// Connection callback utility
	private final ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			try {
				mBoundService = ((WellbeingStateHost.LocalBinder) service).getService();
			} catch (ClassCastException ignored) {
				Toast.makeText(context, "Assertion failure (0xAE): Service is in another process. Please report this to the developers!",
						Toast.LENGTH_SHORT).show();
				return;
			}
			WellbeingStateClient.this.callback.accept(mBoundService);
		}

		public void onServiceDisconnected(ComponentName className) {
			mBoundService = null;
		}

		/*@Override
		public void onNullBinding(ComponentName name) {
			Toast.makeText(context, "Assertion failure (0xAF): Service is null. Please report this to the developers!",
					Toast.LENGTH_SHORT).show();
		}*/
	};

	public WellbeingStateClient(Context context) {
		this.context = context.getApplicationContext();
	}

	@SuppressWarnings("deprecation") //backward compatibility does what we want
	public boolean isServiceRunning() {
		ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (WellbeingStateHost.class.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	public boolean doBindService(Consumer<WellbeingStateHost> callback, boolean canHandleFailure, boolean maybeStartService, boolean lateNotify) {
		this.callback = callback;
		if (mBoundService != null) {
			callback.accept(mBoundService);
			return true;
		}

		if (isServiceRunning() && context.bindService(new Intent(context, WellbeingStateHost.class),
				mConnection, Context.BIND_IMPORTANT)) {
			mShouldUnbind = true;
			return true;
		} else {
			if (maybeStartService) {
				startService(lateNotify);
				if (doBindService(callback, true, false, lateNotify)) {
					return true;
				} else if (!canHandleFailure) {
					Toast.makeText(context, "Assertion failure (0xAA): Failed to start service. Please report this to the developers!",
							Toast.LENGTH_SHORT).show();
				}
			} else if (!canHandleFailure) {
				Toast.makeText(context, "Assertion failure (0xAD): Failed to find service. Please report this to the developers!",
						Toast.LENGTH_SHORT).show();
			}
			return false;
		}
	}

	public boolean doBindService(Consumer<WellbeingStateHost> callback, boolean canHandleFailure) {
		return doBindService(callback, canHandleFailure, false, false);
	}

	public void doBindService(Consumer<WellbeingStateHost> callback) {
		doBindService(callback, false);
	}

	public void doUnbindService() {
		if (mShouldUnbind) {
			// Release information about the service's state.
			context.unbindService(mConnection);
			mShouldUnbind = false;
		}
	}

	public void startService(boolean lateNotify) {
		Intent i = new Intent(context, WellbeingStateHost.class);
		i.putExtra("lateNotify", lateNotify);
		context.startForegroundService(i);
	}

	public void startService() {
		startService(false);
	}

	public void killService() {
		context.stopService(new Intent(context, WellbeingStateHost.class));
	}
}
