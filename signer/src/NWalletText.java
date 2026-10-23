/** Fixed bilingual interface copy. No business status is inferred here. */
public final class NWalletText {
 public static final int HOME=0,SETTINGS=1,ABOUT=2,CONNECT=3,PIN=4,BLUETOOTH=5,LANGUAGE=6,DESIGN=7,GUIDE=8,ARCHITECTURE=9,VERSION=10,BACK=11,SELECT=12,OPEN=13,LOCK=14,EXIT=15,SAMPLE=16,PEER=17,REQUEST=18,NONE=19,ROOT=20;
 private static final String[][] TEXT={
  {"首页","设置","介绍","连接客户端","PIN 与锁定","蓝牙与连接","Language / 语言","设计初心","使用说明","技术框架","版本与能力","返回","选定","进入","锁定","退出","仅公开样本；禁止真实资产","对端","当前请求","无","创建样本根钱包"},
  {"Home","Settings","About","Connect client","PIN & lock","Bluetooth","Language / 语言","Our purpose","Usage guide","Architecture","Version & scope","Back","Select","Open","Lock","Exit","Public fixtures only. No real assets.","Peer","Request","None","Create sample root"}
 };
 public static String get(int language,int key){return TEXT[language==1?1:0][key];}
}
