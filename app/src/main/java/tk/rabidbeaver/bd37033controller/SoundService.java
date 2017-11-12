package tk.rabidbeaver.bd37033controller;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.IBinder;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SoundService extends Service {
    private static final String LOG_TAG = "SoundService";
    private static Notification notification;

    LocalSocket mSocket;
    DataInputStream is;
    DataOutputStream os;

    // Following block is loaded from shared prefs:
    private static int rinit_setup;
    private static int rlpf_setup;
    private static int rmixer_setup;
    private static int rinput_select;
    private static int rinput_gain;
    private static int master_vol;
    private static int rfader_f1;
    private static int rfader_f2;
    private static int rfader_r1;
    private static int rfader_r2;
    private static int rfader_sub;
    private static int mixer_offset;
    private static int rbass_config;
    private static int rmid_config;
    private static int rtreb_config;
    private static int rbass_gain;
    private static int rmid_gain;
    private static int rtreb_gain;
    private static int rloud_config;
    private static int phone_master_vol;
    private static boolean mix;

    // ALWAYS initialize with the phone in the OFF state.
    private static boolean phone = false;
    private static boolean muted = false;

    private static SharedPreferences stateStore = null;

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
    
    int writeBD(int b1, int b2){
        byte data[] = {(byte)0xaa, 0x55, 0x02, (byte)b1, (byte)b2, (byte)(0x02^b1^b2)};
        try {
            os.write(data, 0, 6);
        } catch (IOException e){
            e.printStackTrace();
        }
        return 2;
    }

    private boolean volUp(){
        if (phone && mSocket.isConnected() && phone_master_vol < maxvoloff) {
            if (writeBD(0x20, volsteps[phone_master_vol+1]) == 2){
                phone_master_vol++;
                stateStore.edit().putInt("phone_master_vol", phone_master_vol).apply();
                return true;
            }
            return false;
        } else if (!phone && mSocket.isConnected() && master_vol < maxvoloff){
            if (writeBD(0x20, volsteps[master_vol+1]) == 2){
                master_vol++;
                if (mix) writeBD(0x30, volsteps[master_vol]+mixer_offset);
                stateStore.edit().putInt("master_vol", master_vol).apply();
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean volDown(){
        if (phone && mSocket.isConnected() && phone_master_vol > 0) {
            if (writeBD(0x20, volsteps[phone_master_vol-1]) == 2){
                phone_master_vol--;
                stateStore.edit().putInt("phone_master_vol", phone_master_vol).apply();
                return true;
            }
            return false;
        } else if (!phone && mSocket.isConnected() && master_vol > 0){
            if (writeBD(0x20, volsteps[master_vol-1]) == 2){
                master_vol--;
                if (mix) writeBD(0x30, volsteps[master_vol]+mixer_offset);
                stateStore.edit().putInt("master_vol", master_vol).apply();
                return true;
            }
            return false;
        }
        return false;
    }

    private void resetupBD37033(){
        if (mSocket.isConnected()) {
            writeBD(0xfe, 0x81); // system reset
            writeBD(0x01, rinit_setup); // initial setup *1
            writeBD(0x02, rlpf_setup); // subwoofer setup *2
            writeBD(0x03, rmixer_setup); // mixer setup *3
            writeBD(0x05, rinput_select); // input selector A full diff type negative input
            writeBD(0x06, rinput_gain); // input gain MUTE OFF, 8dB
            writeBD(0x20, volsteps[master_vol]); // master gain -15dB
            writeBD(0x28, rfader_f1); // F1 gain 0dB
            writeBD(0x29, rfader_f2); // F2 gain 0dB
            writeBD(0x2a, rfader_r1); // R1 gain 0dB
            writeBD(0x2b, rfader_r2); // R2 gain 0dB
            writeBD(0x2c, rfader_sub); // subwoofer gain -12dB
            writeBD(0x30, volsteps[master_vol]+mixer_offset);
            writeBD(0x41, rbass_config); // bass f0 80Hz, Q 0.5
            writeBD(0x44, rmid_config); // mid f0 1kHz, Q 1.0
            writeBD(0x47, rtreb_config); // treb f0 7.5 kHz, Q 1.25
            writeBD(0x51, rbass_gain); // bass gain BOOST, 4dB
            writeBD(0x54, rmid_gain); // mid gain BOOST, 0dB
            writeBD(0x57, rtreb_gain); // treb gain BOOST, 5dB
            writeBD(0x75, rloud_config); // loudness gain "HICUT4", 0dB

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
        if (mSocket.isConnected() && !phone){
            if (on) {
                writeBD(0x20, volsteps[master_vol]); // master volume -38dB
                writeBD(0x30, volsteps[master_vol]+mixer_offset); // mixer volume -18dB
                writeBD(0x03, rmixer_setup);
            } else {
                writeBD(0x20, volsteps[master_vol]); // master volume -29dB
                writeBD(0x30, volsteps[0]); // mixer MUTE
                writeBD(0x03, 0x0e); // mixer both channels OFF
            }
            stateStore.edit().putBoolean("mix", mix).apply();
        }
    }

    private void setBass(int q, int f0, int gain, boolean cut){
        if (gain < 0 || gain > 15 || q < 0 || q > 3 || f0 < 0 || f0 > 3) return;
        rbass_config = q | f0 << 4;
        rbass_gain = gain | (cut?0x80:0x00);
        writeBD(0x41, rbass_config);
        writeBD(0x51, rbass_gain);
    }
    private void setMid(int q, int f0, int gain, boolean cut){
        if (gain < 0 || gain > 15 || q < 0 || q > 3 || f0 < 0 || f0 > 3) return;
        rmid_config = q | f0 << 4;
        rmid_gain = gain | (cut?0x80:0x00);
        writeBD(0x44, rmid_config);
        writeBD(0x54, rmid_gain);
    }
    private void setTreb(int q, int f0, int gain, boolean cut){
        if (gain < 0 || gain > 15 || q < 0 || q > 1 || f0 < 0 || f0 > 3) return;
        rtreb_config = q | f0 << 4;
        rtreb_gain = gain | (cut?0x80:0x00);
        writeBD(0x47, rtreb_config);
        writeBD(0x57, rtreb_gain);
    }
    private void storeEq(){
        SharedPreferences.Editor editor = stateStore.edit();
        editor.putInt("rbass_config", rbass_config);
        editor.putInt("rbass_gain", rbass_gain);
        editor.putInt("rmid_config", rmid_config);
        editor.putInt("rmid_gain", rmid_gain);
        editor.putInt("rtreb_config", rtreb_config);
        editor.putInt("rtreb_gain", rtreb_gain);
        editor.apply();
    }

    private void setPhone(boolean on){
        // NOTE: BT phone seems to be on E2_single.
        if (mSocket.isConnected()){
            if (on){
                writeBD(0x20, 0xff); // master MUTE
                writeBD(0x30, 0xff); // mixer MUTE
                writeBD(0x02, 0x00); // sub output MUTE
                writeBD(0x03, 0x0e); // mixer both channels OFF
                writeBD(0x05, 0x0b); // input select E2_single
                writeBD(0x06, 0x05); // input gain 5dB
                writeBD(0x20, volsteps[phone_master_vol]); // master volume -9dB
                writeBD(0x30, 0xff); // mixer MUTE
            } else {
                writeBD(0x20, 0xff); // master MUTE
                writeBD(0x30, 0xff); // mixer MUTE
                writeBD(0x02, rlpf_setup); // sub output resume
                writeBD(0x03, rmixer_setup); // mixer both channels ON
                writeBD(0x05, rinput_select); // input select A_single
                writeBD(0x06, rinput_gain); // input gain 8dB
                writeBD(0x20, volsteps[master_vol]); // master volume -20dB
                writeBD(0x30, volsteps[master_vol]+mixer_offset); // mixer back were it was before
            }
        }
    }

    /*
     * freqCut: 0=OFF, 1=55Hz, 2=85Hz, 3=120Hz, 4=160Hz, 5=full range
     */
    private void setLPF(boolean phase180, boolean bypassAudioProc, int freqCut){
        if (!mSocket.isConnected() || freqCut < 0 || freqCut > 5) return;
        rlpf_setup = freqCut | (phase180?0x80:0x00) | (bypassAudioProc?0x08:0x00);
        writeBD(0x02, rlpf_setup);
    }
    private void saveLPF(){
        stateStore.edit().putInt("rlpf_setup", rlpf_setup).apply();
    }

    /*
     * For loudness we change two registers, loudness gain, and mixing setup
     * Reason is that mixing setup includes loudness center frequency (f0)
     * We also don't need to change the mixing setup since the other values
     * are good as-is.
     */
    private void setLoudness(int loudf0, int loudgain, int hicut){
        if (loudf0 < 0 || loudf0 > 2 || loudgain < 0 || loudgain > 15 || hicut < 0 || hicut > 3) return;
        rloud_config = loudgain | (hicut << 5);
        rmixer_setup = loudf0 << 3;
        if (mix && !phone) writeBD(0x03, rmixer_setup);
        writeBD(0x75, rloud_config);
    }
    private void saveLoudness(){
        stateStore.edit().putInt("rmixer_setup", rmixer_setup).apply();
        stateStore.edit().putInt("rloud_config", rloud_config).apply();
    }

    private void setInputA(){ // AMFM radio
        rinput_select = 0x00;
        writeBD(0x20, 0xff); // master MUTE
        writeBD(0x30, 0xff); // mixer MUTE
        writeBD(0x02, rlpf_setup); // sub output resume
        writeBD(0x05, rinput_select);
        writeBD(0x06, rinput_gain); // input gain 8dB
        writeBD(0x20, volsteps[master_vol]); // master volume -20dB
        writeBD(0x30, volsteps[master_vol]+mixer_offset); // mixer back were it was before
        stateStore.edit().putInt("rinput_select", rinput_select).apply();
    }
    private void setInputB(){ // Android
        rinput_select = 0x01;
        writeBD(0x20, 0xff); // master MUTE
        writeBD(0x30, 0xff); // mixer MUTE
        writeBD(0x02, rlpf_setup); // sub output resume
        writeBD(0x05, rinput_select);
        writeBD(0x06, rinput_gain); // input gain 8dB
        writeBD(0x20, volsteps[master_vol]); // master volume -20dB
        writeBD(0x30, 0xff); // mixer MUTE
        stateStore.edit().putInt("rinput_select", rinput_select).apply();
    }
    private void setInputC(){
        rinput_select = 0x02;
        writeBD(0x20, 0xff); // master MUTE
        writeBD(0x30, 0xff); // mixer MUTE
        writeBD(0x02, rlpf_setup); // sub output resume
        writeBD(0x05, rinput_select);
        writeBD(0x06, rinput_gain); // input gain 8dB
        writeBD(0x20, volsteps[master_vol]); // master volume -20dB
        writeBD(0x30, volsteps[master_vol]+mixer_offset); // mixer back were it was before
        writeBD(0x05, rinput_select);
        stateStore.edit().putInt("rinput_select", rinput_select).apply();
    }
    private void setInputD(){
        rinput_select = 0x03;
        writeBD(0x20, 0xff); // master MUTE
        writeBD(0x30, 0xff); // mixer MUTE
        writeBD(0x02, rlpf_setup); // sub output resume
        writeBD(0x05, rinput_select);
        writeBD(0x06, rinput_gain); // input gain 8dB
        writeBD(0x20, volsteps[master_vol]); // master volume -20dB
        writeBD(0x30, volsteps[master_vol]+mixer_offset); // mixer back were it was before
        stateStore.edit().putInt("rinput_select", rinput_select).apply();
    }
    private void setInputDDiff(){
        rinput_select = 0x06;
        writeBD(0x20, 0xff); // master MUTE
        writeBD(0x30, 0xff); // mixer MUTE
        writeBD(0x02, rlpf_setup); // sub output resume
        writeBD(0x05, rinput_select);
        writeBD(0x06, rinput_gain); // input gain 8dB
        writeBD(0x20, volsteps[master_vol]); // master volume -20dB
        writeBD(0x30, volsteps[master_vol]+mixer_offset); // mixer back were it was before
        stateStore.edit().putInt("rinput_select", rinput_select).apply();
    }
    private void setInputE2(){ // probably never used
        rinput_select = 0x0b;
        writeBD(0x20, 0xff); // master MUTE
        writeBD(0x30, 0xff); // mixer MUTE
        writeBD(0x02, rlpf_setup); // sub output resume
        writeBD(0x05, rinput_select);
        writeBD(0x06, rinput_gain); // input gain 8dB
        writeBD(0x20, volsteps[master_vol]); // master volume -20dB
        writeBD(0x30, volsteps[master_vol]+mixer_offset); // mixer back were it was before
        stateStore.edit().putInt("rinput_select", rinput_select).apply();
    }
    private void setInputEFull(boolean biasType){ // maybe bluetooth stereo?
        rinput_select = 0x08 | (biasType?0x80:0x00);
        writeBD(0x20, 0xff); // master MUTE
        writeBD(0x30, 0xff); // mixer MUTE
        writeBD(0x02, rlpf_setup); // sub output resume
        writeBD(0x05, rinput_select);
        writeBD(0x06, rinput_gain); // input gain 8dB
        writeBD(0x20, volsteps[master_vol]); // master volume -20dB
        writeBD(0x30, volsteps[master_vol]+mixer_offset); // mixer back were it was before
        writeBD(0x05, rinput_select);
        stateStore.edit().putInt("rinput_select", rinput_select).apply();
    }

    /*
     * Mute INPUT, but not MIXER.
     * DO NOT STORE!
     */
    private void mute(boolean on){
        muted = on;
        rinput_gain = on?(rinput_gain|0x80):(rinput_gain&0x7f);
        writeBD(0x06, rinput_gain);
    }
    private void mute(){
        muted = !muted;
        rinput_gain = muted?(rinput_gain|0x80):(rinput_gain&0x7f);
        writeBD(0x06, rinput_gain);
    }

    /*
     * Input range; 0x71(15dB) --> 0xCF (-79dB), with 0xFF (MUTE), and 0x80 (0dB)
     * Set input -1 for no change.
     */
    private void setFaders(int f1, int f2, int r1, int r2, int sub){
        if ((f1 < 0x71 && f1 != -1) || (f1 > 0xcf && f1 != 0xff)
                || (f2 < 0x71 && f2 != -1) || (f2 > 0xcf && f2 != 0xff)
                || (r1 < 0x71 && r1 != -1) || (r1 > 0xcf && r1 != 0xff)
                || (r2 < 0x71 && r2 != -1) || (r2 > 0xcf && r2 != 0xff)
                || (sub < 0x71 && sub != -1) || (sub > 0xcf && sub != 0xff)) return;
        if (f1 > 0){
            rfader_f1 = f1;
            writeBD(0x28, rfader_f1);
        }
        if (f2 > 0){
            rfader_f2 = f2;
            writeBD(0x29, rfader_f2);
        }
        if (r1 > 0){
            rfader_r1 = r1;
            writeBD(0x2a, rfader_r1);
        }
        if (r2 > 0){
            rfader_r2 = r2;
            writeBD(0x2b, rfader_r2);
        }
        if (sub > 0) {
            rfader_sub = sub;
            writeBD(0x2c, rfader_sub);
        }
    }
    private void storeFaders(){
        stateStore.edit().putInt("rfader_f1", rfader_f1).apply();
        stateStore.edit().putInt("rfader_f2", rfader_f2).apply();
        stateStore.edit().putInt("rfader_r1", rfader_r1).apply();
        stateStore.edit().putInt("rfader_r2", rfader_r2).apply();
        stateStore.edit().putInt("rfader_sub", rfader_sub).apply();
    }

    /*
     * Mute MIXER, but not INPUT.
     * DO NOT STORE!
     */
    private void mixMute(boolean on){
        if (on){
            writeBD(0x30, 0xff);
            writeBD(0x03, 0x0e); // mixer both channels OFF
        } else {
            writeBD(0x30, volsteps[master_vol]+mixer_offset);
            writeBD(0x03, rmixer_setup); // mixer both channels ON
        }

    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSocket = new LocalSocket();
        try {
            mSocket.connect(new LocalSocketAddress("/dev/car/audio", LocalSocketAddress.Namespace.FILESYSTEM));
            is = new DataInputStream(mSocket.getInputStream());
            os = new DataOutputStream(mSocket.getOutputStream());
        } catch (Exception e){
            e.printStackTrace();
            stopForeground(true);
            stopSelf();
        }

        stateStore = getApplicationContext().getSharedPreferences("stateStore", Context.MODE_PRIVATE);

        rinit_setup = stateStore.getInt("rinit_setup", 0xe3);
        rlpf_setup = stateStore.getInt("rlpf_setup", 0x03);
        rmixer_setup = stateStore.getInt("rmixer_setup", 0x08);
        rinput_select = stateStore.getInt("rinput_select", 0x00);
        rinput_gain = stateStore.getInt("rinput_gain", 0x08);
        master_vol = stateStore.getInt("master_vol", 10);
        rfader_f1 = stateStore.getInt("rfader_f1", 0x80);
        rfader_f2 = stateStore.getInt("rfader_f2", 0x80);
        rfader_r1 = stateStore.getInt("rfader_r1", 0x80);
        rfader_r2 = stateStore.getInt("rfader_r2", 0x80);
        rfader_sub = stateStore.getInt("rfader_sub", 0x8c);
        mixer_offset = stateStore.getInt("mixer_offset", -10);
        rbass_config = stateStore.getInt("rbass_config", 0x10);
        rmid_config = stateStore.getInt("rmid_config", 0x11);
        rtreb_config = stateStore.getInt("rtreb_config", 0x01);
        rbass_gain = stateStore.getInt("rbass_gain", 0x04);
        rmid_gain = stateStore.getInt("rmid_gain", 0x00);
        rtreb_gain = stateStore.getInt("rtreb_gain", 0x05);
        rloud_config = stateStore.getInt("rloud_config", 0x60);
        phone_master_vol = stateStore.getInt("phone_master_vol", 24);
        mix = stateStore.getBoolean("mix", true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("SoundService", "action: "+intent.getAction());
        if (intent.getAction().contentEquals(Constants.ACTION.VOL_DOWN)){
            volDown();
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else if (intent.getAction().contentEquals(Constants.ACTION.VOL_UP)){
            volUp();
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else if (intent.getAction().contentEquals(Constants.ACTION.MUTE)){
            mute();
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else if (intent.getAction().contentEquals(Constants.ACTION.PHONE_ON)){
            setPhone(true);
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else if (intent.getAction().contentEquals(Constants.ACTION.PHONE_OFF)){
            setPhone(false);
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else if (intent.getAction().contains("mix")){
            mix=!mix;
            mix(mix);
            showNotification();
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(17425, notification);
        } else {
            resetupBD37033();
            showNotification();
            startForeground(17425, notification);
        }
        return START_STICKY;
    }

    private void showNotification() {
        Intent voldownIntent = new Intent(this, SoundService.class);
        voldownIntent.setAction(Constants.ACTION.VOL_DOWN);
        PendingIntent voldownPIntent = PendingIntent.getService(this, 0, voldownIntent, 0);

        Intent volupIntent = new Intent(this, SoundService.class);
        volupIntent.setAction(Constants.ACTION.VOL_UP);
        PendingIntent volupPIntent = PendingIntent.getService(this, 0, volupIntent, 0);

        Intent mixIntent = new Intent(this, SoundService.class);
        mixIntent.setAction("mix");
        PendingIntent mixPIntent = PendingIntent.getService(this, 0, mixIntent, 0);

        notification = new Notification.Builder(this)
                .setContentTitle("BD37033 Controller")
                .setTicker("BD37033 Controller")
                .setContentText("Volume: "+Integer.toString(master_vol)+", Mix: "+Boolean.toString((!phone)&&mix)+", Phone: "+Boolean.toString(phone))
                .setSmallIcon(R.drawable.ic_volume_mute_white_48dp)
                .setOngoing(true)
                .addAction(R.drawable.ic_volume_down_white_48dp, "Vol-", voldownPIntent)
                .addAction(R.drawable.ic_volume_up_white_48dp, "Vol+", volupPIntent)
                .addAction(R.drawable.ic_device_hub_white_48dp, "Mix", mixPIntent)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "In onDestroy");
        if (mSocket.isConnected()){
            try {
                is.close();
                os.close();
                mSocket.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
