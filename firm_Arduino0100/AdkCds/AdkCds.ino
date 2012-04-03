#include <Max3421e.h> //ADKを利用するための3つのライブラリを読み込む
#include <Usb.h>
#include <AndroidAccessory.h>

#define  SENSOR  A0 //アナログ入力を行う光センサ（CdSセル）用のピンをA0番に指定する(1)
// アナログ入力ピン「A0」は「0」でも可

//Androidアプリ側のaccesory_filter.xml内の属性と一致させる
AndroidAccessory acc("Dorobook,Socym",   //第1引数:組織名（manufacturer属性と一致）
      "AdkCds",                          //第2引数:モデル名（model属性と一致）
      "AdkCds - Arduino USB Host",       //第3引数:ダイアログ表示メッセージ
      "1.0",                             //第4引数:バージョン（version属性と一致）
      "http://accessories.android.com/", //第5引数:ジャンプ先URL
      "0000000012345678");               //第6引数:シリアル番号
    
void setup()  //最初に一度だけ実行される部分
{
  Serial.begin(115200);
  Serial.println("\r\nStart");
  //アナログ入力ピンは初期化は不要(2)
  //USBホスト機能を有効にする
  acc.powerOn();
  Serial.println("--setup done--");//シリアルモニターにsetup()終了を出力
}

void loop()  //繰り返し実行される部分
{
  static byte count = 0;//ループカウンタ用
  byte msg[2];//Androidへ送るデータ

  if (acc.isConnected()) {  //Androidを起動・接続する命令を送る
    //communicate with Android application
    
    //アナログ入力の処理を16(0x10)回に1回行うための処理(3)
    if(count++ % 0x10 == 0x0){
      int val = analogRead(SENSOR);//光センサからアナログ入力を読み込む(4)
      msg[0] = val >> 8;    //上位8ビットを8ビット右シフトして格納(5)
      msg[1] = val & 0xff;  //下位8ビットのみビットマスクで取り出して格納(6)
      acc.write(msg, 2);//USBアクセサリ(Android)に書き込む
      //光センサの現在の状態をシリアルモニターに出力
      Serial.println( "val=" + String(val) );
    }
  } else {
    //set the accessory to its default state
  }
  delay(10); //10ミリ秒処理を停止(10ミリ秒おきにloop()を繰り返す)
}

