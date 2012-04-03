#include <Max3421e.h> //ADKを利用するための3つのライブラリを読み込む
#include <Usb.h>
#include <AndroidAccessory.h>

#define  BUTTON  4 //タクトスイッチ（ボタン）用のピンを4番に指定する(1)

//Androidアプリ側のaccesory_filter.xml内の属性と一致させる
AndroidAccessory acc("Dorobook,Socym",   //第1引数:組織名（manufacturer属性と一致）
      "AdkSw",                           //第2引数:モデル名（model属性と一致）
      "AdkSw - Arduino USB Host",        //第3引数:ダイアログ表示メッセージ
      "1.0",                             //第4引数:バージョン（version属性と一致）
      "http://accessories.android.com/", //第5引数:ジャンプ先URL
      "0000000012345678");               //第6引数:シリアル番号

byte b0;//タクトスイッチのひとつ前の状態を保存する

void setup()  //最初に一度だけ実行される部分
{
    Serial.begin(115200);
    Serial.println("\r\nStart");
    pinMode(BUTTON, INPUT);//タクトスイッチ用ピンを出力ポートにする(2)
    b0 = digitalRead(BUTTON);//最初のタクトスイッチの状態を保存する(3)
    //USBホスト機能を有効にする
    acc.powerOn();
    Serial.println("--setup done--");//シリアルモニターにsetup()終了を出力
}

void loop()  //繰り返し実行される部分
{
    byte msg[1];//Androidへ送るデータ

    
    if (acc.isConnected()) {  //Androidを起動・接続する命令を送る
        //communicate with Android application
        byte b = digitalRead(BUTTON);//タクトスイッチからデジタル入力を読み込む(4)
        if(b != b0){ //前の状態と異なるときだけ送信(5)
          msg[0] = b;  //現在のオン／オフの状態をコマンド・データとする
          acc.write(msg, 1);//USBアクセサリ(Android)に書き込む(6)
          b0 = b; //次の処理のために現在のオン／オフの状態を格納
          //タクトスイッチの現在の状態をシリアルモニターに出力(7)
          Serial.println( "msg[0]=" + String(msg[0]) ); 
        }
    } else {
        //set the accessory to its default state
    }
    delay(10); //10ミリ秒処理を停止(10ミリ秒おきにloop()を繰り返す)
}

