package tk.rabidbeaver.bd37033controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/*
 * sofiaserver: I think in module/main/HandlerMain.smali, functions;
 *   mcuKeyVolUp()
 *   mcuKeyVolDown()
 *   mcuKeyVolMute()
 *   ** probably easiest is to redirect it to the same script as used for key remapping
 *   and then to send the broadcast from there.
 */
public class CommandReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent){
        // Just RELAY the Intent's Action to the SoundService, which can sort it out.
        Intent service = new Intent(context, SoundService.class);
        service.setAction(intent.getAction());
        context.startService(service);

        /* Do like this in other program to send commands:
        Intent intent1 = new Intent();
        intent1.setAction("tk.rabidbeaver.bd37033controller.PHONE_ON");
        context.sendBroadcast(intent1);*/

        /* Do like this to send broadcast from terminal;
        am broadcast -a tk.rabidbeaver.bd37033controller.VOL_UP
        am broadcast -a tk.rabidbeaver.bd37033controller.VOL_DOWN
        am broadcast -a tk.rabidbeaver.bd37033controller.MUTE
         */
    }
}
