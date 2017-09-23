package tk.rabidbeaver.bd37033controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LoadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent){
        Intent service = new Intent(context, SoundService.class);
        service.setAction("start");
        context.startService(service);
    }
}
