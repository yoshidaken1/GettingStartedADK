#include <Max3421e.h>  //ADKを利用するための3つのライブラリを読み込む
#include <Usb.h>
#include <AndroidAccessory.h>

//定数の指定：デジタル・アナログ入出力のピンを定数に指定(1)
#define  LED      2 //デジタル出力LED用のピンをD2番に指定する
#define  LED_PWM  3 //PWM出力を行うLED用のピンをD3番に指定する
#define  BUTTON   4 //デジタル入力を行うタクトスイッチ用のピンをD4番に指定する
#define  SENSOR  A0 //アナログ入力を行う（CdSセル）用のピンをA0番に指定する

//外部インテント指定：Androidアプリ側のaccesory_filter.xml内の属性と一致させる(2)
AndroidAccessory acc("Dorobook,Socym",   //第1引数:組織名（manufacturer属性と一致）
    "AdkDaio",                           //第2引数:モデル名（model属性と一致）
    "AdkDaio - Arduino USB Host",        //第3引数:ダイアログ表示メッセージ
    "1.0",                               //第4引数:バージョン（version属性と一致）
    "http://accessories.android.com/",   //第5引数:ジャンプ先URL
    "0000000012345678");                 //第6引数:シリアル番号

byte b0;//タクトスイッチのひとつ前の状態を保存する

void setup()  //最初に一度だけ実行される部分
{
  Serial.begin(115200);
  Serial.println("\r\nStart");
  //初期化：デジタル・アナログ入出力のピンのポート指定(3)
  pinMode(LED, OUTPUT);    //LED用ピンを出力ポートにする
  pinMode(LED_PWM, OUTPUT);//LED(PWM)用ピンを出力ポートにする
  pinMode(BUTTON, INPUT);  //タクトスイッチ用ピンを出力ポートにする
  //アナログ入力ピンは初期化は不要
  b0 = digitalRead(BUTTON);//最初のタクトスイッチの状態を保存する
  //USBホスト機能を有効にする
  acc.powerOn();
  Serial.println("--setup done--");//シリアルモニターにsetup()終了を出力
}

void loop() //繰り返し実行される部分
{
  static byte count = 0;//アナログ入力のループカウンタ用
  byte msg[3];//Androidとやりとりするデータ

  //Androidとの接続処理(4)
  if (acc.isConnected()) {  //Androidを起動・接続する命令を送る
    //communicate with Android application
    
    //デジタル・アナログ出力処理(AndroidからreadしてArduinoのピンにwriteする)(5)
    int len = acc.read(msg, sizeof(msg), 3); //ADK接続から読み込み 
    if (len > 0) {
      //読み込んだデータがあれば処理する
      switch(msg[0]){
      //LEDオンオフ（デジタル出力）コマンド(6)
      case 0x0: 
        digitalWrite(LED, msg[1]? HIGH: LOW);//データに応じてLEDを点灯/消灯する
        Serial.println( "DO:msg[]=" + String(msg[0],HEX) + ", " + String(msg[1],HEX) );
        break;
      //LED PWM出力（アナログ出力）コマンド(7)
      case 0x1:
        if (msg[1] >= 0x0 && msg[1] <= 0xff ) {
          //データが0～255ならば処理する
          analogWrite(LED_PWM, msg[1]);//LEDをPWM値（0:消～255:明るさ最大）で点灯
          Serial.println( "AO:msg[]=" + String(msg[0],HEX) + ", " + String(msg[1],HEX) );
        }          
      }
    }

    //デジタル入力処理（ArduinoのピンからreadしてAndroidにwriteする）(8)
    byte b = digitalRead(BUTTON);
    //タクトスイッチ・オン／オフ入力（デジタル入力）
    if(b != b0){
      //前の状態と異なるときだけ処理
      msg[0] = 0x2;// タクトスイッチ・オン／オフ入力コマンド
      msg[1] = b;  // オン／オフのデータ
      acc.write(msg, 3);//USBアクセサリ(Android)に書き込む
      b0 = b; //次の処理のために現在のオン／オフの状態を格納
      //タクトスイッチの現在の状態をシリアルモニターに出力
      Serial.println( "DI:msg[]=" + String(msg[0],HEX) + ", " + String(msg[1],HEX) );
    }

    //アナログ入力処理（ArduinoのピンからreadしてAndroidにwriteする）(9)
    switch(count++ % 0x10){ // 16(0x10)回に1回行うための処理
    case 0x3: //Cdsセル（光センサー）入力コマンド
      int val = analogRead(SENSOR);//光センサからアナログ入力を読み込む
      msg[0] = 0x3;
      msg[1] = val >> 8;    //上位8ビットを8ビット右シフトして格納
      msg[2] = val & 0xff;  //下位8ビットのみビットマスクで取り出して格納
      acc.write(msg, 3);    //USBアクセサリ(Android)に書き込む
      //光センサの現在の状態をシリアルモニターに出力
      Serial.println( "AI:msg[]=" + String(msg[0],HEX) + ", " + String(msg[1],HEX)+ ", " + String(msg[2],HEX) );
      break;
    }
  } else {
    //set the accessory to its default state
    //Androidと接続されていないときの処理
    digitalWrite(LED,LOW);   //USB接続されていないときは消灯しておく
    analogWrite(LED_PWM, 0); //USB接続されていないときは消灯しておく
  }
  delay(10); //10ミリ秒処理を停止(10ミリ秒おきにloop()を繰り返す)
}


