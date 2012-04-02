package jp.co.socym.dorobook.AdkCdsProj;

import java.io.FileDescriptor;
import java.io.FileInputStream;
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
import android.widget.TextView;

public class MainActivity extends Activity implements Runnable {

    private static final String TAG = "Socym.AdkCdsProj";

    // 本アプリを認識するためのインテントアクション名
    private static final String ACTION_USB_PERMISSION = "jp.co.dorobook.AdkCdsProj.action.USB_PERMISSION";

    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;

    ParcelFileDescriptor mFileDescriptor;

    FileInputStream mInputStream;// 入力用ストリーム
    // FileOutputStream mOutputStream;// 出力用ストリーム（今回は使わない）

    private TextView mTextViewLight1;
    private TextView mTextViewLight2;
    //
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    // Intent からアクセサリを取得
                    UsbAccessory accessory = UsbManager.getAccessory(intent);

                    // パーミッションがあるかチェック
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // 接続を開く
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory " + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                // Intent からアクセサリを取得
                UsbAccessory accessory = UsbManager.getAccessory(intent);
                if (accessory != null && accessory.equals(mAccessory)) {
                    // 接続を閉じる
                    closeAccessory();
                }
            }
        }

    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // UsbManager のインスタンスを取得
        mUsbManager = UsbManager.getInstance(this);

        // 独自パーミッション用 Broadcast Intent
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION),
                0);

        // 独自パーミッション Intent とアクセサリが取り外されたときの Intent を登録
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        setContentView(R.layout.main);
        // レイアウトされたトグルボタンとインスタンスの結びつけ
        mTextViewLight1 = (TextView) findViewById(R.id.textViewLight1);
        mTextViewLight2 = (TextView) findViewById(R.id.textViewLight2);
    }

    @Override
    public void onResume() {
        super.onResume();

        // USB Accessory の一覧を取得
        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            // Accessory にアクセスする権限があるかチェック
            if (mUsbManager.hasPermission(accessory)) {
                // 接続を開く
                openAccessory(accessory);
            } else {
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

    @Override
    public void onPause() {
        super.onPause();
        closeAccessory();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }

    private void openAccessory(UsbAccessory accessory) {
        // アクセサリにアクセスするためのファイルディスクリプタを取得
        mFileDescriptor = mUsbManager.openAccessory(accessory);

        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();

            // 入出力用のストリームを確保
            mInputStream = new FileInputStream(fd);
            // mOutputStream = new FileOutputStream(fd);

            // この中でアクセサリとやりとりする
            Thread thread = new Thread(null, this, "DemoKit");
            thread.start();
            Log.d(TAG, "accessory opened");

            // enableControls(true);
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }

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

    @Override
    public void run() {
        // ここでArduino（USBホスト）からのデータを受信する
        // Arduino(USBホスト） -> Androidアプリ（UDBアクセサリ）
        int ret = 0;
        byte[] buffer = new byte[16384];
        int i;

        // アクセサリ -> アプリ
        while (ret >= 0) {
            try {
                ret = mInputStream.read(buffer);
            } catch (IOException e) {
                break;
            }

            i = 0;
            while (i < ret) {
                int len = ret - i;

                if (len >= 2) {
                    Message m = Message.obtain(mHandler/* , MESSAGE_LED */);
                    m.obj = new CdsMsg(buffer[i],buffer[i+1]);
                    //m.obj = new String("温度=" + buffer[i + 0] + ":" + buffer[i + 1]);
                    mHandler.sendMessage(m);
                }
                i += 1;
            }
        }
    }

    // UI スレッドrun()メソッドからハンドラを用いて画面上の表示を変更
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            CdsMsg o = (CdsMsg) msg.obj;
            mTextViewLight1.setText((o.getRateVal() * 100) +"%");
            mTextViewLight2.setText(o.getAnalogVal()+"/1024");

        }
    };
    //タクトスイッチの状態を保持するクラス
    //メッセージとして利用
    private class CdsMsg {
        private int analogVal;
        private double rateVal;

        public CdsMsg(byte upper, byte lower) {
            int i = upper & 0xff;
            int i1 = lower & 0xff;
            analogVal = i * 256 + i1;
            rateVal = (double)analogVal / 1024.0 ;
        }

        public int getAnalogVal() {
            return analogVal;
        }

        public double getRateVal() {
            return rateVal;
        }

    }

}
