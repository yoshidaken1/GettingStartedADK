package jp.co.socym.dorobook.AdkLedPwmProj;

import java.io.FileDescriptor;
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
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class AdkLedPwmProjActivity extends Activity {

    private static final String TAG = "Socym.AdkLedPwmProj";

    // 本アプリを認識するためのインテントアクション名
    private static final String ACTION_USB_PERMISSION
                = "jp.co.dorobook.AdkLedPwmProj.action.USB_PERMISSION";

    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;

    ParcelFileDescriptor mFileDescriptor;

    FileOutputStream mOutputStream;// 出力用ストリーム

    // レイアウトされたシークバー用インスタンス(1)
    private SeekBar mSeekBar1;

    // USB接続状態を監視するブロードキャストレシーバ mUsbReceiver
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // ACTION_USB_PERMISSIONの場合
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    // Intent からアクセサリを取得
                    UsbAccessory accessory = UsbManager.getAccessory(intent);
                    // 接続許可ダイアログで OK=true, Cancel=false のどちらを押したか
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // 接続を開く
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory " + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            // USBホストシールドがUSBコネクタから外された場合
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                // Intent からアクセサリを取得
                UsbAccessory accessory = UsbManager.getAccessory(intent);
                // 接続中のUSBアクセサリか？
                if (accessory != null && accessory.equals(mAccessory)) {
                 // 接続中のUSBアクセサリなら接続を閉じる
                    closeAccessory();
                }
            }
        }

    };

    // アプリ起動時の処理 OnCreate()メソッド（Activityライフサイクル）
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // UsbManager のインスタンスを取得
        mUsbManager = UsbManager.getInstance(this);

        // パーミッション・インテントの作成（自分自身のアプリから発行）
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        // ブロードキャストレシーバで受信するインテントを登録
        IntentFilter filter = new IntentFilter();
        // USBアクセサリが接続／切断されたときのインテント・フィルター
        filter.addAction(ACTION_USB_PERMISSION);
        // USBアクセサリが切断された（取り外された）ときのインテント・フィルター
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        // layout/main.xmlの画面レイアウトをセット
        setContentView(R.layout.main);
        // レイアウトされたシークバーとインスタンス mSeekBar1 の結びつけ(2)
        mSeekBar1 = (SeekBar) findViewById(R.id.seekBar1);
        mSeekBar1.setMax(255);// シークバーの最大値を255にする

        // シークバーを動かしたときの処理(3)
        mSeekBar1.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekView, int progress, boolean fromUser) {
                // SeekBarをスライドした量(0～255)をコマンドに(4)
                byte command = (byte) progress;
                sendCommand(command);
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
        if (mOutputStream != null) {
            return;
        }

        // 接続されているUSBアクセサリの確認
        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            // USBアクセサリ にアクセスする権限があるかチェック
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

    // 他のActivityが開始される時の処理 OnPause()メソッド（Activityライフサイクル）
    @Override
    public void onPause() {
        super.onPause();
        closeAccessory();
    }

    // アプリ終了時の処理 OnDestroy()メソッド（Activityライフサイクル）
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

            // 入出力用のストリームを確保（今回は出力のみ）
            mOutputStream = new FileOutputStream(fd);

            Log.d(TAG, "accessory opened");

        } else {
            Log.d(TAG, "accessory open fail");
        }
    }

    // USBアクセサリ終了処理
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

    // Androidアプリ（USBアクセサリ -> Arduino（USBホスト）
    public void sendCommand(byte value) {
        byte[] buffer = new byte[1];

        // 1バイトのみのプロトコルデータ
        buffer[0] = value;
        if (mOutputStream != null) {
            try {
              //出力ストリームにbuffer[]配列データを書き込む(5)
                mOutputStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
        }
    }
}
