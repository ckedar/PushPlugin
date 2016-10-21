package com.plugin.gcm;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.google.android.gcm.GCMBaseIntentService;
import org.json.JSONException;
import org.json.JSONObject;
import com.rapidue.uzed.R;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	private static final String TAG = "GCMIntentService";

	public GCMIntentService() {
		super("GCMIntentService");
	}

	@Override
	public void onRegistered(Context context, String regId) {

		Log.v(TAG, "onRegistered: "+ regId);

		JSONObject json;

		try
		{
			json = new JSONObject().put("event", "registered");
			json.put("regid", regId);

			Log.v(TAG, "onRegistered: " + json.toString());

			// Send this JSON data to the JavaScript application above EVENT should be set to the msg type
			// In this case this is the registration ID
			PushPlugin.sendJavascript( json );

		}
		catch( JSONException e)
		{
			// No message to the user is sent, JSON failed
			Log.e(TAG, "onRegistered: JSON exception");
		}
	}

	@Override
	public void onUnregistered(Context context, String regId) {
		Log.d(TAG, "onUnregistered - regId: " + regId);
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d(TAG, "onMessage - context: " + context);

		// Extract the payload from the message
		Bundle extras = intent.getExtras();
		if (extras != null)
		{
			sendDeliveryReport(context, intent.getExtras(), "received");

			// if we are in the foreground, just surface the payload, else post it to the statusbar
            if (PushPlugin.isInForeground()) {
				extras.putBoolean("foreground", true);
                PushPlugin.sendExtras(extras);
			}
			else {
				extras.putBoolean("foreground", false);

                // Send a notification if there is a message
                if (extras.getString("message") != null && extras.getString("message").length() != 0) {
                    createNotification(context, extras);
                }
            }
        }
	}

	public void createNotification(Context context, Bundle extras)
	{
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(this);

		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		int defaults = Notification.DEFAULT_ALL;

		if (extras.getString("defaults") != null) {
			try {
				defaults = Integer.parseInt(extras.getString("defaults"));
			} catch (NumberFormatException e) {}
		}

		int uzedBgColor = 0xFF4B42;
		NotificationCompat.Builder mBuilder =
			new NotificationCompat.Builder(context)
				.setDefaults(defaults)
				.setSmallIcon(getNotificationIcon())
				.setWhen(System.currentTimeMillis())
				.setContentTitle(extras.getString("title"))
				.setTicker(extras.getString("title"))
				.setContentIntent(contentIntent)
				.setAutoCancel(true);

		String message = extras.getString("message");
		if (message != null) {
			mBuilder.setContentText(message);
			mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
		} else {
			mBuilder.setContentText("<missing message content>");
		}

		String imageUrl = extras.getString("imageUrl");
		if(imageUrl != null) {
			NotificationCompat.BigPictureStyle notiStyle = new NotificationCompat.BigPictureStyle();
			notiStyle.setSummaryText(message);
			try {
				Bitmap remote_picture = BitmapFactory.decodeStream((InputStream) new URL(imageUrl).getContent());
				notiStyle.bigPicture(remote_picture);
				mBuilder.setStyle(notiStyle);
			} catch (IOException e) {
			}
		}

		String msgcnt = extras.getString("msgcnt");
		if (msgcnt != null) {
			mBuilder.setNumber(Integer.parseInt(msgcnt));
		}
		
		int notId = 0;
		
		try {
			notId = Integer.parseInt(extras.getString("notId"));
		}
		catch(NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
		}
		catch(Exception e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
		}
		
		mNotificationManager.notify((String) appName, notId, mBuilder.build());
	}

	private int getNotificationIcon() {
		boolean useWhiteIcon = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP);
		return useWhiteIcon ? R.drawable.notification_icon : R.drawable.icon;
	}

	private static String getAppName(Context context)
	{
		CharSequence appName = 
				context
					.getPackageManager()
					.getApplicationLabel(context.getApplicationInfo());
		
		return (String)appName;
	}
	
	@Override
	public void onError(Context context, String errorId) {
		Log.e(TAG, "onError - errorId: " + errorId);
	}

	public static void sendDeliveryReport(Context context, Bundle extras, String status) {
		if (extras != null) {
			final String messageId = extras.getString("google.message_id");
			final String deliveryReportURL = extras.getString("delivery_report_url");
			if(deliveryReportURL == null) return;
			String uuid = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
			String versionCode = null;
			try {
				versionCode = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
			} catch (PackageManager.NameNotFoundException e) {
				e.printStackTrace();
			}

			final Uri uri = Uri.parse(deliveryReportURL).buildUpon()
					.appendQueryParameter("status", status)
					.appendQueryParameter("messageId", messageId)
					.appendQueryParameter("uuid", uuid)
					.appendQueryParameter("version", versionCode)
					.build();

			(new CallAPI()).execute(uri.toString());
		}
	}

	public static class CallAPI extends AsyncTask<String,String,String> {
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}
		static String convertStreamToString(java.io.InputStream is) {
			java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
			return s.hasNext() ? s.next() : "";
		}
		@Override
		protected String doInBackground(String... params) {
			try {
				Thread.sleep((long) (10L*1000L*Math.random()));
			} catch (InterruptedException e) {
			}

			String urlString = params[0]; // URL to call
			String resultToDisplay = "";

			InputStream in = null;
			try {
				URL url = new URL(urlString);
				HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.setRequestMethod("POST");
				in = urlConnection.getInputStream();
				resultToDisplay = convertStreamToString(in);
				in.close();
				urlConnection.disconnect();
			} catch (Exception e) {
				System.out.println(e.getMessage());
				return e.getMessage();
			}

			return resultToDisplay;
		}

	}
}
