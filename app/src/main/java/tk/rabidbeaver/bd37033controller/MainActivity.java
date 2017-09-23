package tk.rabidbeaver.bd37033controller;

import android.app.Activity;

import android.content.Intent;
import android.os.Bundle;
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent service = new Intent(MainActivity.this, SoundService.class);
        service.setAction("start");
        startService(service);
        finish();

    }

}
