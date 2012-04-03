package jp.co.socym.dorobook.AdkDaioProj;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar.OnSeekBarChangeListener;

//Runnableインターフェースを実装してスレッドを使う
public class AdkDaioProjActivity extends Activity implements Runnable {

    private static final String TAG = "Socym.AdkDaioProj";

    // 本アプリを認識するためのインテントアクション名
    private static final String ACTION_USB_PERMISSION
                = "jp.co.dorobook.AdkDaioProj.action.USB_PERMISSION";

    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;

    ParcelFileDescriptor mFileDescriptor;

    // 入出力用ストリームを用意する(1)
    FileInputStream mInputStream;   // 入力用ストリーム
    FileOutputStream mOutputStream; // 出力用ストリーム

    //レイアウトされたウィジェットに対するインスタンス(2)
    private ToggleButton mToggleButton1;//LED オン／オフ用トグルボタン
    private SeekBar mSeekBar1;          //LED PWM出力用シークバー
    private TextView mTextView1;        //タクトスイッチ用テキストビュー
    private TextView mTextViewLight1;   //光センサ用テキストビュー
    private TextView mTextViewLight2;

    //USBの接続状態を監視するブロードキャストレシーバ mUsbReceiver
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
          //ACTION_USB_PERMISSIONの場合
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    // Intent からアクセサリを取得
                    UsbAccessory accessory = UsbManager.getAccessory(intent);
                    //接続許可ダイアログで OK=true, Cancel=false のどちらを押したか
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // 接続を開く
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory " + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            //USBホストシールドがUSBコネクタから外された場合
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                // Intent からアクセサリを取得
                UsbAccessory accessory = UsbManager.getAccessory(intent);
                // 接続中のUSBアクセサリか？
                if (accessory != null && accessory.equals(mAccessory)) {
                    // 接続中のアクセサリが外されたのなら接続を閉じる
                    closeAccessory();
                }
            }
        }

    };

    //アプリ起動時の処理 OnCreate()メソッド（Activityライフサイクル）
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // UsbManager のインスタンスを取得
        mUsbManager = UsbManager.getInstance(this);

        // パーミッションインテントの作成（自分自身のアプリから発行）
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        // パーミッションインテント とアクセサリが取り外されたときのインテントを登録
        IntentFilter filter = new IntentFilter();
        //USBアクセサリが接続／切断されたときのインテント・フィルター
        filter.addAction(ACTION_USB_PERMISSION);
        //USBアクセサリが切断された（取り外された）ときのインテント・フィルター
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        setContentView(R.layout.main);//main.xmlのレイアウトをセット
        //レイアウトされたウィジェットとインスタンスの結びつけ(3)
        mToggleButton1 = (ToggleButton) findViewById(R.id.toggleButton1);// トグルスイッチとの結びつけ
        mSeekBar1 = (SeekBar) findViewById(R.id.seekBar1);// シークバーとの結びつけ
        mSeekBar1.setMax(255);// シークバーの最大値を255にする（アナログ出力は0～255のため）
        mTextView1 = (TextView) findViewById(R.id.textView1);//タクトスイッチ用テキストビューとの結びつけ
        mTextViewLight1 = (TextView) findViewById(R.id.textViewLight1); // 光センサ用テキストビューとの結びつけ
        mTextViewLight2 = (TextView) findViewById(R.id.textViewLight2);

        //トグルボタンのON/OFF表示が切り替わったときに実行されるリスナー
        mToggleButton1.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // デジタル出力処理(4)
                byte command = (byte)0x0;       // LEDオン・オフ出力指定コマンド
                // トグルボタンが押されたら0x1そうでなければ0x0
                byte value = (byte)(isChecked ? 0x1 : 0x0);
                sendCommand(command, value);    // 送信処理へ
            }
        });

        //シークバーのスライドが変化したときに実行されるリスナー
        mSeekBar1.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekView, int progress, boolean arg2) {
                // アナログ(PWM)出力処理(5)
                byte command = (byte)0x1;       //LED PWM出力指定コマンド
                byte value = (byte)progress;    // SeekBarをスライドした量をセット
                sendCommand(command, value);    // 送信処理へ
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

    }

    //アプリ起動時の処理 OnResume()メソッド（Activityライフサイクル）
    @Override
    public void onResume() {
        super.onResume();

        //既に通信しているか
        if (mInputStream != null && mOutputStream != null) {
            return;
        }
        // 接続されているUSBアクセサリの確認
        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            // Accessory にアクセスする権限があるかチェック
            if (mUsbManager.hasPermission(accessory)) {
                // 接続許可されているならば、アプリを起動
                openAccessory(accessory);
            } else {
                // 接続許可されていないのならば、パーミッションインテント発行
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        // パーミッションを依頼
                        mUsbManager.requestPermission(accessory, mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "mAccessory is null");
        }
    }

    //他のActivityが開始される時の処理 OnPause()メソッド（Activityライフサイクル）
    @Override
    public void onPause() {
        super.onPause();
        closeAccessory();
    }

    //アプリ終了時の処理 OnDestroy()メソッド（Activityライフサイクル）
    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }

    //USBアクセサリ開始処理
    private void openAccessory(UsbAccessory accessory) {
        // アクセサリにアクセスするためのファイルディスクリプタを取得
        mFileDescriptor = mUsbManager.openAccessory(accessory);

        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();

            // 入出力用のストリームを確保
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);

            // スレッドの生成
            Thread thread = new Thread(null, this, "DemoKit");
            // run()メソッドに書かれた処理がスレッドとして実行される
            thread.start();
            Log.d(TAG, "accessory opened");
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }

    //USBアクセサリ終了処理
    private void closeAccessory() {
        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    // Androidアプリ（USBアクセサリ -> Arduino（USBホスト）コマンド送信
    public void sendCommand(byte command, byte value) {
        byte[] buffer = new byte[3];
        switch (command) {
            case 0x0:// デジタル出力をバイト配列にセット(6)
                buffer[0] = 0x0;// LEDオン・オフ出力指定コマンド
                buffer[1] = value;// 0x1 LEDオン 0x0 LEDオフ
                break;
            case 0x1:// アナログ(PWM)出力をバイト配列にセット(7)
                buffer[0] = 0x1;//LED PWM出力指定コマンド
                buffer[1] = value;//明るさ0～255
                break;
        }
        if (mOutputStream != null) {
            try {
              //バッファのデータをArduino（USBホスト）へ書き込み
                mOutputStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
        }
    }


    //スレッドがスタートしたら実行される処理
    @Override
    public void run() {
        // ここでArduino（USBホスト）からのデータを受信する
        int ret = 0;
        byte[] buffer = new byte[16384];
        int i;

        // 入力：Androidアプリ（USBアクセサリ）<- Arduino(USBホスト）
        while (ret >= 0) { // 読み込むデータが残っている間繰り返す
            try {
                // 入力ストリームからbuffer[]配列にデータを読み込む
                ret = mInputStream.read(buffer);
            } catch (IOException e) {
                break;
            }

            // 入力したバッファ分読み取る
            i = 0; // buffer[]の位置
            //バッファにたまっている受信データがある間繰り返す
            while (i < ret) {
                int len = ret - i;
                switch (buffer[i]) {
                    case 0x2: //タクトスイッチ・オン／オフ入力指定コマンド(8)
                        if (len >= 3) {
                            //メッセージを作成しwhat値にタクトスイッチ・オン／オフ入力指定コマンドを指定
                            Message m = Message.obtain(mHandler, 0x2);
                            m.obj = new String("SW=" + buffer[i + 1]);
                            mHandler.sendMessage(m);//ハンドラーに送信する
                        }
                        i += 3;
                        break;
                    case 0x3: //光センサ・アナログ入力指定コマンド(9)
                        if (len >= 3) {
                            //メッセージを作成しwhat値に光センサ・アナログ入力指定コマンドを入れる
                            Message m = Message.obtain(mHandler, 0x3);
                            m.obj = new CdsMsg(buffer[i + 1], buffer[i + 2]);
                            mHandler.sendMessage(m);//ハンドラーに送信する
                        }
                        i += 3;
                        break;
                }
            }
        }
    }

    // UI スレッドrun()メソッドからハンドラを用いて画面上の表示を変更
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //what値のコマンドによってUI処理を分岐させる
            switch (msg.what) {
                case 0x2: //タクトスイッチ・オン／オフ入力の表示(10)
                    String s = (String) msg.obj;
                    if (s.equals("SW=1")) {
                        mTextView1.setText("ON");
                    } else {
                        mTextView1.setText("OFF");
                    }
                    break;
                case 0x3:  //光センサ・アナログ入力の表示(11)
                    CdsMsg o = (CdsMsg) msg.obj;
                    mTextViewLight1.setText((o.getRateVal() * 100) + "%");
                    mTextViewLight2.setText(o.getAnalogVal() + "/1023");
                    break;
            }
        }
    };

    // 光センサの状態（アナログ入力による抵抗値=光量）をカプセル化したクラス
    // メッセージとして利用
    private class CdsMsg {
        private int analogVal;//アナログ量
        private double rateVal;//アナログ量の比率

        public CdsMsg(byte upper, byte lower) {
            //8ビットのbyte型変数を符号ビットを含めてビットマスクで取りだす
            int i0 = upper & 0xff;
            int i1 = lower & 0xff;
            //上位8ビットを8ビット左シフトし下位8ビットと合わせる
            analogVal = (i0 << 8) + i1;
            //アナログ量の比率の計算
            rateVal = (double) analogVal / 1023.0 ;
        }

        //フィールド変数analogValのgetter
        public int getAnalogVal() {
            return analogVal;
        }

        //フィールド変数rateValのgetter
        public double getRateVal() {
            return rateVal;
        }

    }

}