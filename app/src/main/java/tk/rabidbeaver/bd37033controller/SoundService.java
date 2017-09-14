package tk.rabidbeaver.bd37033controller;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.util.Log;

import com.syu.jni.JniI2c;

public class SoundService extends Service {
    private static final String LOG_TAG = "SoundService";
    private static Notification notification;

    private static int filedes = -1;

    private static boolean mix = false;
    private static boolean phone = false;
    private static int input_select = 0x00;
    private static int input_gain = 0x08;
    private static int mixer_gain = 0;
    private static int master_gain = 19;

    // the BD37033 supports volume gain in the range of -79dB through +15dB
    // interestingly, the chinese implementation only uses the range -54dB through +3dB.
    // There are 37 total steps at offsets 0-36 inclusive.
    private static final int maxvoloff = 36;
    private static final int[] volsteps = {
            0xff, //0 -79dB (mute)
            0xb6, //1 -54dB
            0xb2, //2 -50dB
            0xae, //3 -46dB
            0xaa, //4 -42dB
            0xa6, //5 -38dB
            0xa2, //6 -34dB
            0xa0, //7 -32dB
            0x9e, //8 -30dB
            0x9c, //9 -28dB
            0x9b, //10 -27dB
            0x99, //11 -25dB
            0x97, //12 -23dB
            0x95, //13 -21dB
            0x94, //14 -20dB
            0x93, //15 -19dB
            0x92, //16 -18dB
            0x91, //17 -17dB
            0x90, //18 -16dB
            0x8f, //19 -15dB
            0x8e, //20 -14dB
            0x8c, //21 -12dB
            0x8b, //22 -11dB
            0x8a, //23 -10dB
            0x89, //24 -9dB
            0x88, //25 -8dB
            0x87, //26 -7dB
            0x86, //27 -6dB
            0x85, //28 -5dB
            0x84, //29 -4dB
            0x83, //30 -3dB
            0x82, //31 -2dB
            0x81, //32 -1dB
            0x80, //33 0dB
            0x7f, //34 +1dB
            0x7e, //35 +2dB
            0x7d  //36 +3dB
    };

    private boolean volUp(){
        if (filedes > 0 && master_gain < maxvoloff){
            if (JniI2c.writeRk(filedes, 0x40, 0x20, volsteps[master_gain+1]) == 2){
                master_gain++;
                return true;
            }
            return false;

        }
        return false;
    }

    private boolean volDown(){
        if (filedes > 0 && master_gain > 0){
            if (JniI2c.writeRk(filedes, 0x40, 0x20, volsteps[master_gain-1]) == 2){
                master_gain--;
                return true;
            }
            return false;

        }
        return false;
    }

    private void resetupBD37033(){
        if (filedes > 0) {
            JniI2c.writeRk(filedes, 0x40, 0xfe, 0x81); // system reset
            JniI2c.writeRk(filedes, 0x40, 0x01, 0xe3); // initial setup *1
            JniI2c.writeRk(filedes, 0x40, 0x02, 0x03); // subwoofer setup *2
            JniI2c.writeRk(filedes, 0x40, 0x03, 0x18); // mixer setup *3
            JniI2c.writeRk(filedes, 0x40, 0x41, 0x10); // bass f0 80Hz, Q 0.5
            JniI2c.writeRk(filedes, 0x40, 0x44, 0x11); // mid f0 1kHz, Q 1.0
            JniI2c.writeRk(filedes, 0x40, 0x47, 0x01); // treb f0 7.5 kHz, Q 1.25
            JniI2c.writeRk(filedes, 0x40, 0x75, 0x60); // loudness gain "HICUT4", 0dB
            JniI2c.writeRk(filedes, 0x40, 0x2c, 0x8c); // subwoofer gain -12dB
            JniI2c.writeRk(filedes, 0x40, 0x05, input_select); // input selector A full diff type negative input
            JniI2c.writeRk(filedes, 0x40, 0x06, input_gain); // input gain MUTE OFF, 8dB
            JniI2c.writeRk(filedes, 0x40, 0x51, 0x04); // bass gain BOOST, 4dB
            JniI2c.writeRk(filedes, 0x40, 0x54, 0x00); // mid gain BOOST, 0dB
            JniI2c.writeRk(filedes, 0x40, 0x57, 0x05); // treb gain BOOST, 5dB
            JniI2c.writeRk(filedes, 0x40, 0x29, 0x80); // F2 gain 0dB
            JniI2c.writeRk(filedes, 0x40, 0x28, 0x80); // F1 gain 0dB
            JniI2c.writeRk(filedes, 0x40, 0x2b, 0x80); // R2 gain 0dB
            JniI2c.writeRk(filedes, 0x40, 0x2a, 0x80); // R1 gain 0dB
            JniI2c.writeRk(filedes, 0x40, 0x20, volsteps[master_gain]); // master gain -15dB
            JniI2c.writeRk(filedes, 0x40, 0x30, volsteps[mixer_gain]); // mixer MUTE

            /* Notes:
             * 1: "11100011"
             * "1": Advanced switching (no "pop") ON
             * "1": Anti alias filter ON
             * "10": Advanced switch fader time 11.2 ms
             * "00": ignored
             * "11": Advanced switch muting time 3.2 ms
             * -- extra: Original value was 0xe7, which is invalid.
             *
             * 2: "00000011"
             * "0": LPF Phase 0 degrees
             * "0": Level meter reset HOLD
             * "00": Subwoofer output selector LPF
             * "0": Subwoofer input selector Loudness (i.e., feeds through the volume and tone controller)
             * "011": Subwoofer LPF frequency cutoff (fc) 120Hz
             *
             * 3: "00011000"
             * "00": Mixing input selector "Mix". I think it means to set the mixer input according
             * to the MIN pin, which will select between A1A2 and B1B2. Setting values "01" or "10"
             * should override the hardware selector and set inputs A or B.
             * "0": ignored
             * "11": Loudness f0 "prohibition"... not sure what that means.
             * "0": Mix channel 2 ON
             * "0": Mix channel 1 ON
             * "0": ignored
             * -- extra: Original value was 0x19, which is invalid.
             */
        }
    }

    private void mix(boolean on){
        // NOTE: Master volume controls AMFM radio on input A, mixer controls Android on input B
        if (filedes > 0){
            if (on) {
                mixer_gain = 16;
                if (master_gain >= 4) master_gain -= 4;
                else master_gain = 0;
                JniI2c.writeRk(filedes, 0x40, 0x20, volsteps[master_gain]); // master volume -38dB
                JniI2c.writeRk(filedes, 0x40, 0x30, volsteps[mixer_gain]); // mixer volume -18dB
            } else {
                mixer_gain = 0;
                if (master_gain < (maxvoloff-4)) master_gain += 4;
                else master_gain = maxvoloff;
                JniI2c.writeRk(filedes, 0x40, 0x20, volsteps[master_gain]); // master volume -29dB
                JniI2c.writeRk(filedes, 0x40, 0x30, volsteps[mixer_gain]); // mixer MUTE
            }
        }
    }

    private void setPhone(boolean on){
        // NOTE: BT phone seems to be on E2_single.
        if (filedes > 0){
            if (on){
                JniI2c.writeRk(filedes, 0x40, 0x20, 0xff); // master MUTE
                JniI2c.writeRk(filedes, 0x40, 0x30, 0xff); // mixer MUTE
                JniI2c.writeRk(filedes, 0x40, 0x05, 0x0b); // input select E2_single
                JniI2c.writeRk(filedes, 0x40, 0x06, 0x05); // input gain 5dB
                JniI2c.writeRk(filedes, 0x40, 0x20, 0x89); // master volume -9dB
                JniI2c.writeRk(filedes, 0x40, 0x30, 0xff); // mixer MUTE
            } else {
                JniI2c.writeRk(filedes, 0x40, 0x20, 0xff); // master MUTE
                JniI2c.writeRk(filedes, 0x40, 0x30, 0xff); // mixer MUTE
                JniI2c.writeRk(filedes, 0x40, 0x05, input_select); // input select A_single
                JniI2c.writeRk(filedes, 0x40, 0x06, input_gain); // input gain 8dB
                JniI2c.writeRk(filedes, 0x40, 0x20, volsteps[master_gain]); // master volume -20dB
                JniI2c.writeRk(filedes, 0x40, 0x30, volsteps[mixer_gain]); // mixer back were it was before
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        filedes = JniI2c.open("/dev/i2c-4");
        Log.d(LOG_TAG, "Filedes: "+Integer.toString(filedes));
        if (filedes <= 0){
            stopForeground(true);
            stopSelf();
        } else {
            resetupBD37033();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().contains("vol-")){
            volDown();
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else if (intent.getAction().contains("vol+")){
            volUp();
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else if (intent.getAction().contains("mix")){
            mix=!mix;
            mix(mix);
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else if (intent.getAction().contains("phone")){
            phone=!phone;
            setPhone(phone);
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else {
            showNotification();
            startForeground(17425, notification);
        }
        return START_STICKY;
    }

    private void showNotification() {
        Intent voldownIntent = new Intent(this, SoundService.class);
        voldownIntent.setAction("vol-");
        PendingIntent voldownPIntent = PendingIntent.getService(this, 0, voldownIntent, 0);

        Intent volupIntent = new Intent(this, SoundService.class);
        volupIntent.setAction("vol+");
        PendingIntent volupPIntent = PendingIntent.getService(this, 0, volupIntent, 0);

        Intent mixIntent = new Intent(this, SoundService.class);
        mixIntent.setAction("mix");
        PendingIntent mixPIntent = PendingIntent.getService(this, 0, mixIntent, 0);

        Intent phoneIntent = new Intent(this, SoundService.class);
        phoneIntent.setAction("phone");
        PendingIntent phonePIntent = PendingIntent.getService(this, 0, phoneIntent, 0);

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        notification = new Notification.Builder(this)
                .setContentTitle("BD37033 Controller")
                .setTicker("BD37033 Controller")
                .setContentText("Volume: "+Integer.toString(master_gain)+", Mix: "+Boolean.toString((!phone)&&mix)+", Phone: "+Boolean.toString(phone))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(phonePIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_previous, "Vol-", voldownPIntent)
                .addAction(android.R.drawable.ic_media_next, "Vol+", volupPIntent)
                .addAction(android.R.drawable.ic_media_play, "Mix", mixPIntent)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "In onDestroy");
        if (filedes > 0) JniI2c.close(filedes);
        filedes = 0;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
