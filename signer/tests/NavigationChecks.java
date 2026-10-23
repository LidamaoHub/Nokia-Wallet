public final class NavigationChecks {
 private static int checks;
 private static void check(boolean value,String message){if(!value)throw new RuntimeException(message);checks++;}
 public static void main(String[] args){
  NWalletNavigation n=new NWalletNavigation(0);
  n.tab(1);n.move(1);n.move(1);check(n.focus()==2,"settings order");
  n.openLanguage();check(n.focus()==0&&n.language()==0,"starts at saved language");
  n.move(1);check(n.focus()==1&&n.language()==0,"browsing must not apply");
  n.tab(1);check(n.tab()==1,"language is not a main tab");
  n.back();check(n.language()==0,"back must not save a draft");
  n.openLanguage();n.move(1);n.languageSaved();check(n.language()==1,"explicit selection applies");
  n.back();check(n.language()==1,"back cannot roll back a save");
  check(new NWalletNavigation(n.language()).language()==1,"restored host preference");
  n.tab(1);n.move(99);check(n.focus()==3,"last item reachable");n.move(-99);check(n.focus()==0,"no boundary wrapping");
  n.read();n.tab(1);check(n.tab()==2,"read view cannot switch tabs");
  System.out.println("PASS "+checks+" navigation and language transition checks");
 }
}
