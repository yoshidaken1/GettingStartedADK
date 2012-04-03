#include <Max3421e.h> //ADKを利用するための3つのライブラリを読み込む
#include <Usb.h>
#include <AndroidAccessory.h>

#define  LED_PWM  3  //PWM出力を行うLED用のピンを3番に指定する
//USBホストシールド使用時はPWMに使えるピンはD3,D5,D6まで(1)

//Androidアプリ側のaccesory_filter.xml内の属性と一致させる
AndroidAccessory acc("Dorobook,Socym",   //第1引数:組織名（manufacturer属性と一致）
      "AdkLedPwm",                       //第2引数:モデル名（model属性と一致）
      "AdkLedPwm - Arduino USB Host",    //第3引数:ダイアログ表示メッセージ
      "1.0",                             //第4引数:バージョン（version属性と一致）
      "http://accessories.android.com/", //第5引数:ジャンプ先URL
      "0000000012345678");               //第6引数:シリアル番号

void setup() //最初に一度だけ実行される部分
{
  Serial.begin(115200);
  Serial.print("\r\nStart");
  pinMode(LED_PWM, OUTPUT);  //LED(PWM)用ピンを出力ポートにする
  //USBホスト機能を有効にする
  acc.powerOn();
}

void loop() //繰り返し実行される部分
{
  byte msg[1];//Androidから受け取るデータ

  if (acc.isConnected()) {  //Androidを起動・接続する命令を送る
    //communicate with Android application
    int len = acc.read(msg, sizeof(msg), 1); //ADK接続から読み込み 
    if (len > 0) { //読み込んだデータがあれば処理する
      //独自プロトコルのコマンド・データが0～255ならば処理する(2)
      if (msg[0] >= 0x0 && msg[0] <= 0xff ) {
        //LEDをPWM値（0:消～255:明るさ最大）で点灯(3)
        analogWrite(LED_PWM, msg[0]);
      }
    }
  } else {
    //set the accessory to its default state
    analogWrite(LED_PWM, 0); //USB接続されていないときは消灯しておく
  }
  delay(10); //10ミリ秒処理を停止(10ミリ秒おきにloop()を繰り返す) 
}

