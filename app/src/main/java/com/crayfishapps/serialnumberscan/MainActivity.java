package com.crayfishapps.serialnumberscan;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;

import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CompoundBarcodeView;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private CompoundBarcodeView barcodeView;

    private String username;
    private String password;

    private void checkAxis(String serialNumber) {

        String stringUrl = "https://cst.axis.com/widgets/product.cgi?sn=" + serialNumber + "&submit=Check";
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            new DownloadWebpageTask().execute(stringUrl);
        } else {
            Toast.makeText(getApplicationContext(), "No network connection available.", Toast.LENGTH_LONG).show();
        }
    }

    private class DownloadWebpageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            try {
                return downloadUrl(urls[0]);
            } catch (IOException e) {
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {

            int start, end;

            // parse the results from the Axis Product Widget
            if (result.length() > 2000) {
                int posSerialNumber = result.indexOf("Serial number", 2000);
                int posPartDescription = result.indexOf("Part description");
                int posPartNumber = result.indexOf("Part number");
                int posProductWarranty = result.indexOf("Product warranty");
                int posEOW = result.indexOf("EOW date");

                start = result.indexOf(">", posSerialNumber) + 1;
                end = result.indexOf("<", start);
                String toastText = "Serial number: " + result.substring(start, end) + "\n";

                start = result.indexOf(">", posPartDescription) + 1;
                end = result.indexOf("<", start);
                toastText += "Part description: "  + result.substring(start, end) + "\n";

                start = result.indexOf(">", posPartNumber) + 1;
                end = result.indexOf("<", start);
                toastText += "Part number: " + result.substring(start, end) + "\n";

                start = result.indexOf(">", posProductWarranty) + 1;
                end = result.indexOf("<", start);
                toastText += "Product warranty: " + result.substring(start, end) + " years\n";

                start = result.indexOf(">", posEOW) + 1;
                end = result.indexOf("<", start);
                toastText += "EOW date: " + result.substring(start, end);

                Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(getApplicationContext(), "Could not retrieve camera details", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String downloadUrl(String myurl) throws IOException {
        InputStream is = null;
        int len = 3000;

        try {
            URL url = new URL(myurl);
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);

            conn.connect();
            is = conn.getInputStream();

            String contentAsString = readIt(is, len);
            return contentAsString;

        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    // Reads an InputStream and converts it to a String.
    public String readIt(InputStream stream, int len) throws IOException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }

    private BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() != null) {
                barcodeView.setStatusText(result.getText());

                String serialNumber = result.getText().substring(0, 12).toUpperCase();
                if (serialNumber.startsWith("0040") || serialNumber.startsWith("ACCC")) {
                    Toast.makeText(getApplicationContext(), serialNumber, Toast.LENGTH_LONG).show();
                    checkAxis(serialNumber);
                }
            }
            //Added preview of scanned barcode
            ImageView imageView = (ImageView) findViewById(R.id.barcodePreview);
            imageView.setImageBitmap(result.getBitmapWithResultPoints(Color.YELLOW));
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // setup action bar to show logo and title bar
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);

        barcodeView = (CompoundBarcodeView) findViewById(R.id.barcode_scanner);
        barcodeView.decodeContinuous(callback);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        username = preferences.getString("preference_username", "");
        password = preferences.getString("preference_password", "");

        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        barcodeView.pause();
    }

    public void pause(View view) {
        barcodeView.pause();
    }

    public void resume(View view) {
        barcodeView.resume();
    }

    public void triggerScan(View view) {
        barcodeView.decodeSingle(callback);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }
}