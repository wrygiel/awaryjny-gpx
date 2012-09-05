package net.rygielski.geocaching.egpx;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class DownloadTask extends AsyncTask<Void, String, Void>
{
	private static final String LOG_TAG = "egpx";
	private final Context context;
	private final File outputFile;
	private ProgressDialog dialog;
	private String cancelMessage = "Anulowano wykonanie";
	private final String lat;
	private final String lon;
	private final String limit;
	private final String okapi_base_url;
	private final Runnable onSuccess;
	private final Runnable onFailure;
	private final String user;
	private final String when_found;
	
	public DownloadTask(final Context c, final File outputFile, final String lat, final String lon,
			final String limit, final String user, final String when_found, final String okapi_base_url,
			final Runnable onSuccess, final Runnable onFailure)
	{
		this.context = c;
		this.outputFile = outputFile;
		this.lat = lat;
		this.lon = lon;
		this.limit = limit;
		this.user = user;
		this.okapi_base_url = okapi_base_url;
		this.onSuccess = onSuccess;
		this.onFailure = onFailure;
		this.when_found = when_found;
	}
	
	@Override
	protected void onPreExecute()
	{
		this.dialog = ProgressDialog.show(this.context, "Awaryjny GPX", "Przygotowuję...", true, true);
		this.dialog.setOnCancelListener(new ProgressDialog.OnCancelListener()
		{
			@Override
			public void onCancel(final DialogInterface dialog)
			{
				DownloadTask.this.cancel(true);
			}
		});
	}
	
	@Override
	protected void onProgressUpdate(final String... values)
	{
		this.dialog.setMessage(values[0]);
	}
	
	@Override
	protected void onCancelled()
	{
		this.dialog.hide();
		Toast.makeText(this.context, this.cancelMessage, Toast.LENGTH_LONG).show();
		this.onFailure.run();
	}
	
	@Override
	protected void onPostExecute(final Void result)
	{
		this.dialog.hide();
		this.onSuccess.run();
		// Toast t = Toast.makeText(this.context, "Plik zapisany pomyślnie.",
		// Toast.LENGTH_SHORT);
		// t.show();
	}
	
	@Override
	protected Void doInBackground(final Void... arg0)
	{
		URL u;
		OutputStream output;
		String user_uuid = null;
		
		if (this.user.length() > 0)
		{
			this.publishProgress("Przygotowuję parametry...");
			
			try
			{
				String resp = this.getUrl(this.okapi_base_url + "services/users/by_username" + "?username="
						+ URLEncoder.encode(this.user) + "&fields=uuid&consumer_key=YOUR-KEY");
				Log.v(LOG_TAG, resp);
				if (resp.startsWith("{\"uuid\":\""))
					user_uuid = resp.substring(9, 9 + 36);
			}
			catch (MalformedURLException e1)
			{
				this.cancelMessage = "Nieznany błąd.";
				this.cancel(false);
				return null;
			}
			catch (IOException e)
			{
				Log.e(LOG_TAG, Log.getStackTraceString(e));
				this.cancelMessage = "Błąd połączenia z serwerem.";
				this.cancel(false);
				return null;
			}
			catch (URISyntaxException e)
			{
				Log.e(LOG_TAG, Log.getStackTraceString(e));
				this.cancelMessage = "Błąd połączenia z serwerem.";
				this.cancel(false);
				return null;
			}
		}
		
		File parentDir = this.outputFile.getParentFile();
		if (parentDir == null)
		{
			this.cancelMessage = "Niepoprawna ścieżka.";
			this.cancel(false);
			return null;
		}
		if (!parentDir.exists())
		{
			if (!parentDir.mkdirs())
			{
				this.cancelMessage = "Nie udało się stworzyć katalogu " + parentDir.toString();
				this.cancel(false);
				return null;
			}
		}
		
		try
		{
			/*
			 * File.setWritable is available since API level 9, but we need to
			 * make file readable to ALL and we're using API level 7. We MAY
			 * skip the requirement for API 9 *if* the file is contained in our
			 * getFilesDir.
			 */
			
			if (this.outputFile.getParentFile().equals(this.context.getFilesDir()))
			{
				Log.v(LOG_TAG, "openFileOutput mode");
				output = new BufferedOutputStream(this.context.openFileOutput(this.outputFile.getName(),
						Context.MODE_WORLD_READABLE));
			}
			else
			{
				Log.v(LOG_TAG, "new FileOutputStream mode");
				output = new BufferedOutputStream(new FileOutputStream(this.outputFile));
			}
		}
		catch (FileNotFoundException e1)
		{
			this.cancelMessage = "Problem z otwarciem pliku do zapisu.";
			this.cancel(false);
			return null;
		}
		this.publishProgress("Pobieram GPX...");
		String center;
		try
		{
			Double lat = Location.convert(this.lat);
			Double lon = Location.convert(this.lon);
			center = Double.toString(lat) + "|" + Double.toString(lon);
		}
		catch (IllegalArgumentException e)
		{
			this.cancelMessage = "Niepoprawny format współrzędnych geograficznych.";
			this.cancel(false);
			return null;
		}
		Integer intlimit;
		try
		{
			intlimit = Integer.parseInt(this.limit);
			if (intlimit < 1 || intlimit > 500)
				throw new NumberFormatException();
		}
		catch (NumberFormatException e)
		{
			this.cancelMessage = "Niepoprawna wartość limitu (od 1 do 500).";
			this.cancel(false);
			return null;
		}
		
		try
		{
			u = new URL(
					this.okapi_base_url
							+ "services/caches/shortcuts/search_and_retrieve"
							+ "?search_method=services/caches/search/nearest"
							+ "&search_params="
							+ URLEncoder
									.encode("{\"center\":\""
											+ center
											+ "\",\"limit\":\""
											+ intlimit
											+ "\""
											+ (((user_uuid != null) && (this.when_found == "skip")) ? ",\"not_found_by\":\""
													+ user_uuid + "\""
													: "") + "}")
							+ "&retr_method=services/caches/formatters/gpx"
							+ "&retr_params="
							+ URLEncoder
									.encode("{\"langpref\":\"pl|en\", \"ns_ground\":\"true\", \"ns_gsak\":\"true\", "
											+ "\"ns_ox\":\"true\", \"latest_logs\":\"true\", \"images\":\"descrefs:all\", "
											+ "\"trackables\":\"desc:list\", \"recommendations\":\"desc:count\", \"lpc\":\"all\""
											+ (((user_uuid != null) && (this.when_found == "mark")) ? ",\"user_uuid\":\""
													+ user_uuid + "\",\"mark_found\":\"true\""
													: "") + "}")
							+ "&wrap=false&consumer_key=YOUR-KEY");
			Log.v(LOG_TAG, u.toString());
		}
		catch (MalformedURLException e1)
		{
			this.cancelMessage = "Nieznany błąd.";
			this.cancel(false);
			return null;
		}
		
		try
		{
			HttpClient httpClient = new DefaultHttpClient();
			HttpGet pageGet = new HttpGet(u.toURI());
			HttpResponse response = httpClient.execute(pageGet);
			InputStream input = response.getEntity().getContent();
			long length = response.getEntity().getContentLength();
			// InputStream input = u.openStream();
			this.copy(input, output, length, true);
			output.close();
		}
		catch (IOException e)
		{
			Log.e(LOG_TAG, Log.getStackTraceString(e));
			this.cancelMessage = "Błąd podczas pobierania (lub zapisywania) pliku.";
			this.cancel(false);
			return null;
		}
		catch (URISyntaxException e)
		{
			Log.e(LOG_TAG, Log.getStackTraceString(e));
			this.cancelMessage = "Błąd podczas pobierania (lub zapisywania) pliku.";
			this.cancel(false);
			return null;
		}
		
		return null;
	}
	
	/**
	 * @param length
	 *            Set negative value if length is unknown.
	 */
	long copy(final InputStream input, final OutputStream output, final long length,
			final boolean publishProgress) throws IOException
	{
		byte[] buffer = new byte[4096];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer)))
		{
			output.write(buffer, 0, n);
			count += n;
			if (publishProgress)
			{
				String message = "Pobieram - " + String.format("%d", count / 1024) + "kB";
				if (length > 0)
					message += " (" + String.format("%d", 100 * count / length) + "%)";
				message += "...";
				this.publishProgress(message);
			}
		}
		return count;
	}
	
	String copyToString(final InputStream input) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		this.copy(input, output, -1, false);
		return output.toString();
	}
	
	String getUrl(final String url) throws URISyntaxException, ClientProtocolException, IOException
	{
		URL u = new URL(url);
		Log.v(LOG_TAG, u.toString());
		HttpClient httpClient = new DefaultHttpClient();
		HttpGet pageGet = new HttpGet(u.toURI());
		HttpResponse response = httpClient.execute(pageGet);
		return this.copyToString(response.getEntity().getContent());
	}
}
