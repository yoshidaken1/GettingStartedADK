#include <Max3421e.h> //ADKを利用するための3つのライブラリを読み込む
#include <Usb.h>
#include <AndroidAccessory.h>

#define  LED  2 //LED用のピンを2番に指定する(1)

//Androidアプリ側のaccesory_filter.xml内の属性と一致させる(2)
AndroidAccessory acc("Dorobook,Socym",   //第1引数:組織名（manufacturer属性と一致）
      "AdkLedOnOff",                     //第2引数:モデル名（model属性と一致）
      "AdkLedOnOff - Arduino USB Host",  //第3引数:ダイアログ表示メッセージ
      "1.0",                             //第4引数:バージョン（version属性と一致）
      "http://accessories.android.com/", //第5引数:ジャンプ先URL
      "0000000012345678");               //第6引数:シリアル番号

void setup()  //最初に一度だけ実行される部分
{
  Serial.begin(115200);
  Serial.print("\r\nStart");
  pinMode(LED, OUTPUT); //LED用ピンを出力ポートにする(3)
  //USBホスト機能を有効にする
  acc.powerOn();
}

void loop()  //繰り返し実行される部分
{
  byte msg[1];//Androidから受け取るデータ

  if (acc.isConnected()) {  //Androidを起動・接続する命令を送る
    //communicate with Android application
    int len = acc.read(msg, sizeof(msg), 1); //ADK接続から読み込み(4)

    if (len > 0) { //読み込んだデータがあれば処理する(5)
      if (msg[0] == 0x1) { //独自プロトコルのコマンド・コマンドが0x1ならば処理する(6)
        digitalWrite(LED, HIGH);//LEDを点灯する(6)
      } else {
        digitalWrite(LED, LOW);//LEDを消灯する(6)
      }
    }
  } else {
    //set the accessory to its default state
    digitalWrite(LED, LOW); //USB接続されていないときは消灯しておく
  }
  delay(10); //10ミリ秒処理を停止(10ミリ秒おきにloop()を繰り返す) 
}

