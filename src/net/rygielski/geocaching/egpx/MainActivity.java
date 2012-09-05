package net.rygielski.geocaching.egpx;

import java.io.File;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

public class MainActivity extends Activity
{
	private static final String LOG_TAG = "egpx";
	
	private Button btnStart;
	private EditText textSaveAs;
	private EditText textLat;
	private EditText textLon;
	private EditText textLimit;
	private EditText textUser;
	private RadioButton radioSkipped;
	private RadioButton radioMarked;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.main);
		this.btnStart = (Button) this.findViewById(R.id.btnStart);
		this.textLat = (EditText) this.findViewById(R.id.editText1);
		this.textLon = (EditText) this.findViewById(R.id.editText2);
		this.textSaveAs = (EditText) this.findViewById(R.id.editSaveAs);
		this.textLimit = (EditText) this.findViewById(R.id.editText4);
		this.textUser = (EditText) this.findViewById(R.id.editUser);
		this.radioSkipped = (RadioButton) this.findViewById(R.id.radioSkipped);
		this.radioMarked = (RadioButton) this.findViewById(R.id.radioMarked);
		this.btnStart.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (MainActivity.this.checkCard())
				{
					MainActivity.this.runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							File outputFile = new File(MainActivity.this.textSaveAs.getText().toString());
							Runnable onSuccess = new Runnable()
							{
								@Override
								public void run()
								{
									MainActivity.this.runOnUiThread(new Runnable()
									{
										@Override
										public void run()
										{
											Toast.makeText(MainActivity.this, "Plik zapisany pomyślnie.",
													Toast.LENGTH_SHORT).show();
										}
									});
								}
							};
							Runnable onFailure = new Runnable()
							{
								@Override
								public void run()
								{
								}
							};
							new DownloadTask(MainActivity.this, outputFile, MainActivity.this.textLat
									.getText().toString(), MainActivity.this.textLon.getText().toString(),
									MainActivity.this.textLimit.getText().toString(),
									MainActivity.this.textUser.getText().toString(),
									MainActivity.this.radioSkipped.isChecked() ? "skip" : "mark",
									"http://opencaching.pl/okapi/", onSuccess, onFailure)
									.execute((Void) null);
						}
					});
				}
			}
		});
		
		final LocationManager locman = (LocationManager) MainActivity.this
				.getSystemService(Context.LOCATION_SERVICE);
		locman.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1000, new LocationListener()
		{
			@Override
			public void onStatusChanged(final String provider, final int status, final Bundle extras)
			{
			}
			
			@Override
			public void onProviderEnabled(final String provider)
			{
			}
			
			@Override
			public void onProviderDisabled(final String provider)
			{
			}
			
			@Override
			public void onLocationChanged(final Location location)
			{
				MainActivity.this.textLat.setText(Double.toString(location.getLatitude()));
				MainActivity.this.textLon.setText(Double.toString(location.getLongitude()));
				locman.removeUpdates(this);
			}
		});
		if (this.checkCard())
		{
			File storageDir = Environment.getExternalStorageDirectory();
			this.textSaveAs.setText(storageDir.getAbsolutePath() + "/Garmin/GPX/awaryjny.gpx");
		}
	}
	
	public boolean checkCard()
	{
		String state = Environment.getExternalStorageState();
		
		if (Environment.MEDIA_MOUNTED.equals(state))
			return true;
		
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				Toast t = Toast.makeText(MainActivity.this, "Uwaga: brak dostępu do karty pamięci.",
						Toast.LENGTH_LONG);
				t.show();
			}
		});
		return false;
	}
	
	public static String getPref(final Context context, final String key, final String defValue)
	{
		return context.getSharedPreferences("egpx", 0).getString(key, defValue);
	}
	
	public static void setPref(final Context context, final String key, final String value)
	{
		Editor editor = context.getSharedPreferences("egpx", 0).edit();
		editor.putString(key, value);
		editor.commit();
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		setPref(this, "user", this.textUser.getText().toString());
		setPref(this, "saveAs", this.textSaveAs.getText().toString());
		setPref(this, "limit", this.textLimit.getText().toString());
		setPref(this, "when_found", (this.radioSkipped.isChecked() ? "skip" : "mark"));
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		this.textUser.setText(getPref(this, "user", ""));
		this.textSaveAs.setText(getPref(this, "saveAs", "/Garmin/GPX/awaryjny.gpx"));
		this.textLimit.setText(getPref(this, "limit", "500"));
		if (getPref(this, "when_found", "mark") == "skip")
		{
			this.radioSkipped.setChecked(true);
		}
		else
		{
			this.radioMarked.setChecked(true);
		}
	}
	
}