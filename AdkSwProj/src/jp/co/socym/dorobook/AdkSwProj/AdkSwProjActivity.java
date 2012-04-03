package jp.co.socym.dorobook.AdkSwProj;

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

// Runnableインターフェースを実装してスレッドを使う(1)
public class AdkSwProjActivity extends Activity implements Runnable {

    private static final String TAG = "Socym.AdkSwProj";

    // 本アプリを認識するためのインテントアクション名
    private static final String ACTION_USB_PERMISSION = "jp.co.dorobook.AdkLedSwProj.action.USB_PERMISSION";

    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;

    ParcelFileDescriptor mFileDescriptor;

    FileInputStream mInputStream;// 入力用ストリーム

    //レイアウトされたテキストビュー用インスタンス(2)
    private TextView mTextView1;

    //USB接続状態を監視するブロードキャストレシーバ mUsbReceiver
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
                    // 接続中のUSBアクセサリなら接続を閉じる
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

        // パーミッション・インテントの作成（自分自身のアプリから発行）
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        // ブロードキャストレシーバで受信するインテントを登録
        IntentFilter filter = new IntentFilter();
        //USBアクセサリが接続／切断されたときのインテント・フィルター
        filter.addAction(ACTION_USB_PERMISSION);
        //USBアクセサリが切断された（取り外された）ときのインテント・フィルター
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        // layout/main.xmlの画面レイアウトをセット
        setContentView(R.layout.main);
        // レイアウトされたテキストビューとインスタンスの結びつけ(3)
        mTextView1 = (TextView) findViewById(R.id.textView1);

    }

    //アプリ起動時の処理 OnResume()メソッド（Activityライフサイクル）
    @Override
    public void onResume() {
        super.onResume();

        //既に通信しているか
        if (mInputStream != null) {
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

            // 入出力用のストリームを確保（今回は入力のみ）(4)
            mInputStream = new FileInputStream(fd);

            // スレッドの生成
            Thread thread = new Thread(null, this, "DemoKit");
            // run()メソッドに書かれた処理がスレッドとして実行される(5)
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

    //スレッドがスタートしたら実行される処理(6)
    @Override
    public void run() {
        // ここでArduino（USBホスト）からのデータを受信する
        int ret = 0;
        byte[] buffer = new byte[16384];
        int i;

        // 入力：Androidアプリ（USBアクセサリ）<- Arduino(USBホスト）
        while (ret >= 0) { // 読み込むデータが残っている間繰り返す
            try {
                // 入力ストリームからbuffer[]配列にデータを読み込む(7)
                ret = mInputStream.read(buffer);
            } catch (IOException e) {
                break;
            }

            // 入力したバッファ分読み取る
            i = 0; // buffer[]の位置
            while (i < ret) {
                int len = ret - i;

                // バッファから読み取ったデータが1バイト以上あれば処理
                if (len >= 1) {
                    // ハンドラに渡すメッセージの作成
                    Message m = Message.obtain(mHandler);

                    // メッセージの内容を作る(8)
                    m.obj = new String("SW="+buffer[i]);
                    //ハンドラにメッセージを送信
                    mHandler.sendMessage(m);
                }
                i += 1;
            }
        }
    }

    // ハンドラの宣言と処理(9)
    // スレッドのrun()メソッドから呼ばれる。
    // ハンドラを用いて画面上の表示を変更
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // メッセージは文字列で送られているのでStringクラスのインスタンスに格納する
            String s = (String)msg.obj;
            if ( s.equals("SW=1") ) { // メッセージが"SW=1"ならば
                mTextView1.setText("ON"); // "ON"を画面に表示する
            } else { // それ以外のメッセージが"SW=0"ならば
                mTextView1.setText("OFF"); // "OFF"を画面に表示
            }
        }
    };

}
