/*
 * Android USB Serial Monitor Lite
 * 
 * Copyright (C) 2012 Keisuke SUZUKI
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * thanks to Arun.
 */
package co.kr.fvn.wp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.UartConfig;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class AndroidUSBSerialMonitorLite extends Activity {

    private Button btn_reset,btn_edit,btn_insert,btn_start,btn_stop,btn_continue,btn_washer,btn_drain,btn_vent,btn_conn;
    private EditText et_set_top_cnt,et_set_pump_cnt,et_set_volt,et_set_volt_dec,et_set_am,et_set_am_dec,et_set_on,et_set_off,et_set_delay,et_set_flooding;
    private TextView tv_time,tv_total_cnt,tv_top_state,tv_top_volt,tv_top_am;
    private String sepa = ":";

    static final int PREFERENCE_REV_NUM = 1;
    private String tempStr = "";
    // debug settings
    private static final boolean SHOW_DEBUG                 = false;
    private static final boolean USE_WRITE_BUTTON_FOR_DEBUG = false;

    public static final boolean isICSorHigher = ( Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB_MR2 );

    // occurs USB packet loss if TEXT_MAX_SIZE is over 6000
    private static final int TEXT_MAX_SIZE = 8192;

    private static final int MENU_ID_SETTING        = 0;
    private static final int MENU_ID_CLEARTEXT      = 1;
    private static final int MENU_ID_SENDTOEMAIL    = 2;
    private static final int MENU_ID_OPENDEVICE     = 3;
    private static final int MENU_ID_CLOSEDEVICE    = 4;
    private static final int MENU_ID_WORDLIST       = 5;
  
    private static final int REQUEST_PREFERENCE         = 0;
    private static final int REQUEST_WORD_LIST_ACTIVITY = 1;
    
    // Defines of Display Settings
    private static final int DISP_CHAR  = 0;
    private static final int DISP_DEC   = 1;
    private static final int DISP_HEX   = 2;

    // Linefeed Code Settings
    private static final int LINEFEED_CODE_CR   = 0;
    private static final int LINEFEED_CODE_CRLF = 1;
    private static final int LINEFEED_CODE_LF   = 2;

    // Load Bundle Key (for view switching)
    private static final String BUNDLEKEY_LOADTEXTVIEW = "bundlekey.LoadTextView";

    Physicaloid mSerial;

    private ScrollView mSvText;
    private TextView mTvSerial;
    private StringBuilder mText = new StringBuilder();
    private boolean mStop = false;

    String TAG = "AndroidSerialTerminal";

    Handler mHandler = new Handler();

    // Default settings
    private int mBaudrate           = 19200;
    private int mTextFontSize       = 12;
    private Typeface mTextTypeface  = Typeface.MONOSPACE;
    private int mDisplayType        = DISP_CHAR;
    //private int mReadLinefeedCode   = LINEFEED_CODE_CRLF;
    //private int mWriteLinefeedCode  = LINEFEED_CODE_CRLF;
    private int mReadLinefeedCode   = LINEFEED_CODE_CR;
    private int mWriteLinefeedCode  = LINEFEED_CODE_CR;
    private int mDataBits           = UartConfig.DATA_BITS8;
    private int mParity             = UartConfig.PARITY_NONE;
    private int mStopBits           = UartConfig.STOP_BITS1;
    private int mFlowControl        = UartConfig.FLOW_CONTROL_OFF;
    private String mEmailAddress    = "@gmail.com";

    private boolean mRunningMainLoop = false;

    private static final String ACTION_USB_PERMISSION =
            "jp.ksksue.app.terminal.USB_PERMISSION";

    // Linefeed
    private final static String BR = System.getProperty("line.separator");

    ListView listview ;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

/* FIXME : How to check that there is a title bar menu or not.
        // Should not set a Window.FEATURE_NO_TITLE on Honeycomb because a user cannot see menu button.
        if(isICSorHigher) {
            if(!getWindow().hasFeature(Window.FEATURE_ACTION_BAR)) {
                requestWindowFeature(Window.FEATURE_NO_TITLE);
            }
        }
*/
        setContentView(R.layout.activity_main);
        initUi();
        btnOnClickEvent();
        // get service
        mSerial = new Physicaloid(this);

        if (SHOW_DEBUG) {
            Log.d(TAG, "New instance : " + mSerial);
        }
        // listen for new devices
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
        //openUsbSerial();  registerReceiver 에 의해 연결되면 자동으로 열게됨

    }
    private void initUi() {
        btn_reset = findViewById(R.id.btn_reset);
        btn_edit = findViewById(R.id.btn_edit);
        btn_insert = findViewById(R.id.btn_insert);
        btn_start = findViewById(R.id.btn_start);
        btn_stop = findViewById(R.id.btn_stop);
        btn_continue = findViewById(R.id.btn_continue);
        btn_washer = findViewById(R.id.btn_washer);
        btn_drain = findViewById(R.id.btn_drain);
        btn_vent = findViewById(R.id.btn_vent);
        btn_conn = findViewById(R.id.btn_conn);

        tv_total_cnt = findViewById(R.id.tv_total_cnt);
        tv_top_state = findViewById(R.id.tv_top_state);
        tv_top_volt = findViewById(R.id.tv_top_volt);
        tv_top_am = findViewById(R.id.tv_top_am);
        tv_time = findViewById(R.id.tv_time);

        et_set_top_cnt = findViewById(R.id.et_set_top_cnt);
        et_set_pump_cnt = findViewById(R.id.et_set_pump_cnt);
        et_set_volt = findViewById(R.id.et_set_volt);
        et_set_volt_dec = findViewById(R.id.et_set_volt_dec);
        et_set_am = findViewById(R.id.et_set_am);
        et_set_am_dec = findViewById(R.id.et_set_am_dec);
        et_set_on = findViewById(R.id.et_set_on);
        et_set_off = findViewById(R.id.et_set_off);
        et_set_delay = findViewById(R.id.et_set_delay);
        et_set_flooding = findViewById(R.id.et_set_flooding);
        TimerTask mTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일 EE요일 aa hh시 mm분 ss초");
                        tv_time.setText(sdf.format(new Date(System.currentTimeMillis())));
                    }
                });
            }
        };
        Timer mTimer = new Timer(true);
        mTimer.schedule(mTask, 100, 1000);
        //저장된 값을 불러오기 위해 같은 네임파일을 찾음.
        SharedPreferences sf = getSharedPreferences("wp",MODE_PRIVATE);
        String setVal = sf.getString("set_val","");

        if("".equals(setVal)){  //첫기동
            btn_edit.setEnabled(false);
            btn_insert.setEnabled(true);
            et_set_top_cnt.setEnabled(true);
            et_set_pump_cnt.setEnabled(true);
            et_set_on.setEnabled(true);
            et_set_off.setEnabled(true);
            et_set_delay.setEnabled(true);
            et_set_flooding.setEnabled(true);
            et_set_volt.setEnabled(true);
            et_set_volt_dec.setEnabled(true);
            et_set_am.setEnabled(true);
            et_set_am_dec.setEnabled(true);
        }else{
            String[] valArry = setVal.split(sepa);
            et_set_top_cnt.setText(valArry[0]);
            et_set_pump_cnt.setText(valArry[1]);
            et_set_on.setText(valArry[2]);
            et_set_off.setText(valArry[3]);
            et_set_delay.setText(valArry[4]);
            et_set_flooding.setText(valArry[5]);
            et_set_volt.setText(valArry[6]);
            et_set_volt_dec.setText(valArry[7]);
            et_set_am.setText(valArry[8]);
            et_set_am_dec.setText(valArry[9]);

            btn_edit.setEnabled(true);
            btn_insert.setEnabled(false);
            et_set_top_cnt.setEnabled(false);
            et_set_pump_cnt.setEnabled(false);
            et_set_volt.setEnabled(false);
            et_set_am.setEnabled(false);
            et_set_volt_dec.setEnabled(false);
            et_set_am_dec.setEnabled(false);
            et_set_on.setEnabled(false);
            et_set_off.setEnabled(false);
            et_set_delay.setEnabled(false);
            et_set_flooding.setEnabled(false);
        }
    }
    private void btnOnClickEvent() {
        btn_reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSerial == null) {
                    Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                }else{
                    if(mSerial.isOpened()){
                        writeToSerial("$CRESET");
                    }else{
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        btn_edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!et_set_top_cnt.isEnabled()){
                    btn_edit.setEnabled(false);
                    btn_insert.setEnabled(true);
                    et_set_top_cnt.setEnabled(true);
                    et_set_pump_cnt.setEnabled(true);
                    et_set_volt.setEnabled(true);
                    et_set_am.setEnabled(true);
                    et_set_volt_dec.setEnabled(true);
                    et_set_am_dec.setEnabled(true);
                    et_set_on.setEnabled(true);
                    et_set_off.setEnabled(true);
                    et_set_delay.setEnabled(true);
                    et_set_flooding.setEnabled(true);
                }
            }
        });
        btn_insert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(et_set_top_cnt.isEnabled()){
                    String etTopCnt = String.valueOf(et_set_top_cnt.getText());
                    if(etTopCnt.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "전체 회차를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_top_cnt.requestFocus();
                        return;
                    }
                    String etPump = String.valueOf(et_set_pump_cnt.getText());
                    if(etPump.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "On Off 회차를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_pump_cnt.requestFocus();
                        return;
                    }
                    String etOn = String.valueOf(et_set_on.getText());
                    if(etOn.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "On Time 을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_on.requestFocus();
                        return;
                    }
                    String etOff = String.valueOf(et_set_off.getText());
                    if(etOff.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "Off Time 을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_off.requestFocus();
                        return;
                    }
                    String etDelay = String.valueOf(et_set_delay.getText());
                    if(etDelay.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "지연 Time 을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_delay.requestFocus();
                        return;
                    }
                    String etFlood = String.valueOf(et_set_flooding.getText());
                    if(etFlood.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "침수 회차를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_flooding.requestFocus();
                        return;
                    }
                    String etVolt = String.valueOf(et_set_volt.getText());
                    if(etVolt.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "Volt 소수점 이상을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_volt.requestFocus();
                        return;
                    }
                    String etVoltDec = String.valueOf(et_set_volt_dec.getText());
                    if(etVoltDec.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "Volt 소수점 이하를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_volt_dec.requestFocus();
                        return;
                    }
                    String etAm = String.valueOf(et_set_am.getText());
                    if(etAm.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "암페어 소수점 이상을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_am.requestFocus();
                        return;
                    }
                    String etAmDec = String.valueOf(et_set_am_dec.getText());
                    if(etAmDec.isEmpty()){
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "암페어 소수점 이하를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_am_dec.requestFocus();
                        return;
                    }

                    String saveVal = etTopCnt+sepa+etPump+sepa+etOn+sepa+etOff+sepa+etDelay+sepa+etFlood+sepa+etVolt+"."+etVoltDec+sepa+etAm+"."+etAmDec;

                    if(mSerial == null) {
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                        return;
                    }else{
                        if(mSerial.isOpened()){
                            writeToSerial("$S"+saveVal);
                        }else{
                            Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    btn_edit.setEnabled(true);
                    btn_insert.setEnabled(false);
                    et_set_top_cnt.setEnabled(false);
                    et_set_pump_cnt.setEnabled(false);
                    et_set_volt.setEnabled(false);
                    et_set_am.setEnabled(false);
                    et_set_volt_dec.setEnabled(false);
                    et_set_am_dec.setEnabled(false);
                    et_set_on.setEnabled(false);
                    et_set_off.setEnabled(false);
                    et_set_delay.setEnabled(false);
                    et_set_flooding.setEnabled(false);
                    SharedPreferences sf = getSharedPreferences("wp",MODE_PRIVATE);
                    SharedPreferences.Editor editor = sf.edit();
                    editor.putString("set_val",saveVal);
                    editor.commit();
                }
            }
        });
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSerial == null) {
                    Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                }else{
                    if(mSerial.isOpened()){
                        writeToSerial("$CSTART");
                    }else{
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSerial == null) {
                    Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                }else{
                    if(mSerial.isOpened()){
                        writeToSerial("$CPAUSE");
                    }else{
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        btn_continue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSerial == null) {
                    Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                }else{
                    if(mSerial.isOpened()){
                        writeToSerial("$CRESUME");
                    }else{
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        btn_washer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSerial == null) {
                    Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                }else{
                    if(mSerial.isOpened()){
                        writeToSerial("$TWASHER");
                    }else{
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        btn_drain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSerial == null) {
                    Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                }else{
                    if(mSerial.isOpened()){
                        writeToSerial("$TINPUT");
                    }else{
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        btn_vent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSerial == null) {
                    Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                }else{
                    if(mSerial.isOpened()){
                        writeToSerial("$TOUTPUT");
                    }else{
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        btn_conn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSerial == null) {
                    Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                }else{
                    if(mSerial.isOpened()){
                        //writeDataToSerial();
                    }else{
                        Toast.makeText(AndroidUSBSerialMonitorLite.this, "모듈과 연결이 되지 않았습니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    private void writeToSerial(String command) {
        command = command+"\r";
        mSerial.write(command.getBytes(), command.length());
    }

    private void writeDataToSerial() {

        String strWrite = et_set_top_cnt.getText().toString();
        if(strWrite.length() != 2){
            Toast.makeText(this, "RCU ID는 두자리입니다.", Toast.LENGTH_LONG).show();
            return;
        }
        if("0123456789".indexOf(strWrite.substring(0,1)) < 0){
            Toast.makeText(this, "RCU ID는 숫자로 입력해주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        if("0123456789".indexOf(strWrite.substring(1,2)) < 0){
            Toast.makeText(this, "RCU ID는 숫자로 입력해주세요.", Toast.LENGTH_LONG).show();
            return;
        }

        mSerial.write(makeRcuStsByte(strWrite).getBytes(), makeRcuStsByte(strWrite).length());

    }
    public String makeRcuStsByte(String rcuId) {
        int checkSum = 0;
        byte[] bytePack = new byte[9];
        char[] rcuIdArry = rcuId.toCharArray();
        bytePack[0] = 38;                    //&
        bytePack[1] = (byte) rcuIdArry[0];  //RCU ID
        bytePack[2] = (byte) rcuIdArry[1];  //RCU ID
        bytePack[3] = 49;                    //CMD = 1
        bytePack[4] = 48;                    //DATA = 0
        for(int idx = 0; idx < 5; idx++){
            checkSum += bytePack[idx];
        }
        checkSum = checkSum % 256;

        byte upCrc = 0;
        upCrc = (byte) (checkSum >> 4);

        byte downCrc = 0;
        checkSum = checkSum << 28;
        checkSum = checkSum >>> 28;
        downCrc = (byte) checkSum;

        bytePack[5] = upCrc;
        bytePack[5] += '0';
        //down CRC
        bytePack[6] = downCrc;
        bytePack[6] += '0';

        bytePack[7] = 13;	//CR
        bytePack[8] = 10;	//LF
        String reRcuSts = (char) bytePack[0]+""+(char) bytePack[1]+""+(char) bytePack[2]+""+(char) bytePack[3]+""+(char) bytePack[4]+""+(char) bytePack[5]+""+(char) bytePack[6]+""+(char) bytePack[7]+""+(char) bytePack[8];
        return reRcuSts;
    }

    private String changeEscapeSequence(String in) {
        String out = new String();
        try {
            out = unescapeJava(in);
        } catch (IOException e) {
            return "";
        }
        switch (mWriteLinefeedCode) {
            case LINEFEED_CODE_CR:
                out = out + "\r";
                break;
            case LINEFEED_CODE_CRLF:
                out = out + "\r\n";
                break;
            case LINEFEED_CODE_LF:
                out = out + "\n";
                break;
            default:
        }
        return out;
    }

    public void setWriteTextString(String str)
    {
        et_set_top_cnt.setText(str);
    }
    
    // ---------------------------------------------------------------------------------------
    // Menu Button
    // ---------------------------------------------------------------------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_ID_OPENDEVICE, Menu.NONE, "Open Device");
        //menu.add(Menu.NONE, MENU_ID_WORDLIST, Menu.NONE, "Word List ...");
        //menu.add(Menu.NONE, MENU_ID_SETTING, Menu.NONE, "Setting ...");
        //menu.add(Menu.NONE, MENU_ID_CLEARTEXT, Menu.NONE, "Clear Text");
        //menu.add(Menu.NONE, MENU_ID_SENDTOEMAIL, Menu.NONE, "Email to ...");
        menu.add(Menu.NONE, MENU_ID_CLOSEDEVICE, Menu.NONE, "Close Device");
/*        if(mSerial!=null) {
            if(mSerial.isConnected()) {
                menu.getItem(MENU_ID_OPENDEVICE).setEnabled(false);
            } else {
                menu.getItem(MENU_ID_CLOSEDEVICE).setEnabled(false);
            }
        }
*/        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ID_OPENDEVICE:
                openUsbSerial();
                return true;
            case MENU_ID_WORDLIST:
                //Intent intent = new Intent(this, WordListActivity.class);
                //startActivityForResult(intent, REQUEST_WORD_LIST_ACTIVITY);
                return true;
            case MENU_ID_SETTING:
                startActivityForResult(new Intent().setClassName(this.getPackageName(),
                        AndroidUSBSerialMonitorLitePrefActivity.class.getName()),
                        REQUEST_PREFERENCE);
                return true;
            case MENU_ID_CLEARTEXT:
                //mTvSerial.setText("");
                mText.setLength(0);
                return true;
            case MENU_ID_SENDTOEMAIL:
                sendTextToEmail();
                return true;
            case MENU_ID_CLOSEDEVICE:
                closeUsbSerial();
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_WORD_LIST_ACTIVITY) {
            if(resultCode == RESULT_OK) {
                try {
                    String strWord = data.getStringExtra("word");
                    et_set_top_cnt.setText(strWord);
                    // Set a cursor position last
                    et_set_top_cnt.setSelection(et_set_top_cnt.getText().length());
                } catch(Exception e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == REQUEST_PREFERENCE) {

            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

            String res = pref.getString("display_list", Integer.toString(DISP_CHAR));
            mDisplayType = Integer.valueOf(res);

            res = pref.getString("fontsize_list", Integer.toString(12));
            mTextFontSize = Integer.valueOf(res);
            //mTvSerial.setTextSize(mTextFontSize);

            res = pref.getString("typeface_list", Integer.toString(3));
            switch(Integer.valueOf(res)){
                case 0:
                    mTextTypeface = Typeface.DEFAULT;
                    break;
                case 1:
                    mTextTypeface = Typeface.SANS_SERIF;
                    break;
                case 2:
                    mTextTypeface = Typeface.SERIF;
                    break;
                case 3:
                    mTextTypeface = Typeface.MONOSPACE;
                    break;
            }
            //mTvSerial.setTypeface(mTextTypeface);
            //et_set_top_cnt.setTypeface(mTextTypeface);

            res = pref.getString("readlinefeedcode_list", Integer.toString(LINEFEED_CODE_CRLF));
            mReadLinefeedCode = Integer.valueOf(res);

            res = pref.getString("writelinefeedcode_list", Integer.toString(LINEFEED_CODE_CRLF));
            mWriteLinefeedCode = Integer.valueOf(res);

            res = pref.getString("email_edittext", "@gmail.com");
            mEmailAddress = res;

            int intRes;

            res = pref.getString("baudrate_list", Integer.toString(mBaudrate));
            intRes = Integer.valueOf(res);
            if (mBaudrate != intRes) {
                mBaudrate = intRes;
                mSerial.setBaudrate(mBaudrate);
            }

            res = pref.getString("databits_list", Integer.toString(UartConfig.DATA_BITS8));
            intRes = Integer.valueOf(res);
            if (mDataBits != intRes) {
                mDataBits = Integer.valueOf(res);
                mSerial.setDataBits(mDataBits);
            }

            res = pref.getString("parity_list",
                    Integer.toString(UartConfig.PARITY_NONE));
            intRes = Integer.valueOf(res);
            if (mParity != intRes) {
                mParity = intRes;
                mSerial.setParity(mParity);
            }

            res = pref.getString("stopbits_list",
                    Integer.toString(UartConfig.STOP_BITS1));
            intRes = Integer.valueOf(res);
            if (mStopBits != intRes) {
                mStopBits = intRes;
                mSerial.setStopBits(mStopBits);
            }

            res = pref.getString("flowcontrol_list",
                    Integer.toString(UartConfig.FLOW_CONTROL_OFF));
            intRes = Integer.valueOf(res);
            if (mFlowControl != intRes) {
                mFlowControl = intRes;
                if(mFlowControl == UartConfig.FLOW_CONTROL_ON) {
                    mSerial.setDtrRts(true, true);
                } else {
                    mSerial.setDtrRts(false, false);
                }
            }

            /*
            res = pref.getString("break_list", Integer.toString(FTDriver.FTDI_SET_NOBREAK));
            intRes = Integer.valueOf(res) << 14;
            if (mBreak != intRes) {
                mBreak = intRes;
                mSerial.setSerialPropertyBreak(mBreak, FTDriver.CH_A);
                mSerial.setSerialPropertyToChip(FTDriver.CH_A);
            }
            */
        }
    }

    // ---------------------------------------------------------------------------------------
    // End of Menu button
    // ---------------------------------------------------------------------------------------

    /**
     * Saves values for view switching
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //outState.putString(BUNDLEKEY_LOADTEXTVIEW, mTvSerial.getText().toString());
    }

    /**
     * Loads values for view switching
     */

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        //mTvSerial.setText(savedInstanceState.getString(BUNDLEKEY_LOADTEXTVIEW));
    }

    @Override
    public void onDestroy() {
        mSerial.close();
        mStop = true;
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }

    private void mainloop() {
        mStop = false;
        mRunningMainLoop = true;
        btn_conn.setEnabled(true);
        //etWrite.setEnabled(true);
        //etWriteEnd.setEnabled(true);
        //Toast.makeText(this, "connected", Toast.LENGTH_SHORT).show();
        if (SHOW_DEBUG) {
            Log.d(TAG, "start mainloop");
        }
        new Thread(mLoop).start();
    }
    public void setData(String[] dataVal) {
        tv_total_cnt.setText(dataVal[0].substring(2));
        tv_top_volt.setText(dataVal[1]);
        tv_top_am.setText(dataVal[2]);
        tv_top_state.setText(dataVal[3]);
        btn_conn.setText(dataVal[4]);
    }
    public void makeListData(StringBuilder responeRcuSts) {
        //잘려서 들어오는 경우도 있음
        // $IXXXX:XX.X:XX.X:PASS/FAIL [CR]
        //$I [NOW Count],[ON Time duration],[Voltage XX.X] ,[CURRNET XX.X] ,[RESULT : PASS —> FAIL 이 없으면 , 한번이라도 FAIL 나면 FAIL 주고 STOP],[Carage return]
        Toast.makeText(this, responeRcuSts.toString(), Toast.LENGTH_SHORT).show();
        tempStr += responeRcuSts.toString();
        if(tempStr.indexOf("\r") > -1){   //최종데이터가 들어올때만    CR
            try{
                setData(tempStr.split(":"));
            }catch(ArrayIndexOutOfBoundsException e){
                e.printStackTrace();
            }catch(Exception e){
                e.printStackTrace();
            }
            tempStr = ""; //초기화
        }else{

        }
    }

    private Runnable mLoop = new Runnable() {
        @Override
        public void run() {
        int len;
        byte[] rbuf = new byte[4096];

        for (;;) {// this is the main loop for transferring
            // Read and Display to Terminal
            len = mSerial.read(rbuf);
            rbuf[len] = 0;

            if (len > 0) {
                if (SHOW_DEBUG) {
                    Log.d(TAG, "Read  Length : " + len);
                }

                switch (mDisplayType) { //출력 방식 CHAR, DEX, HEX
                    case DISP_CHAR:
                        setSerialDataToTextView(mDisplayType, rbuf, len, "", "");
                        break;
                    case DISP_DEC:
                        setSerialDataToTextView(mDisplayType, rbuf, len, "013", "010");
                        break;
                    case DISP_HEX:
                        setSerialDataToTextView(mDisplayType, rbuf, len, "0d", "0a");
                        break;
                }

                mHandler.post(new Runnable() {
                    public void run() {
                        /*if (mTvSerial.length() > TEXT_MAX_SIZE) {   //최대치가 넘으면 반절을 지운다
                            StringBuilder sb = new StringBuilder();
                            sb.append(mTvSerial.getText());
                            sb.delete(0, TEXT_MAX_SIZE / 2);
                            mTvSerial.setText(sb);
                        }*/
                        //mText=받은 데이터 임시 저장소 mTvSerial=실제 표시되는 텍스트뷰
                        //mTvSerial.append(mText);
                        makeListData(mText);
                        mText.setLength(0);
                        //mSvText.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (mStop) {
                mRunningMainLoop = false;
                return;
            }
        }
    }
    };

    private String IntToHex2(int Value) {
        char HEX2[] = {
                Character.forDigit((Value >> 4) & 0x0F, 16),
                Character.forDigit(Value & 0x0F, 16)
        };
        String Hex2Str = new String(HEX2);
        return Hex2Str;
    }

    boolean lastDataIs0x0D = false;

    void setSerialDataToTextView(int disp, byte[] rbuf, int len, String sCr, String sLf) {
        int tmpbuf;
        for (int i = 0; i < len; ++i) {
            if (SHOW_DEBUG) {
                Log.d(TAG, "Read  Data[" + i + "] : " + rbuf[i]);
            }

            // "\r":CR(0x0D) "\n":LF(0x0A)
            if ((mReadLinefeedCode == LINEFEED_CODE_CR) && (rbuf[i] == 0x0D)) { //\r
                mText.append(sCr);
                mText.append(BR);   //줄바꿈
            } else if ((mReadLinefeedCode == LINEFEED_CODE_LF) && (rbuf[i] == 0x0A)) {  //\n
                mText.append(sLf);
                mText.append(BR);
            } else if ((mReadLinefeedCode == LINEFEED_CODE_CRLF) && (rbuf[i] == 0x0D) && (rbuf[i + 1] == 0x0A)) {   //\r\n
                mText.append(sCr);
                if (disp != DISP_CHAR) {    //문자출력이 아닐때
                    mText.append(" ");
                }
                mText.append(sLf);
                mText.append(BR);
                ++i;
            } else if ((mReadLinefeedCode == LINEFEED_CODE_CRLF) && (rbuf[i] == 0x0D)) {
                // case of rbuf[last] == 0x0D and rbuf[0] == 0x0A
                mText.append(sCr);
                lastDataIs0x0D = true;
            } else if (lastDataIs0x0D && (rbuf[0] == 0x0A)) {
                if (disp != DISP_CHAR) {
                    mText.append(" ");
                }
                mText.append(sLf);
                mText.append(BR);
                lastDataIs0x0D = false;
            } else if (lastDataIs0x0D && (i != 0)) {
                // only disable flag
                lastDataIs0x0D = false;
                --i;
            } else {
                switch (disp) {
                    case DISP_CHAR:
                        mText.append((char) rbuf[i]);
                        break;
                    case DISP_DEC:
                        tmpbuf = rbuf[i];
                        if (tmpbuf < 0) {
                            tmpbuf += 256;
                        }
                        mText.append(String.format("%1$03d", tmpbuf));
                        mText.append(" ");
                        break;
                    case DISP_HEX:
                        mText.append(IntToHex2((int) rbuf[i]));
                        mText.append(" ");
                        break;
                    default:
                        break;
                }
            }
        }
    }

    void loadDefaultSettingValues() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String res = pref.getString("display_list", Integer.toString(DISP_CHAR));
        mDisplayType = Integer.valueOf(res);

        res = pref.getString("fontsize_list", Integer.toString(12));
        mTextFontSize = Integer.valueOf(res);

        res = pref.getString("typeface_list", Integer.toString(3));
        switch(Integer.valueOf(res)){
            case 0:
                mTextTypeface = Typeface.DEFAULT;
                break;
            case 1:
                mTextTypeface = Typeface.SANS_SERIF;
                break;
            case 2:
                mTextTypeface = Typeface.SERIF;
                break;
            case 3:
                mTextTypeface = Typeface.MONOSPACE;
                break;
        }
        //mTvSerial.setTypeface(mTextTypeface);
        et_set_top_cnt.setTypeface(mTextTypeface);

        res = pref.getString("readlinefeedcode_list", Integer.toString(LINEFEED_CODE_CRLF));
        mReadLinefeedCode = Integer.valueOf(res);

        res = pref.getString("writelinefeedcode_list", Integer.toString(LINEFEED_CODE_CRLF));
        mWriteLinefeedCode = Integer.valueOf(res);

        res = pref.getString("email_edittext", "@gmail.com");
        mEmailAddress = res;

        res = pref.getString("baudrate_list", Integer.toString(19200));
        mBaudrate = Integer.valueOf(res);

        res = pref.getString("databits_list", Integer.toString(UartConfig.DATA_BITS8));
        mDataBits = Integer.valueOf(res);

        res = pref.getString("parity_list", Integer.toString(UartConfig.PARITY_NONE));
        mParity = Integer.valueOf(res);

        res = pref.getString("stopbits_list", Integer.toString(UartConfig.STOP_BITS1));
        mStopBits = Integer.valueOf(res);

        res = pref.getString("flowcontrol_list", Integer.toString(UartConfig.FLOW_CONTROL_OFF));
        mFlowControl = Integer.valueOf(res);
    }

    private void sendTextToEmail() {
        Intent intent =
                new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"
                        + mEmailAddress));

        intent.putExtra("subject", "Result of " + getString(R.string.app_name));
        //intent.putExtra("body", mTvSerial.getText().toString().trim());
        startActivity(intent);
    }

    // Load default baud rate
    int loadDefaultBaudrate() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String res = pref.getString("baudrate_list", Integer.toString(19200));
        return Integer.valueOf(res);
    }
    
    private void openUsbSerial() {
        if(mSerial == null) {
            Toast.makeText(this, "모듈과 연결이 불가능합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mSerial.isOpened()) {
            if (SHOW_DEBUG) {
                Log.d(TAG, "onNewIntent begin");
            }
            if (!mSerial.open()) {
                Toast.makeText(this, "모듈과 연결이 불가능합니다.", Toast.LENGTH_SHORT).show();
                return;
            } else {
                loadDefaultSettingValues();

                boolean dtrOn=false;
                boolean rtsOn=false;
                if(mFlowControl == UartConfig.FLOW_CONTROL_ON) {
                    dtrOn = true;
                    rtsOn = true;
                }
                mSerial.setConfig(new UartConfig(mBaudrate, mDataBits, mStopBits, mParity, dtrOn, rtsOn));

                if(SHOW_DEBUG) {
                    Log.d(TAG, "setConfig : baud : "+mBaudrate+", DataBits : "+mDataBits+", StopBits : "+mStopBits+", Parity : "+mParity+", dtr : "+dtrOn+", rts : "+rtsOn);
                }

                //mTvSerial.setTextSize(mTextFontSize);

                Toast.makeText(this, "모듈 connected", Toast.LENGTH_SHORT).show();
            }
        }
        
        if (!mRunningMainLoop) {
            mainloop();
        }

    }

    private void closeUsbSerial() {
        detachedUi();
        mStop = true;
        mSerial.close();
    }

    protected void onNewIntent(Intent intent) {
        if (SHOW_DEBUG) {
            Log.d(TAG, "onNewIntent");
        }
        
        openUsbSerial();
    };

    private void detachedUi() {
        btn_conn.setEnabled(false);
        //etWrite.setEnabled(false);
        //etWriteEnd.setEnabled(false);
        Toast.makeText(this, "disconnect", Toast.LENGTH_SHORT).show();
    }

    // BroadcastReceiver when insert/remove the device USB plug into/from a USB
    // port
    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) { //RCU가 접속 하였을때
                if (SHOW_DEBUG) {
                    Log.d(TAG, "Device attached");
                }
                if (!mSerial.isOpened()) {
                    if (SHOW_DEBUG) {
                        Log.d(TAG, "Device attached begin");
                    }
                    openUsbSerial();
                }
                if (!mRunningMainLoop) {
                    if (SHOW_DEBUG) {
                        Log.d(TAG, "Device attached mainloop");
                    }
                    mainloop();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {  //RCU가 접속 해제 되었을경우
                if (SHOW_DEBUG) {
                    Log.d(TAG, "Device detached");
                }
                mStop = true;
                detachedUi();
//                mSerial.usbDetached(intent);
                mSerial.close();
            } else if (ACTION_USB_PERMISSION.equals(action)) {          //USB 권한
                if (SHOW_DEBUG) {
                    Log.d(TAG, "Request permission");
                }
                synchronized (this) {
                    if (!mSerial.isOpened()) {
                        if (SHOW_DEBUG) {
                            Log.d(TAG, "Request permission begin");
                        }
                        openUsbSerial();
                    }
                }
                if (!mRunningMainLoop) {
                    if (SHOW_DEBUG) {
                        Log.d(TAG, "Request permission mainloop");
                    }
                    mainloop();
                }
            }
        }
    };


    /**
     * <p>Unescapes any Java literals found in the <code>String</code> to a
     * <code>Writer</code>.</p>
     *
     * <p>For example, it will turn a sequence of <code>'\'</code> and
     * <code>'n'</code> into a newline character, unless the <code>'\'</code>
     * is preceded by another <code>'\'</code>.</p>
     * 
     * <p>A <code>null</code> string input has no effect.</p>
     * 
     * //@param out  the <code>String</code> used to output unescaped characters
     * @param str  the <code>String</code> to unescape, may be null
     * @throws IllegalArgumentException if the Writer is <code>null</code>
     * @throws IOException if error occurs on underlying Writer
     */
    private String unescapeJava(String str) throws IOException {
        if (str == null) {
            return "";
        }
        int sz = str.length();
        StringBuffer unicode = new StringBuffer(4);

        StringBuilder strout = new StringBuilder();
        boolean hadSlash = false;
        boolean inUnicode = false;
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            if (inUnicode) {
                // if in unicode, then we're reading unicode
                // values in somehow
                unicode.append(ch);
                if (unicode.length() == 4) {
                    // unicode now contains the four hex digits
                    // which represents our unicode character
                    try {
                        int value = Integer.parseInt(unicode.toString(), 16);
                        strout.append((char) value);
                        unicode.setLength(0);
                        inUnicode = false;
                        hadSlash = false;
                    } catch (NumberFormatException nfe) {
                        // throw new NestableRuntimeException("Unable to parse unicode value: " + unicode, nfe);
                        throw new IOException("Unable to parse unicode value: " + unicode, nfe);
                    }
                }
                continue;
            }
            if (hadSlash) {
                // handle an escaped value
                hadSlash = false;
                switch (ch) {
                    case '\\':
                        strout.append('\\');
                        break;
                    case '\'':
                        strout.append('\'');
                        break;
                    case '\"':
                        strout.append('"');
                        break;
                    case 'r':
                        strout.append('\r');
                        break;
                    case 'f':
                        strout.append('\f');
                        break;
                    case 't':
                        strout.append('\t');
                        break;
                    case 'n':
                        strout.append('\n');
                        break;
                    case 'b':
                        strout.append('\b');
                        break;
                    case 'u':
                        {
                            // uh-oh, we're in unicode country....
                            inUnicode = true;
                            break;
                        }
                    default :
                        strout.append(ch);
                        break;
                }
                continue;
            } else if (ch == '\\') {
                hadSlash = true;
                continue;
            }
            strout.append(ch);
        }
        if (hadSlash) {
            // then we're in the weird case of a \ at the end of the
            // string, let's output it anyway.
            strout.append('\\');
        }
        return new String(strout.toString());
    }

}
